package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ListaConversasActivity : AppCompatActivity() {

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

        configurarBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        carregarConversas()
    }

    private fun carregarConversas() {
        val container = findViewById<LinearLayout>(R.id.containerConversas)
        container.removeAllViews()

        lifecycleScope.launch {
            try {
                // Lista de itens: (tipo, nome, ultimaMsg, id, outroEmail)
                val itens = mutableListOf<ConversaItem>()

                // 1. Busca conversas PV
                val chatsPV = db.collection("chats")
                    .whereArrayContains("participantes", emailUsuario)
                    .get()
                    .await()

                chatsPV.documents.forEach { chat ->
                    val chatId = chat.id
                    val participantes = chat.get("participantes") as? List<*>
                    val outroEmail = participantes?.firstOrNull { it != emailUsuario } as? String ?: ""
                    val ultimaMsg = chat.getString("ultimaMensagem") ?: "Sem mensagens"
                    val atualizadoEm = chat.getLong("atualizadoEm") ?: 0L

                    val outroUsuario = db.collection("usuarios").document(outroEmail).get().await()
                    val nomeOutro = outroUsuario.getString("nome") ?: outroEmail

                    itens.add(
                        ConversaItem(
                            tipo = "pv",
                            nome = nomeOutro,
                            ultimaMsg = ultimaMsg,
                            id = chatId,
                            outroEmail = outroEmail,
                            atualizadoEm = atualizadoEm
                        )
                    )
                }

                // 2. Busca conversas de GRUPO que o usuário participa
                val grupos = db.collection("grupos")
                    .whereArrayContains("membros", emailUsuario)
                    .get()
                    .await()

                grupos.documents.forEach { grupo ->
                    val grupoId = grupo.id
                    val nomeGrupo = grupo.getString("nomeGrupo") ?: "Grupo"

                    // Busca a última mensagem do grupo
                    val ultimaMsgDoc = db.collection("grupos").document(grupoId)
                        .collection("mensagens")
                        .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .limit(1)
                        .get()
                        .await()

                    val ultimaMsg = if (!ultimaMsgDoc.isEmpty) {
                        val doc = ultimaMsgDoc.documents[0]
                        val nome = doc.getString("nomeRemetente") ?: "Usuário"
                        val texto = doc.getString("texto") ?: ""
                        val tipo = doc.getString("tipo") ?: "texto"
                        if (tipo == "foto") "$nome: 📷 Foto" else "$nome: $texto"
                    } else {
                        "Nenhuma mensagem ainda"
                    }

                    val timestamp = ultimaMsgDoc.documents.firstOrNull()?.getLong("timestamp") ?: 0L

                    itens.add(
                        ConversaItem(
                            tipo = "grupo",
                            nome = nomeGrupo,
                            ultimaMsg = ultimaMsg,
                            id = grupoId,
                            outroEmail = "",
                            atualizadoEm = timestamp
                        )
                    )
                }

                // 3. Ordena por atualizadoEm (mais recente primeiro)
                val itensOrdenados = itens.sortedByDescending { it.atualizadoEm }

                // 4. Mostra na tela
                if (itensOrdenados.isEmpty()) {
                    val txtVazio = TextView(this@ListaConversasActivity).apply {
                        text = "Nenhuma conversa ainda"
                        setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                        textSize = 14f
                        setPadding(0, 60, 0, 60)
                        gravity = android.view.Gravity.CENTER
                    }
                    container.addView(txtVazio)
                    return@launch
                }

                val inflater = LayoutInflater.from(this@ListaConversasActivity)

                itensOrdenados.forEach { item ->
                    val view = inflater.inflate(R.layout.item_conversa, container, false)

                    val cardIcone = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardIconeConversa)
                    val txtIcone = view.findViewById<TextView>(R.id.txtIconeConversa)
                    val txtNome = view.findViewById<TextView>(R.id.txtNomeConversa)
                    val txtUltima = view.findViewById<TextView>(R.id.txtUltimaMensagem)

                    txtNome.text = item.nome
                    txtUltima.text = item.ultimaMsg

                    if (item.tipo == "grupo") {
                        // Grupo: ícone 👥 e cor azul
                        txtIcone.text = "👥"
                        cardIcone.setCardBackgroundColor(android.graphics.Color.parseColor("#0A66C2"))
                    } else {
                        // PV: ícone 👤 e cor marrom
                        txtIcone.text = "👤"
                        cardIcone.setCardBackgroundColor(android.graphics.Color.parseColor("#3D2B27"))
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

                    // Long press: só para PV (apagar conversa)
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

            } catch (e: Exception) {
                Log.e("LISTA_CONVERSAS", "Erro: ${e.message}")
            }
        }
    }

    private fun apagarConversa(chatId: String) {
        AlertDialog.Builder(this)
            .setTitle("Apagar conversa")
            .setMessage("Tem certeza? Todas as mensagens serão removidas.")
            .setPositiveButton("Apagar") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val mensagens = db.collection("chats").document(chatId)
                            .collection("mensagens").get().await()
                        mensagens.documents.forEach { msg ->
                            db.collection("chats").document(chatId)
                                .collection("mensagens").document(msg.id).delete().await()
                        }
                        db.collection("chats").document(chatId).delete().await()
                        Toast.makeText(this@ListaConversasActivity, "Conversa apagada", Toast.LENGTH_SHORT).show()
                        carregarConversas()
                    } catch (e: Exception) {
                        Toast.makeText(this@ListaConversasActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun abrirPerfil(email: String) {
        val intent = Intent(this, PerfilUsuarioActivity::class.java)
        intent.putExtra("emailOutro", email)
        startActivity(intent)
    }

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
                            chatExistente.id
                        } else {
                            val novoChat = hashMapOf(
                                "participantes" to listOf(emailUsuario, emailOutro),
                                "ultimaMensagem" to "",
                                "atualizadoEm" to System.currentTimeMillis(),
                                "tipo" to "individual"
                            )
                            db.collection("chats").add(novoChat).await().id
                        }

                        val intent = Intent(this@ListaConversasActivity, ChatActivity::class.java)
                        intent.putExtra("outroEmail", emailOutro)
                        intent.putExtra("chatId", chatId)
                        startActivity(intent)

                    } catch (e: Exception) {
                        Toast.makeText(this@ListaConversasActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun configurarBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_chat -> true
                R.id.nav_groups -> {
                    startActivity(Intent(this, produtos::class.java))
                    finish()
                    true
                }
                R.id.nav_notifications -> {
                    startActivity(Intent(this, notificacao::class.java))
                    finish()
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, perfil::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    // Classe auxiliar
    data class ConversaItem(
        val tipo: String,        // "pv" ou "grupo"
        val nome: String,
        val ultimaMsg: String,
        val id: String,
        val outroEmail: String,
        val atualizadoEm: Long
    )
}