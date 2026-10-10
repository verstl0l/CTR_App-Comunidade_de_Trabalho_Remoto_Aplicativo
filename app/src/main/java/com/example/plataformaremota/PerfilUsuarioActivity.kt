package com.example.plataformaremota

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PerfilUsuarioActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""
    private var emailOutro: String = ""

    // ✅ Estado do bloqueio (substitui a comparação com texto do botão)
    private var estaBloqueado: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_perfil_usuario)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""
        emailOutro = intent.getStringExtra("emailOutro") ?: ""

        if (emailOutro.isEmpty()) {
            Toast.makeText(this, getString(R.string.perfil_usuario_nao_encontrado), Toast.LENGTH_SHORT).show()
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
            abrirConversa()
        }

        // ✅ Comparação via estado booleano — funciona em qualquer idioma
        btnBloquear.setOnClickListener {
            if (estaBloqueado) {
                desbloquear()
            } else {
                confirmarBloqueio()
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

                val nome = usuarioDoc.getString("nome") ?: getString(R.string.perfil_usuario_usuario_fallback)
                val profissao = usuarioDoc.getString("profissao") ?: getString(R.string.perfil_usuario_profissao_fallback)
                val fotoUrl = usuarioDoc.getString("fotoUrl") ?: ""

                val links = usuarioDoc.get("links") as? List<Map<String, String>> ?: emptyList()

                val linkedin = links.find { it["tipo"] == "linkedin" }?.get("url")
                    ?: usuarioDoc.getString("linkedin") ?: ""
                val github = links.find { it["tipo"] == "github" }?.get("url")
                    ?: usuarioDoc.getString("github") ?: ""
                val portfolio = links.find { it["tipo"] == "portfolio" }?.get("url")
                    ?: usuarioDoc.getString("portfolio") ?: ""

                findViewById<TextView>(R.id.txtNomePerfilUsuario).text = nome
                findViewById<TextView>(R.id.txtProfissaoPerfilUsuario).text = profissao
                findViewById<TextView>(R.id.txtEmailPerfilUsuario).text = emailOutro

                val imgAvatar = findViewById<ImageView>(R.id.imgAvatarUsuario)
                val txtIniciais = findViewById<TextView>(R.id.txtIniciaisUsuario)

                if (fotoUrl.isNotEmpty()) {
                    Glide.with(this@PerfilUsuarioActivity).load(fotoUrl).circleCrop().into(imgAvatar)
                    txtIniciais.visibility = View.GONE
                } else {
                    val iniciais = nome.split(" ").take(2)
                        .map { it.firstOrNull()?.uppercase() ?: "" }
                        .joinToString("")
                    txtIniciais.text = iniciais.ifEmpty {
                        getString(R.string.perfil_usuario_iniciais_fallback)
                    }
                    txtIniciais.visibility = View.VISIBLE
                }

                configurarLink(
                    findViewById(R.id.txtLinkedinUsuario),
                    linkedin,
                    getString(R.string.perfil_usuario_linkedin_vazio)
                )
                configurarLink(
                    findViewById(R.id.txtGithubUsuario),
                    github,
                    getString(R.string.perfil_usuario_github_vazio)
                )
                configurarLink(
                    findViewById(R.id.txtPortfolioUsuario),
                    portfolio,
                    getString(R.string.perfil_usuario_portfolio_vazio)
                )

            } catch (e: Exception) {
                Log.e(TAG, "Erro: ${e.message}")
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
                    Toast.makeText(
                        this,
                        getString(R.string.perfil_usuario_link_invalido),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun abrirConversa() {
        lifecycleScope.launch {
            try {
                val existente = db.collection("chats")
                    .whereArrayContains("participantes", emailUsuario)
                    .get()
                    .await()

                val chatExistente = existente.documents.find { doc ->
                    val parts = doc.get("participantes") as? List<*>
                    parts?.contains(emailOutro) == true
                }

                val chatId = chatExistente?.id ?: run {
                    val novoChat = hashMapOf(
                        "participantes" to listOf(emailUsuario, emailOutro),
                        "ultimaMensagem" to "",
                        "atualizadoEm" to System.currentTimeMillis(),
                        "tipo" to "individual"
                    )
                    db.collection("chats").add(novoChat).await().id
                }

                val intent = Intent(this@PerfilUsuarioActivity, ChatActivity::class.java)
                intent.putExtra("outroEmail", emailOutro)
                intent.putExtra("chatId", chatId)
                startActivity(intent)

            } catch (e: Exception) {
                Toast.makeText(
                    this@PerfilUsuarioActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

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
                    Toast.makeText(
                        this@PerfilUsuarioActivity,
                        getString(R.string.perfil_usuario_sem_conversa),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val chatId = chat.id

                val opcoes = arrayOf(
                    getString(R.string.perfil_usuario_apagar_mensagens),
                    getString(R.string.perfil_usuario_apagar_conversa_inteira)
                )

                AlertDialog.Builder(this@PerfilUsuarioActivity)
                    .setTitle(getString(R.string.perfil_usuario_apagar_conversa_titulo))
                    .setItems(opcoes) { _, which ->
                        when (which) {
                            0 -> apagarSomenteMensagens(chatId)
                            1 -> apagarConversaInteira(chatId)
                        }
                    }
                    .setNegativeButton(R.string.cancelar, null)
                    .show()

            } catch (e: Exception) {
                Toast.makeText(
                    this@PerfilUsuarioActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
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

                Toast.makeText(
                    this@PerfilUsuarioActivity,
                    getString(R.string.perfil_usuario_mensagens_apagadas),
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {
                Toast.makeText(
                    this@PerfilUsuarioActivity,
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

                Toast.makeText(
                    this@PerfilUsuarioActivity,
                    getString(R.string.perfil_usuario_conversa_apagada),
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {
                Toast.makeText(
                    this@PerfilUsuarioActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun verificarBloqueio() {
        lifecycleScope.launch {
            try {
                val bloqueio = db.collection("bloqueios")
                    .whereEqualTo("bloqueadorEmail", emailUsuario)
                    .whereEqualTo("bloqueadoEmail", emailOutro)
                    .limit(1)
                    .get().await()

                estaBloqueado = !bloqueio.isEmpty
                atualizarBotaoBloqueio()
            } catch (e: Exception) {
                Log.e(TAG, "Erro: ${e.message}")
            }
        }
    }

    private fun atualizarBotaoBloqueio() {
        val btnBloquear = findViewById<Button>(R.id.btnBloquear)
        btnBloquear.text = if (estaBloqueado) {
            getString(R.string.perfil_usuario_btn_desbloquear)
        } else {
            getString(R.string.perfil_usuario_btn_bloquear)
        }
    }

    private fun confirmarBloqueio() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.perfil_usuario_bloquear_titulo))
            .setMessage(getString(R.string.perfil_usuario_bloquear_msg))
            .setPositiveButton(getString(R.string.perfil_usuario_bloquear_confirmar)) { _, _ -> bloquear() }
            .setNegativeButton(R.string.cancelar, null)
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
                Toast.makeText(
                    this@PerfilUsuarioActivity,
                    getString(R.string.perfil_usuario_bloqueado_sucesso),
                    Toast.LENGTH_SHORT
                ).show()
                verificarBloqueio()
            } catch (e: Exception) {
                Toast.makeText(
                    this@PerfilUsuarioActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
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

                Toast.makeText(
                    this@PerfilUsuarioActivity,
                    getString(R.string.perfil_usuario_desbloqueado_sucesso),
                    Toast.LENGTH_SHORT
                ).show()
                verificarBloqueio()
            } catch (e: Exception) {
                Toast.makeText(
                    this@PerfilUsuarioActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        private const val TAG = "PERFIL_USUARIO"
    }
}