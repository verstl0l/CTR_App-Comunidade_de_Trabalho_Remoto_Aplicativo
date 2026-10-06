package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
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

    // Listener em tempo real no doc do usuário (chatsResumo)
    private var listenerUsuario: ListenerRegistration? = null

    // Listeners em tempo real em CADA chat (pra badge)
    private val listenersChats = mutableListOf<ListenerRegistration>()

    // Mapa: chatId -> view do item (pra atualizar badge sem re-renderizar tudo)
    private val viewsPorChat = mutableMapOf<String, View>()

    // Guard contra sincronização duplicada
    private var sincronizando = false

    // Debounce pra evitar múltiplas renderizações
    private var jobDebounce: Job? = null

    // Guard contra renderização duplicada
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

        // 1. Mostra cache instantâneo
        mostrarCacheLocal()

        // 2. Sincroniza resumos
        sincronizarResumos()

        // 3. Inicia listener do doc do usuário (chatsResumo)
        iniciarListenerConversas()

        // 4. Inicia listeners de CADA chat (pra badge em tempo real)
        iniciarListenersDosChats()

        // 5. Migra dados antigos
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

    // ============================================================
    // CACHE LOCAL — instantâneo
    // ============================================================
    private fun mostrarCacheLocal() {
        try {
            val prefs = getSharedPreferences("CTR_PREFS", MODE_PRIVATE)
            val cacheJson = prefs.getString("conversas_cache", "") ?: ""
            if (cacheJson.isEmpty()) return

            val conversas = parsearJson(cacheJson)
            if (conversas.isNotEmpty()) {
                val container = findViewById<LinearLayout>(R.id.containerConversas)
                exibirConversas(conversas, container)
                Log.d("LISTA_CONVERSAS", "✅ Cache local exibido (${conversas.size} conversas)")
            }
        } catch (e: Exception) {
            Log.e("LISTA_CONVERSAS", "Erro ao ler cache: ${e.message}")
        }
    }

    // ============================================================
    // SINCRONIZAÇÃO DE RESUMOS
    // ============================================================
    private fun sincronizarResumos() {
        if (emailUsuario.isEmpty()) return

        // ✅ Guard: evita rodar 2x em paralelo
        if (sincronizando) {
            Log.d("LISTA_CONVERSAS", "⏳ Já sincronizando, pulando...")
            return
        }
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

                // ========== CHATS PV ==========
                for (chatId in chatsIds) {
                    try {
                        val chatDoc = db.collection("chats").document(chatId).get().await()
                        if (!chatDoc.exists()) continue

                        val timestampChat = chatDoc.getLong("atualizadoEm") ?: 0L
                        val resumoExistente = resumos.find { it["chatId"] == chatId }
                        val timestampResumo = resumoExistente?.get("timestamp") as? Long ?: 0L

                        if (timestampChat > timestampResumo) {
                            val participantes = chatDoc.get("participantes") as? List<*>
                            val outroEmail = participantes?.firstOrNull { it != emailUsuario } as? String ?: continue

                            val outroUser = db.collection("usuarios").document(outroEmail).get().await()
                            val nomeOutro = outroUser.getString("nome") ?: outroEmail

                            val ultimaMsg = chatDoc.getString("ultimaMensagem") ?: ""
                            val naoLidasMap = chatDoc.get("naoLidas") as? Map<*, *>
                            val naoLidas = (naoLidasMap?.get(emailUsuario) as? Number)?.toLong() ?: 0L

                            resumos.removeAll { it["chatId"] == chatId }
                            resumos.add(
                                mapOf(
                                    "chatId" to chatId,
                                    "nome" to nomeOutro,
                                    "ultimaMsg" to ultimaMsg,
                                    "timestamp" to timestampChat,
                                    "naoLidas" to naoLidas,
                                    "tipo" to "pv"
                                )
                            )
                            alterou = true
                            Log.d("LISTA_CONVERSAS", "🔄 Sincronizado chat: $chatId")
                        }
                    } catch (e: Exception) {
                        // ✅ Silencia PERMISSION_DENIED (chat que não é seu)
                        if (e.message?.contains("PERMISSION_DENIED") == true) {
                            Log.d("LISTA_CONVERSAS", "Chat $chatId não acessível, ignorando")
                        } else {
                            Log.e("LISTA_CONVERSAS", "Erro chat $chatId: ${e.message}")
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

                        if (timestampGrupo > timestampResumo) {
                            val nomeGrupo = grupoDoc.getString("nomeGrupo") ?: "Grupo"
                            val ultimaMsg = grupoDoc.getString("ultimaMensagem") ?: ""
                            val naoLidasMap = grupoDoc.get("naoLidas") as? Map<*, *>
                            val naoLidas = (naoLidasMap?.get(emailUsuario) as? Number)?.toLong() ?: 0L

                            resumos.removeAll { it["chatId"] == grupoId }
                            resumos.add(
                                mapOf(
                                    "chatId" to grupoId,
                                    "nome" to nomeGrupo,
                                    "ultimaMsg" to ultimaMsg,
                                    "timestamp" to timestampGrupo,
                                    "naoLidas" to naoLidas,
                                    "tipo" to "grupo"
                                )
                            )
                            alterou = true
                            Log.d("LISTA_CONVERSAS", "🔄 Sincronizado grupo: $grupoId")
                        }
                    } catch (e: Exception) {
                        if (e.message?.contains("PERMISSION_DENIED") == true) {
                            Log.d("LISTA_CONVERSAS", "Grupo $grupoId não acessível, ignorando")
                        } else {
                            Log.e("LISTA_CONVERSAS", "Erro grupo $grupoId: ${e.message}")
                        }
                    }
                }

                // ========== SALVA ==========
                if (alterou) {
                    val ordenados = resumos.sortedByDescending { (it["timestamp"] as? Long) ?: 0L }
                    db.collection("usuarios").document(emailUsuario)
                        .update("chatsResumo", ordenados).await()

                    Log.d("LISTA_CONVERSAS", "✅ Sincronização completa: ${ordenados.size} resumos")

                    val docAtualizado = db.collection("usuarios").document(emailUsuario).get().await()
                    renderizarDoResumo(docAtualizado)
                } else {
                    Log.d("LISTA_CONVERSAS", "Nada a sincronizar")
                }

            } catch (e: Exception) {
                Log.e("LISTA_CONVERSAS", "Erro sync: ${e.message}")
            } finally {
                // ✅ Libera o guard
                sincronizando = false
            }
        }
    }

    // ============================================================
    // LISTENERS DOS CHATS — badge em tempo real
    // ============================================================
    private fun iniciarListenersDosChats() {
        listenersChats.forEach { it.remove() }
        listenersChats.clear()

        if (emailUsuario.isEmpty()) return

        lifecycleScope.launch {
            try {
                val chats = db.collection("chats")
                    .whereArrayContains("participantes", emailUsuario)
                    .get().await()

                Log.d("LISTA_CONVERSAS", "🔔 Registrando ${chats.size()} listeners de chats")

                chats.documents.forEach { chatDoc ->
                    val chatId = chatDoc.id
                    val listener = chatDoc.reference.addSnapshotListener { snapshot, error ->
                        if (error != null) return@addSnapshotListener
                        if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

                        val naoLidasMap = snapshot.get("naoLidas") as? Map<*, *>
                        val naoLidas = (naoLidasMap?.get(emailUsuario) as? Number)?.toInt() ?: 0

                        runOnUiThread {
                            atualizarBadgeDoChat(chatId, naoLidas)
                        }
                    }
                    listenersChats.add(listener)
                }

                // Também registra listeners pros GRUPOS
                val gruposIds = (db.collection("usuarios").document(emailUsuario).get().await()
                    .get("gruposIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                gruposIds.forEach { grupoId ->
                    val listener = db.collection("grupos").document(grupoId)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) return@addSnapshotListener
                            if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

                            val naoLidasMap = snapshot.get("naoLidas") as? Map<*, *>
                            val naoLidas = (naoLidasMap?.get(emailUsuario) as? Number)?.toInt() ?: 0

                            runOnUiThread {
                                atualizarBadgeDoChat(grupoId, naoLidas)
                            }
                        }
                    listenersChats.add(listener)
                }

            } catch (e: Exception) {
                Log.e("LISTA_CONVERSAS", "Erro listeners chats: ${e.message}")
            }
        }
    }

    /**
     * Atualiza o badge APENAS do chat que mudou (sem re-renderizar a lista toda).
     */
    private fun atualizarBadgeDoChat(chatId: String, naoLidas: Int) {
        val view = viewsPorChat[chatId] ?: return
        val txtBadge = view.findViewById<TextView>(R.id.txtBadgeConversa) ?: return

        if (naoLidas > 0) {
            txtBadge.text = if (naoLidas > 99) "99+" else naoLidas.toString()
            txtBadge.visibility = View.VISIBLE
            Log.d("LISTA_CONVERSAS", "🔔 Badge $chatId = $naoLidas")
        } else {
            txtBadge.visibility = View.GONE
        }
    }

    private fun salvarCacheLocal(conversas: List<ConversaItem>) {
        try {
            val prefs = getSharedPreferences("CTR_PREFS", MODE_PRIVATE)
            prefs.edit().putString("conversas_cache", gerarJson(conversas)).apply()
        } catch (e: Exception) {
            Log.e("LISTA_CONVERSAS", "Erro ao salvar cache: ${e.message}")
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

    // ============================================================
    // LISTENER EM TEMPO REAL (chatsResumo)
    // ============================================================
    private fun iniciarListenerConversas() {
        listenerUsuario?.remove()

        if (emailUsuario.isEmpty()) return

        listenerUsuario = db.collection("usuarios").document(emailUsuario)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("LISTA_CONVERSAS", "Erro listener: ${error.message}")
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

            resumos?.forEach { item ->
                val map = item as? Map<*, *> ?: return@forEach
                val chatId = map["chatId"] as? String ?: return@forEach
                val nome = map["nome"] as? String ?: "Conversa"
                val ultimaMsg = map["ultimaMsg"] as? String ?: ""
                val timestamp = map["timestamp"] as? Long ?: 0L

                // ✅ Lê naoLidas de forma robusta
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

            Log.d("LISTA_CONVERSAS", "✅ Lista renderizada do resumo (${conversas.size} conversas)")

        } catch (e: Exception) {
            Log.e("LISTA_CONVERSAS", "Erro ao renderizar: ${e.message}")
        } finally {
            renderizando = false
        }
    }

    // ============================================================
    // MIGRAÇÃO
    // ============================================================
    private fun migrarChatsResumo() {
        lifecycleScope.launch {
            try {
                val userDoc = db.collection("usuarios").document(emailUsuario).get().await()

                if (userDoc.contains("chatsResumo")) {
                    Log.d("MIGRACAO_RESUMO", "Já migrado, pulando")
                    return@launch
                }

                Log.d("MIGRACAO_RESUMO", "Iniciando migração...")

                val chatsIds = (userDoc.get("chatsIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val gruposIds = (userDoc.get("gruposIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                val resumos = mutableListOf<Map<String, Any>>()

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
                        Log.e("MIGRACAO_RESUMO", "Erro chat $chatId: ${e.message}")
                    }
                }

                for (grupoId in gruposIds) {
                    try {
                        val grupo = db.collection("grupos").document(grupoId).get().await()
                        if (!grupo.exists()) continue

                        val nome = grupo.getString("nomeGrupo") ?: "Grupo"
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
                        Log.e("MIGRACAO_RESUMO", "Erro grupo $grupoId: ${e.message}")
                    }
                }

                val ordenados = resumos.sortedByDescending { (it["timestamp"] as? Long) ?: 0L }
                db.collection("usuarios").document(emailUsuario)
                    .update("chatsResumo", ordenados).await()

                Log.d("MIGRACAO_RESUMO", "✅ Migração completa: ${ordenados.size} resumos")

            } catch (e: Exception) {
                Log.e("MIGRACAO_RESUMO", "Erro: ${e.message}")
            }
        }
    }

    // ============================================================
    // EXIBIR CONVERSAS
    // ============================================================
    private fun exibirConversas(itens: List<ConversaItem>, container: LinearLayout) {
        container.removeAllViews()
        viewsPorChat.clear()

        if (itens.isEmpty()) {
            val txtVazio = TextView(this@ListaConversasActivity).apply {
                text = "Nenhuma conversa ainda"
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
                txtIcone.text = "👥"
                cardIcone.setCardBackgroundColor(ContextCompat.getColor(this@ListaConversasActivity, R.color.linkedin_blue))
            } else {
                txtIcone.text = "👤"
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
                        .setItems(arrayOf("Apagar conversa", "Ver perfil")) { _, which ->
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

    // ============================================================
    // APAGAR CONVERSA
    // ============================================================
    private fun apagarConversa(chatId: String) {
        AlertDialog.Builder(this)
            .setTitle("Apagar conversa")
            .setItems(arrayOf("Apagar mensagens", "Apagar conversa inteira")) { _, which ->
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

                Toast.makeText(this@ListaConversasActivity, "Mensagens apagadas", Toast.LENGTH_SHORT).show()
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

                Toast.makeText(this@ListaConversasActivity, "Conversa apagada", Toast.LENGTH_SHORT).show()
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
                Log.e("LISTA_CONVERSAS", "Erro manual: ${e.message}")
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
            Log.e("LISTA_CONVERSAS", "Erro: ${e.message}")
        }
    }

    private fun abrirPerfil(email: String) {
        val intent = Intent(this, PerfilUsuarioActivity::class.java)
        intent.putExtra("emailOutro", email)
        startActivity(intent)
    }

    // ============================================================
    // NOVA CONVERSA
    // ============================================================
    private fun mostrarDialogNovaConversa() {
        val edtEmail = EditText(this).apply {
            hint = "Email do usuário"
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(this)
            .setTitle("Nova conversa")
            .setView(edtEmail)
            .setPositiveButton("Iniciar") { _, _ ->
                val emailOutro = edtEmail.text.toString().trim().lowercase()
                if (emailOutro.isEmpty()) return@setPositiveButton

                if (emailOutro == emailUsuario) {
                    Toast.makeText(this, "Você não pode conversar consigo mesmo", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    try {
                        val usuario = db.collection("usuarios").document(emailOutro).get().await()
                        if (!usuario.exists()) {
                            Toast.makeText(this@ListaConversasActivity, "Usuário não encontrado", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        val existente = db.collection("chats")
                            .whereArrayContains("participantes", emailUsuario)
                            .get().await()

                        val chatExistente = existente.documents.find { doc ->
                            val parts = doc.get("participantes") as? List<*>
                            parts?.contains(emailOutro) == true
                        }

                        val chatId = if (chatExistente != null) {
                            atualizarChatsIds(emailUsuario, chatExistente.id)
                            atualizarChatsIds(emailOutro, chatExistente.id)
                            chatExistente.id
                        } else {
                            val novoChat = hashMapOf(
                                "participantes" to listOf(emailUsuario, emailOutro),
                                "ultimaMensagem" to "",
                                "atualizadoEm" to System.currentTimeMillis(),
                                "tipo" to "individual"
                            )
                            val novoId = db.collection("chats").add(novoChat).await().id
                            atualizarChatsIds(emailUsuario, novoId)
                            atualizarChatsIds(emailOutro, novoId)
                            novoId
                        }

                        val intent = Intent(this@ListaConversasActivity, ChatActivity::class.java)
                        intent.putExtra("outroEmail", emailOutro)
                        intent.putExtra("chatId", chatId)
                        startActivity(intent)

                    } catch (e: Exception) {
                        Toast.makeText(this@ListaConversasActivity, getString(R.string.erro_generico, e.message ?: ""), Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private suspend fun atualizarChatsIds(email: String, chatId: String) {
        try {
            val userRef = db.collection("usuarios").document(email)
            val userDoc = userRef.get().await()
            val ids = (userDoc.get("chatsIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            if (chatId !in ids) userRef.update("chatsIds", ids + chatId).await()
        } catch (e: Exception) {
            Log.e("LISTA_CONVERSAS", "Erro: ${e.message}")
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
}