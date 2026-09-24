package com.example.plataformaremota

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await


class PerfilUsuarioActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""
    private var emailOutro: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_perfil_usuario)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""
        emailOutro = intent.getStringExtra("emailOutro") ?: ""

        if (emailOutro.isEmpty()) {
            Toast.makeText(this, "Usuário não encontrado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val btnVoltar = findViewById<Button>(R.id.btnVoltarPerfilUsuario)
        btnVoltar.setOnClickListener { finish() }

        val btnConversar = findViewById<Button>(R.id.btnConversar)
        val btnBloquear = findViewById<Button>(R.id.btnBloquear)
        val btnApagarConversa = findViewById<Button>(R.id.btnApagarConversa)

        carregarDados()
        verificarBloqueio()

        btnConversar.setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("outroEmail", emailOutro)
            startActivity(intent)
        }

        btnBloquear.setOnClickListener {
            if (btnBloquear.text == "BLOQUEAR USUÁRIO") {
                confirmarBloqueio()
            } else {
                desbloquear()
            }
        }

        btnApagarConversa.setOnClickListener {
            abrirDialogApagarConversa()
        }
    }

    private fun carregarDados() {
        lifecycleScope.launch {
            try {
                val usuarioDoc = db.collection("usuarios").document(emailOutro).get().await()

                val nome = usuarioDoc.getString("nome") ?: "Usuário"
                val profissao = usuarioDoc.getString("profissao") ?: "Profissão"
                val fotoUrl = usuarioDoc.getString("fotoUrl") ?: ""
                val linkedin = usuarioDoc.getString("linkedin") ?: ""
                val github = usuarioDoc.getString("github") ?: ""
                val portfolio = usuarioDoc.getString("portfolio") ?: ""

                findViewById<TextView>(R.id.txtNomePerfilUsuario).text = nome
                findViewById<TextView>(R.id.txtProfissaoPerfilUsuario).text = profissao
                findViewById<TextView>(R.id.txtEmailPerfilUsuario).text = emailOutro

                val imgAvatar = findViewById<ImageView>(R.id.imgAvatarUsuario)
                val txtIniciais = findViewById<TextView>(R.id.txtIniciaisUsuario)

                if (fotoUrl.isNotEmpty()) {
                    Glide.with(this@PerfilUsuarioActivity).load(fotoUrl).circleCrop().into(imgAvatar)
                    txtIniciais.visibility = android.view.View.GONE
                } else {
                    val iniciais = nome.split(" ").take(2)
                        .map { it.firstOrNull()?.uppercase() ?: "" }
                        .joinToString("")
                    txtIniciais.text = iniciais.ifEmpty { "US" }
                    txtIniciais.visibility = android.view.View.VISIBLE
                }

                configurarLink(findViewById(R.id.txtLinkedinUsuario), linkedin, "LinkedIn não cadastrado")
                configurarLink(findViewById(R.id.txtGithubUsuario), github, "GitHub não cadastrado")
                configurarLink(findViewById(R.id.txtPortfolioUsuario), portfolio, "Portfólio não cadastrado")

            } catch (e: Exception) {
                Log.e("PERFIL_USUARIO", "Erro: ${e.message}")
            }
        }
    }

    private fun configurarLink(textView: TextView, url: String, textoVazio: String) {
        if (url.isEmpty()) {
            textView.text = textoVazio
            textView.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
            textView.setOnClickListener(null)
        } else {
            textView.text = url
            textView.setTextColor(android.graphics.Color.parseColor("#F5E6D0"))
            textView.setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    Toast.makeText(this, "Link inválido", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ========== APAGAR CONVERSA ==========
    private fun abrirDialogApagarConversa() {
        lifecycleScope.launch {
            try {
                val chats = db.collection("chats")
                    .whereArrayContains("participantes", emailUsuario)
                    .get()
                    .await()

                val chat = chats.documents.find { doc ->
                    val parts = doc.get("participantes") as? List<*>
                    parts?.contains(emailOutro) == true
                }

                if (chat == null) {
                    Toast.makeText(this@PerfilUsuarioActivity, "Nenhuma conversa encontrada", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val chatId = chat.id

                AlertDialog.Builder(this@PerfilUsuarioActivity)
                    .setTitle("Apagar conversa")
                    .setItems(arrayOf("Apagar mensagens", "Apagar conversa inteira")) { _, which ->
                        when (which) {
                            0 -> apagarSomenteMensagens(chatId)
                            1 -> apagarConversaInteira(chatId)
                        }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()

            } catch (e: Exception) {
                Toast.makeText(this@PerfilUsuarioActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
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

                Toast.makeText(this@PerfilUsuarioActivity, "Mensagens apagadas", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Toast.makeText(this@PerfilUsuarioActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
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

                Toast.makeText(this@PerfilUsuarioActivity, "Conversa apagada", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Toast.makeText(this@PerfilUsuarioActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ========== BLOQUEAR / DESBLOQUEAR ==========
    private fun verificarBloqueio() {
        lifecycleScope.launch {
            try {
                val bloqueio = db.collection("bloqueios")
                    .whereEqualTo("bloqueadorEmail", emailUsuario)
                    .whereEqualTo("bloqueadoEmail", emailOutro)
                    .limit(1)
                    .get().await()

                val btnBloquear = findViewById<Button>(R.id.btnBloquear)
                if (!bloqueio.isEmpty) {
                    btnBloquear.text = "DESBLOQUEAR USUÁRIO"
                } else {
                    btnBloquear.text = "BLOQUEAR USUÁRIO"
                }
            } catch (e: Exception) {
                Log.e("PERFIL_USUARIO", "Erro: ${e.message}")
            }
        }
    }

    private fun confirmarBloqueio() {
        AlertDialog.Builder(this)
            .setTitle("Bloquear usuário")
            .setMessage("Tem certeza? Ele não poderá mais enviar mensagens para você.")
            .setPositiveButton("Bloquear") { _, _ -> bloquear() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun bloquear() {
        lifecycleScope.launch {
            try {
                val bloqueio = hashMapOf(
                    "bloqueadorEmail" to emailUsuario,
                    "bloqueadoEmail" to emailOutro,
                    "criadoEm" to System.currentTimeMillis()
                )
                db.collection("bloqueios").add(bloqueio).await()
                Toast.makeText(this@PerfilUsuarioActivity, "✅ Usuário bloqueado", Toast.LENGTH_SHORT).show()
                verificarBloqueio()
            } catch (e: Exception) {
                Toast.makeText(this@PerfilUsuarioActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun desbloquear() {
        lifecycleScope.launch {
            try {
                val bloqueios = db.collection("bloqueios")
                    .whereEqualTo("bloqueadorEmail", emailUsuario)
                    .whereEqualTo("bloqueadoEmail", emailOutro)
                    .get().await()

                bloqueios.documents.forEach { doc ->
                    db.collection("bloqueios").document(doc.id).delete().await()
                }

                Toast.makeText(this@PerfilUsuarioActivity, "✅ Desbloqueado", Toast.LENGTH_SHORT).show()
                verificarBloqueio()
            } catch (e: Exception) {
                Toast.makeText(this@PerfilUsuarioActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}