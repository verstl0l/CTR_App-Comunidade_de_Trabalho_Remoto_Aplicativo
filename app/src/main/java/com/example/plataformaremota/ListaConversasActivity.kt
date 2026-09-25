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
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ListaConversasActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lista_conversas)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""

        val btnNovaConversa = findViewById<Button>(R.id.btnNovaConversa)
        btnNovaConversa.setOnClickListener { mostrarDialogNovaConversa() }

        // ✅ Bottom nav em 1 linha
        configurarBottomNavigation(R.id.nav_chat)
    }

    override fun onResume() {
        super.onResume()
        carregarConversas()
    }

    // ============================================================
    // ✅ ALTERADO: usa chatsIds/gruposIds (offline-friendly)
    // ✅ Otimizado: ~2 queries iniciais em vez de N+1
    // ============================================================
    private fun carregarConversas() {
        val container = findViewById<LinearLayout>(R.id.containerConversas)
        container.removeAllViews()

        lifecycleScope.launch {
            try {
                val itens = mutableListOf<ConversaItem>()

                // ✅ 1 query: usuário com IDs denormalizados
                val usuarioDoc = db.collection("usuarios").document(emailUsuario).get().await()
                val chatsIds = (usuarioDoc.get("chatsIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val gruposIds = (usuarioDoc.get("gruposIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                // ========== 1. CHATS PV ==========
                for (chatId in chatsIds) {
                    try {
                        val chat = db.collection("chats").document(chatId).get().await()
                        if (!chat.exists()) continue

                        val participantes = chat.get("participantes") as? List<*>
                        val outroEmail = participantes?.firstOrNull { it != emailUsuario } as? String ?: continue
                        val ultimaMsg = chat.getString("ultimaMensagem") ?: "Sem mensagens"
                        val atualizadoEm = chat.getLong("atualizadoEm") ?: 0L

                        val naoLidas = chat.reference.collection("mensagens")
                            .whereEqualTo("lida", false)
                            .get().await()

                        val countNaoLidas = naoLidas.documents.count {
                            it.getString("remetente") != emailUsuario
                        }

                        val outroUsuario = db.collection("usuarios").document(outroEmail).get().await()
                        val nomeOutro = outroUsuario.getString("nome") ?: outroEmail

                        itens.add(
                            ConversaItem(
                                tipo = "pv",
                                nome = nomeOutro,
                                ultimaMsg = ultimaMsg,
                                id = chatId,
                                outroEmail = outroEmail,
                                atualizadoEm = atualizadoEm,
                                naoLidas = countNaoLidas
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("LISTA_CONVERSAS", "Erro chat $chatId: ${e.message}")
                    }
                }

                // ========== 2. GRUPOS ==========
                for (grupoId in gruposIds) {
                    try {
                        val grupo = db.collection("grupos").document(grupoId).get().await()
                        if (!grupo.exists()) continue

                        val nomeGrupo = grupo.getString("nomeGrupo") ?: "Grupo"

                        val ultimaMsgDoc = grupo.reference.collection("mensagens")
                            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                            .limit(1)
                            .get().await()

                        val ultimaMsg = if (!ultimaMsgDoc.isEmpty) {
                            val doc = ultimaMsgDoc.documents[0]
                            val nome = doc.getString("nomeRemetente") ?: "Usuário"
                            val texto = doc.getString("texto") ?: ""
                            val tipo = doc.getString("tipo") ?: "texto"
                            when (tipo) {
                                "foto" -> "$nome: 📷 Foto"
                                "video" -> "$nome: 🎥 Vídeo"
                                "arquivo" -> "$nome: 📎 Arquivo"
                                else -> "$nome: $texto"
                            }
                        } else "Nenhuma mensagem ainda"

                        val timestamp = ultimaMsgDoc.documents.firstOrNull()?.getLong("timestamp") ?: 0L

                        val naoLidas = grupo.reference.collection("mensagens")
                            .whereEqualTo("lida", false)
                            .get().await()

                        val countNaoLidas = naoLidas.documents.count {
                            it.getString("remetente") != emailUsuario
                        }

                        itens.add(
                            ConversaItem(
                                tipo = "grupo",
                                nome = nomeGrupo,
                                ultimaMsg = ultimaMsg,
                                id = grupoId,
                                outroEmail = "",
                                atualizadoEm = timestamp,
                                naoLidas = countNaoLidas
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("LISTA_CONVERSAS", "Erro grupo $grupoId: ${e.message}")
                    }
                }

                // ========== 3. ORDENA E MOSTRA ==========
                val itensOrdenados = itens.sortedByDescending { it.atualizadoEm }
                exibirConversas(itensOrdenados, container)

            } catch (e: Exception) {
                Log.e("LISTA_CONVERSAS", getString(R.string.erro_generico, e.message ?: ""))
            }
        }
    }

    private fun exibirConversas(itens: List<ConversaItem>, container: LinearLayout) {
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

            view.setOnClickListener {
                if (item.tipo == "grupo") {
                    val intent = Intent(this@ListaConversasActivity, ChatGrupoActivity::class.java)
                    intent.putExtra("grupoId", item.id)
                    intent.putExtra("nomeGrupo", item.nome)
                    startActivity(intent)
                } else {
                    val intent = Intent(this@ListaConversasActivity, ChatActivity::class.java)
                    intent.putExtra("outroEmail", item.outroEmail)
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
                                1 -> abrirPerfil(item.outroEmail)
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
                carregarConversas()

            } catch (e: Exception) {
                Toast.makeText(
                    this@ListaConversasActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
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

                // ✅ Remove o chatId da lista do usuário
                removerChatIdDoUsuario(emailUsuario, chatId)

                Toast.makeText(this@ListaConversasActivity, "Conversa apagada", Toast.LENGTH_SHORT).show()
                carregarConversas()

            } catch (e: Exception) {
                Toast.makeText(
                    this@ListaConversasActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private suspend fun removerChatIdDoUsuario(email: String, chatId: String) {
        try {
            val userRef = db.collection("usuarios").document(email)
            val userDoc = userRef.get().await()
            val ids = (userDoc.get("chatsIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

            if (chatId in ids) {
                userRef.update("chatsIds", ids - chatId).await()
            }
        } catch (e: Exception) {
            Log.e("LISTA_CONVERSAS", "Erro ao remover chatId: ${e.message}")
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
                            // ✅ Garante chatsIds
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

                            // ✅ Adiciona chatId aos dois
                            atualizarChatsIds(emailUsuario, novoId)
                            atualizarChatsIds(emailOutro, novoId)

                            novoId
                        }

                        val intent = Intent(this@ListaConversasActivity, ChatActivity::class.java)
                        intent.putExtra("outroEmail", emailOutro)
                        intent.putExtra("chatId", chatId)
                        startActivity(intent)

                    } catch (e: Exception) {
                        Toast.makeText(
                            this@ListaConversasActivity,
                            getString(R.string.erro_generico, e.message ?: ""),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    /**
     * ✅ Adiciona o chatId na lista `chatsIds` do usuário (idempotente).
     */
    private suspend fun atualizarChatsIds(email: String, chatId: String) {
        try {
            val userRef = db.collection("usuarios").document(email)
            val userDoc = userRef.get().await()
            val ids = (userDoc.get("chatsIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

            if (chatId !in ids) {
                userRef.update("chatsIds", ids + chatId).await()
                Log.d("LISTA_CONVERSAS", "✅ chatsIds + $chatId para $email")
            }
        } catch (e: Exception) {
            Log.e("LISTA_CONVERSAS", "Erro ao atualizar chatsIds: ${e.message}")
        }
    }

    // ============================================================
    // DATA CLASS
    // ============================================================
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