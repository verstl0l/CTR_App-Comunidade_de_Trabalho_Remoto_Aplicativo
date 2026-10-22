package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

class ListaConversasActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""

    private var listenerUsuario: ListenerRegistration? = null
    private val listenersChats = mutableListOf<ListenerRegistration>()
    private val viewsPorChat = mutableMapOf<String, View>()
    private var sincronizando = false
    private var jobDebounce: Job? = null
    private var renderizando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lista_conversas)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""

        val btnNovaConversa = findViewById<Button>(R.id.btnNovaConversa)
        btnNovaConversa.setOnClickListener { mostrarDialogNovaConversa() }

        configurarBottomNavigation(R.id.nav_chat)

        mostrarCacheLocal()
        sincronizarResumos()
        iniciarListenerConversas()
        iniciarListenersDosChats()
        migrarChatsResumo()
    }

    override fun onResume() {
        super.onResume()
        sincronizarResumos()
    }

    override fun onDestroy() {
        super.onDestroy()
        listenerUsuario?.remove()
        listenerUsuario = null
        listenersChats.forEach { it.remove() }
        listenersChats.clear()
        jobDebounce?.cancel()
    }

    private fun mostrarCacheLocal() {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val cacheJson = prefs.getString(KEY_CACHE_CONVERSAS, "") ?: ""
            if (cacheJson.isEmpty()) return

            val conversas = parsearJson(cacheJson)
            if (conversas.isNotEmpty()) {
                val container = findViewById<LinearLayout>(R.id.containerConversas)
                exibirConversas(conversas, container)
                Log.d(TAG, "✅ Cache local exibido (${conversas.size} conversas)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao ler cache: ${e.message}")
        }
    }

    private fun sincronizarResumos() {
        if (emailUsuario.isEmpty()) return
        if (sincronizando) return
        sincronizando = true

        lifecycleScope.launch {
            try {
                val userDoc = db.collection("usuarios").document(emailUsuario).get().await()

                val chatsIds = (userDoc.get("chatsIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val gruposIds = (userDoc.get("gruposIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                val resumos = (userDoc.get("chatsResumo") as? List<*>)
                    ?.filterIsInstance<Map<String, Any>>()
                    ?.toMutableList() ?: mutableListOf()

                var alterou = false
                val fallbackNomeGrupo = getString(R.string.grupo_padrao)

                // ========== CHATS PV ==========
                for (chatId in chatsIds) {
                    try {
                        val chatDoc = db.collection("chats").document(chatId)
                            .get(com.google.firebase.firestore.Source.SERVER).await()
                        if (!chatDoc.exists()) continue

                        val timestampChat = chatDoc.getLong("atualizadoEm") ?: 0L
                        val resumoExistente = resumos.find { it["chatId"] == chatId }
                        val timestampResumo = resumoExistente?.get("timestamp") as? Long ?: 0L

                        val naoLidasMapAtual = chatDoc.get("naoLidas") as? Map<*, *>
                        val naoLidasChat = (naoLidasMapAtual?.get(emailUsuario) as? Number)?.toLong() ?: 0L
                        val naoLidasResumo = (resumoExistente?.get("naoLidas") as? Number)?.toLong() ?: 0L

                        if (timestampChat > timestampResumo || naoLidasChat != naoLidasResumo) {
                            val participantes = chatDoc.get("participantes") as? List<*>
                            val outroEmail = participantes?.firstOrNull { it != emailUsuario } as? String ?: continue

                            val outroUser = db.collection("usuarios").document(outroEmail).get().await()
                            val nomeOutro = outroUser.getString("nome") ?: outroEmail

                            val ultimaMsg = chatDoc.getString("ultimaMensagem") ?: ""

                            resumos.removeAll { it["chatId"] == chatId }
                            resumos.add(
                                mapOf(
                                    "chatId" to chatId,
                                    "nome" to nomeOutro,
                                    "ultimaMsg" to ultimaMsg,
                                    "timestamp" to maxOf(timestampChat, timestampResumo),
                                    "naoLidas" to naoLidasChat,
                                    "tipo" to "pv"
                                )
                            )
                            alterou = true
                        }
                    } catch (e: Exception) {
                        if (e.message?.contains("PERMISSION_DENIED") != true) {
                            Log.e(TAG, "Erro chat $chatId: ${e.message}")
                        }
                    }
                }

                // ========== GRUPOS ==========
                for (grupoId in gruposIds) {
                    try {
                        val grupoDoc = db.collection("grupos").document(grupoId).get().await()
                        if (!grupoDoc.exists()) continue

                        val timestampGrupo = grupoDoc.getLong("atualizadoEm") ?: 0L
                        val resumoExistente = resumos.find { it["chatId"] == grupoId }
                        val timestampResumo = resumoExistente?.get("timestamp") as? Long ?: 0L

                        val naoLidasMapAtual = grupoDoc.get("naoLidas") as? Map<*, *>
                        val naoLidasGrupo = (naoLidasMapAtual?.get(emailUsuario) as? Number)?.toLong() ?: 0L
                        val naoLidasResumo = (resumoExistente?.get("naoLidas") as? Number)?.toLong() ?: 0L

                        if (timestampGrupo > timestampResumo || naoLidasGrupo != naoLidasResumo) {
                            val nomeGrupo = grupoDoc.getString("nomeGrupo") ?: fallbackNomeGrupo
                            val ultimaMsg = grupoDoc.getString("ultimaMensagem") ?: ""

                            resumos.removeAll { it["chatId"] == grupoId }
                            resumos.add(
                                mapOf(
                                    "chatId" to grupoId,
                                    "nome" to nomeGrupo,
                                    "ultimaMsg" to ultimaMsg,
                                    "timestamp" to maxOf(timestampGrupo, timestampResumo),
                                    "naoLidas" to naoLidasGrupo,
                                    "tipo" to "grupo"
                                )
                            )
                            alterou = true
                        }
                    } catch (e: Exception) {
                        if (e.message?.contains("PERMISSION_DENIED") != true) {
                            Log.e(TAG, "Erro grupo $grupoId: ${e.message}")
                        }
                    }
                }

                // ========== SALVA ==========
                if (alterou) {
                    val ordenados = resumos.sortedByDescending { (it["timestamp"] as? Long) ?: 0L }
                    db.collection("usuarios").document(emailUsuario)
                        .update("chatsResumo", ordenados).await()

                    val docAtualizado = db.collection("usuarios").document(emailUsuario).get().await()
                    renderizarDoResumo(docAtualizado)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro sync: ${e.message}")
            } finally {
                sincronizando = false
            }
        }
    }

    private fun iniciarListenersDosChats() {
        listenersChats.forEach { it.remove() }
        listenersChats.clear()
        if (emailUsuario.isEmpty()) return

        lifecycleScope.launch {
            try {
                val chats = db.collection("chats")
                    .whereArrayContains("participantes", emailUsuario)
                    .get().await()

                chats.documents.forEach { chatDoc ->
                    val chatId = chatDoc.id
                    val listener = chatDoc.reference.addSnapshotListener { snapshot, error ->
                        if (error != null) return@addSnapshotListener
                        if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

                        val naoLidasMap = snapshot.get("naoLidas") as? Map<*, *>
                        val naoLidas = (naoLidasMap?.get(emailUsuario) as? Number)?.toInt() ?: 0

                        runOnUiThread { atualizarBadgeDoChat(chatId, naoLidas) }
                    }
                    listenersChats.add(listener)
                }

                val gruposIds = (db.collection("usuarios").document(emailUsuario).get().await()
                    .get("gruposIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                gruposIds.forEach { grupoId ->
                    val listener = db.collection("grupos").document(grupoId)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) return@addSnapshotListener
                            if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

                            val naoLidasMap = snapshot.get("naoLidas") as? Map<*, *>
                            val naoLidas = (naoLidasMap?.get(emailUsuario) as? Number)?.toInt() ?: 0

                            runOnUiThread { atualizarBadgeDoChat(grupoId, naoLidas) }
                        }
                    listenersChats.add(listener)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro listeners chats: ${e.message}")
            }
        }
    }

    private fun atualizarBadgeDoChat(chatId: String, naoLidas: Int) {
        val view = viewsPorChat[chatId] ?: return
        val txtBadge = view.findViewById<TextView>(R.id.txtBadgeConversa) ?: return

        if (naoLidas > 0) {
            txtBadge.text = if (naoLidas > 99) "99+" else naoLidas.toString()
            txtBadge.visibility = View.VISIBLE
        } else {
            txtBadge.visibility = View.GONE
        }
    }

    private fun salvarCacheLocal(conversas: List<ConversaItem>) {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            prefs.edit().putString(KEY_CACHE_CONVERSAS, gerarJson(conversas)).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar cache: ${e.message}")
        }
    }

    private fun gerarJson(conversas: List<ConversaItem>): String {
        val arr = JSONArray()
        conversas.forEach { c ->
            val obj = JSONObject()
            obj.put("tipo", c.tipo)
            obj.put("nome", c.nome)
            obj.put("ultimaMsg", c.ultimaMsg)
            obj.put("id", c.id)
            obj.put("outroEmail", c.outroEmail)
            obj.put("atualizadoEm", c.atualizadoEm)
            obj.put("naoLidas", c.naoLidas)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun parsearJson(json: String): List<ConversaItem> {
        val arr = JSONArray(json)
        val lista = mutableListOf<ConversaItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            lista.add(
                ConversaItem(
                    tipo = obj.getString("tipo"),
                    nome = obj.getString("nome"),
                    ultimaMsg = obj.getString("ultimaMsg"),
                    id = obj.getString("id"),
                    outroEmail = obj.optString("outroEmail", ""),
                    atualizadoEm = obj.getLong("atualizadoEm"),
                    naoLidas = obj.getInt("naoLidas")
                )
            )
        }
        return lista
    }

    private fun iniciarListenerConversas() {
        listenerUsuario?.remove()
        if (emailUsuario.isEmpty()) return

        listenerUsuario = db.collection("usuarios").document(emailUsuario)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Erro listener: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

                jobDebounce?.cancel()
                jobDebounce = lifecycleScope.launch {
                    delay(300)
                    renderizarDoResumo(snapshot)
                }
            }
    }

    private fun renderizarDoResumo(snapshot: com.google.firebase.firestore.DocumentSnapshot) {
        if (renderizando) return
        renderizando = true

        try {
            val resumos = snapshot.get("chatsResumo") as? List<*>
            val conversas = mutableListOf<ConversaItem>()
            val fallbackConversa = getString(R.string.conversa_padrao)

            resumos?.forEach { item ->
                val map = item as? Map<*, *> ?: return@forEach
                val chatId = map["chatId"] as? String ?: return@forEach
                val nome = map["nome"] as? String ?: fallbackConversa
                val ultimaMsg = map["ultimaMsg"] as? String ?: ""
                val timestamp = map["timestamp"] as? Long ?: 0L

                val naoLidasRaw = map["naoLidas"]
                val naoLidas = when (naoLidasRaw) {
                    is Number -> naoLidasRaw.toInt()
                    else -> 0
                }

                val tipo = map["tipo"] as? String ?: "pv"

                conversas.add(
                    ConversaItem(
                        tipo = tipo,
                        nome = nome,
                        ultimaMsg = ultimaMsg,
                        id = chatId,
                        outroEmail = if (tipo == "pv") nome else "",
                        atualizadoEm = timestamp,
                        naoLidas = naoLidas
                    )
                )
            }

            val container = findViewById<LinearLayout>(R.id.containerConversas)
            exibirConversas(conversas, container)
            salvarCacheLocal(conversas)

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao renderizar: ${e.message}")
        } finally {
            renderizando = false
        }
    }

    private fun migrarChatsResumo() {
        lifecycleScope.launch {
            try {
                val userDoc = db.collection("usuarios").document(emailUsuario).get().await()
                if (userDoc.contains("chatsResumo")) return@launch

                val chatsIds = (userDoc.get("chatsIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val gruposIds = (userDoc.get("gruposIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                val resumos = mutableListOf<Map<String, Any>>()
                val fallbackGrupo = getString(R.string.grupo_padrao)

                for (chatId in chatsIds) {
                    try {
                        val chat = db.collection("chats").document(chatId).get().await()
                        if (!chat.exists()) continue

                        val participantes = chat.get("participantes") as? List<*>
                        val outroEmail = participantes?.firstOrNull { it != emailUsuario } as? String ?: continue
                        val ultimaMsg = chat.getString("ultimaMensagem") ?: ""
                        val timestamp = chat.getLong("atualizadoEm") ?: 0L
                        val naoLidas = (chat.get("naoLidas") as? Map<*, *>)?.get(emailUsuario) as? Number ?: 0L

                        val outroUser = db.collection("usuarios").document(outroEmail).get().await()
                        val nome = outroUser.getString("nome") ?: outroEmail

                        resumos.add(
                            mapOf(
                                "chatId" to chatId,
                                "nome" to nome,
                                "ultimaMsg" to ultimaMsg,
                                "timestamp" to timestamp,
                                "naoLidas" to naoLidas.toLong(),
                                "tipo" to "pv"
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro chat $chatId: ${e.message}")
                    }
                }

                for (grupoId in gruposIds) {
                    try {
                        val grupo = db.collection("grupos").document(grupoId).get().await()
                        if (!grupo.exists()) continue

                        val nome = grupo.getString("nomeGrupo") ?: fallbackGrupo
                        val timestamp = grupo.getLong("atualizadoEm") ?: 0L
                        val naoLidas = (grupo.get("naoLidas") as? Map<*, *>)?.get(emailUsuario) as? Number ?: 0L

                        resumos.add(
                            mapOf(
                                "chatId" to grupoId,
                                "nome" to nome,
                                "ultimaMsg" to (grupo.getString("ultimaMensagem") ?: ""),
                                "timestamp" to timestamp,
                                "naoLidas" to naoLidas.toLong(),
                                "tipo" to "grupo"
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro grupo $grupoId: ${e.message}")
                    }
                }

                val ordenados = resumos.sortedByDescending { (it["timestamp"] as? Long) ?: 0L }
                db.collection("usuarios").document(emailUsuario)
                    .update("chatsResumo", ordenados).await()

            } catch (e: Exception) {
                Log.e(TAG, "Erro migração: ${e.message}")
            }
        }
    }

    private fun exibirConversas(itens: List<ConversaItem>, container: LinearLayout) {
        container.removeAllViews()
        viewsPorChat.clear()

        if (itens.isEmpty()) {
            val txtVazio = TextView(this@ListaConversasActivity).apply {
                text = getString(R.string.lista_conversas_vazio)
                setTextColor(ContextCompat.getColor(this@ListaConversasActivity, R.color.text_secondary))
                textSize = 14f
                setPadding(0, 60, 0, 60)
                gravity = android.view.Gravity.CENTER
            }
            container.addView(txtVazio)
            return
        }

        val inflater = LayoutInflater.from(this@ListaConversasActivity)

        itens.forEach { item ->
            val view = inflater.inflate(R.layout.item_conversa, container, false)

            val cardIcone = view.findViewById<MaterialCardView>(R.id.cardIconeConversa)
            val txtIcone = view.findViewById<TextView>(R.id.txtIconeConversa)
            val txtNome = view.findViewById<TextView>(R.id.txtNomeConversa)
            val txtUltima = view.findViewById<TextView>(R.id.txtUltimaMensagem)
            val txtBadge = view.findViewById<TextView>(R.id.txtBadgeConversa)

            txtNome.text = item.nome
            txtUltima.text = item.ultimaMsg

            if (item.tipo == "grupo") {
                txtIcone.text = getString(R.string.item_conversa_icone_grupo)
                cardIcone.setCardBackgroundColor(ContextCompat.getColor(this@ListaConversasActivity, R.color.linkedin_blue))
            } else {
                txtIcone.text = getString(R.string.item_conversa_icone_usuario)
                cardIcone.setCardBackgroundColor(ContextCompat.getColor(this@ListaConversasActivity, R.color.bg_surface_hover))
            }

            if (item.naoLidas > 0) {
                txtBadge.text = if (item.naoLidas > 99) "99+" else item.naoLidas.toString()
                txtBadge.visibility = View.VISIBLE
            } else {
                txtBadge.visibility = View.GONE
            }

            viewsPorChat[item.id] = view

            view.setOnClickListener {
                if (item.tipo == "grupo") {
                    val intent = Intent(this@ListaConversasActivity, ChatGrupoActivity::class.java)
                    intent.putExtra("grupoId", item.id)
                    intent.putExtra("nomeGrupo", item.nome)
                    startActivity(intent)
                } else {
                    val intent = Intent(this@ListaConversasActivity, ChatActivity::class.java)
                    intent.putExtra("outroEmail", item.outroEmail.ifEmpty { item.nome })
                    intent.putExtra("chatId", item.id)
                    startActivity(intent)
                }
            }

            if (item.tipo == "pv") {
                view.setOnLongClickListener {
                    AlertDialog.Builder(this@ListaConversasActivity)
                        .setTitle(item.nome)
                        .setItems(arrayOf(
                            getString(R.string.lista_conversas_apagar_conversa),
                            getString(R.string.lista_conversas_ver_perfil)
                        )) { _, which ->
                            when (which) {
                                0 -> apagarConversa(item.id)
                                1 -> abrirPerfil(item.outroEmail.ifEmpty { item.nome })
                            }
                        }
                        .show()
                    true
                }
            }

            container.addView(view)
        }
    }

    private fun apagarConversa(chatId: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.lista_conversas_apagar_conversa))
            .setItems(arrayOf(
                getString(R.string.lista_conversas_apagar_mensagens),
                getString(R.string.lista_conversas_apagar_inteira)
            )) { _, which ->
                when (which) {
                    0 -> apagarSomenteMensagens(chatId)
                    1 -> apagarConversaInteira(chatId)
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun apagarSomenteMensagens(chatId: String) {
        lifecycleScope.launch {
            try {
                val mensagens = db.collection("chats").document(chatId)
                    .collection("mensagens").get().await()

                mensagens.documents.forEach { msg ->
                    db.collection("chats").document(chatId)
                        .collection("mensagens").document(msg.id).delete().await()
                }

                db.collection("chats").document(chatId).update(
                    mapOf(
                        "ultimaMensagem" to "",
                        "atualizadoEm" to System.currentTimeMillis()
                    )
                ).await()

                Toast.makeText(this@ListaConversasActivity, getString(R.string.lista_conversas_mensagens_apagadas), Toast.LENGTH_SHORT).show()
                carregarConversasManual()
            } catch (e: Exception) {
                Toast.makeText(this@ListaConversasActivity, getString(R.string.erro_generico, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun apagarConversaInteira(chatId: String) {
        lifecycleScope.launch {
            try {
                val mensagens = db.collection("chats").document(chatId)
                    .collection("mensagens").get().await()

                mensagens.documents.forEach { msg ->
                    db.collection("chats").document(chatId)
                        .collection("mensagens").document(msg.id).delete().await()
                }

                db.collection("chats").document(chatId).delete().await()
                removerChatIdDoUsuario(emailUsuario, chatId)
                ChatResumoHelper.removerResumo(emailUsuario, chatId)

                Toast.makeText(this@ListaConversasActivity, getString(R.string.lista_conversas_conversa_apagada), Toast.LENGTH_SHORT).show()
                carregarConversasManual()
            } catch (e: Exception) {
                Toast.makeText(this@ListaConversasActivity, getString(R.string.erro_generico, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun carregarConversasManual() {
        lifecycleScope.launch {
            try {
                val doc = db.collection("usuarios").document(emailUsuario).get().await()
                renderizarDoResumo(doc)
            } catch (e: Exception) {
                Log.e(TAG, "Erro manual: ${e.message}")
            }
        }
    }

    private suspend fun removerChatIdDoUsuario(email: String, chatId: String) {
        try {
            val userRef = db.collection("usuarios").document(email)
            val userDoc = userRef.get().await()
            val ids = (userDoc.get("chatsIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            if (chatId in ids) userRef.update("chatsIds", ids - chatId).await()
        } catch (e: Exception) {
            Log.e(TAG, "Erro: ${e.message}")
        }
    }

    private fun abrirPerfil(email: String) {
        val intent = Intent(this, PerfilUsuarioActivity::class.java)
        intent.putExtra("emailOutro", email)
        startActivity(intent)
    }

    private fun mostrarDialogNovaConversa() {
        SeletorUsuarioHelper.abrir(this, emailUsuario) { email, nome ->
            lifecycleScope.launch {
                try {
                    val existente = db.collection("chats")
                        .whereArrayContains("participantes", emailUsuario)
                        .get().await()

                    val chatExistente = existente.documents.find { doc ->
                        (doc.get("participantes") as? List<*>)?.contains(email) == true
                    }

                    val chatId = if (chatExistente != null) {
                        atualizarChatsIds(emailUsuario, chatExistente.id)
                        atualizarChatsIds(email, chatExistente.id)
                        chatExistente.id
                    } else {
                        val novoChat = hashMapOf(
                            "participantes" to listOf(emailUsuario, email),
                            "ultimaMensagem" to "",
                            "atualizadoEm" to System.currentTimeMillis(),
                            "tipo" to "individual",
                            "naoLidas" to emptyMap<String, Long>()
                        )
                        val novoId = db.collection("chats").add(novoChat).await().id
                        atualizarChatsIds(emailUsuario, novoId)
                        atualizarChatsIds(email, novoId)
                        novoId
                    }

                    val intent = Intent(this@ListaConversasActivity, ChatActivity::class.java)
                    intent.putExtra("outroEmail", email)
                    intent.putExtra("chatId", chatId)
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this@ListaConversasActivity, getString(R.string.erro_generico, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun atualizarChatsIds(email: String, chatId: String) {
        try {
            val userRef = db.collection("usuarios").document(email)
            val userDoc = userRef.get().await()
            val ids = (userDoc.get("chatsIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            if (chatId !in ids) userRef.update("chatsIds", ids + chatId).await()
        } catch (e: Exception) {
            Log.e(TAG, "Erro: ${e.message}")
        }
    }

    data class ConversaItem(
        val tipo: String,
        val nome: String,
        val ultimaMsg: String,
        val id: String,
        val outroEmail: String,
        val atualizadoEm: Long,
        val naoLidas: Int
    )

    companion object {
        private const val TAG = "LISTA_CONVERSAS"
        private const val PREFS_NAME = "CTR_PREFS"
        private const val KEY_CACHE_CONVERSAS = "conversas_cache"
    }
}