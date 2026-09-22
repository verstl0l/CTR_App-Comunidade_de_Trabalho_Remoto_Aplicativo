package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChatActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""
    private var outroEmail: String = ""
    private var chatId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""
        outroEmail = intent.getStringExtra("outroEmail") ?: ""
        chatId = intent.getStringExtra("chatId") ?: ""

        if (chatId.isEmpty()) {
            lifecycleScope.launch {
                chatId = criarOuBuscarChat()
                configurarUI()
            }
        } else {
            configurarUI()
        }
    }

    private fun configurarUI() {
        val btnVoltar = findViewById<Button>(R.id.btnVoltarChat)
        val btnVerPerfil = findViewById<Button>(R.id.btnVerPerfil)
        val btnEnviar = findViewById<Button>(R.id.btnEnviar)
        val edtMensagem = findViewById<EditText>(R.id.edtMensagem)
        val txtNomeOutro = findViewById<TextView>(R.id.txtNomeOutro)

        btnVoltar.setOnClickListener { finish() }

        btnVerPerfil.setOnClickListener {
            val intent = Intent(this, PerfilUsuarioActivity::class.java)
            intent.putExtra("emailOutro", outroEmail)
            startActivity(intent)
        }

        lifecycleScope.launch {
            try {
                val usuario = db.collection("usuarios").document(outroEmail).get().await()
                txtNomeOutro.text = usuario.getString("nome") ?: outroEmail
            } catch (e: Exception) {
                txtNomeOutro.text = outroEmail
            }
        }

        verificarBloqueio(edtMensagem, btnEnviar)

        btnEnviar.setOnClickListener {
            val texto = edtMensagem.text.toString().trim()
            if (texto.isEmpty()) return@setOnClickListener

            edtMensagem.text.clear()
            enviarMensagem(texto)
        }

        carregarMensagens()
    }

    private fun verificarBloqueio(edtMensagem: EditText, btnEnviar: Button) {
        lifecycleScope.launch {
            try {
                val euBloqueei = db.collection("bloqueios")
                    .whereEqualTo("bloqueadorEmail", emailUsuario)
                    .whereEqualTo("bloqueadoEmail", outroEmail)
                    .limit(1)
                    .get().await()

                val eleMeBloqueou = db.collection("bloqueios")
                    .whereEqualTo("bloqueadorEmail", outroEmail)
                    .whereEqualTo("bloqueadoEmail", emailUsuario)
                    .limit(1)
                    .get().await()

                if (!euBloqueei.isEmpty) {
                    edtMensagem.isEnabled = false
                    edtMensagem.hint = "Você bloqueou este usuário"
                    btnEnviar.isEnabled = false
                } else if (!eleMeBloqueou.isEmpty) {
                    edtMensagem.isEnabled = false
                    edtMensagem.hint = "Você foi bloqueado por este usuário"
                    btnEnviar.isEnabled = false
                }

            } catch (e: Exception) {
                Log.e("CHAT", "Erro bloqueio: ${e.message}")
            }
        }
    }

    private suspend fun criarOuBuscarChat(): String {
        val existente = db.collection("chats")
            .whereArrayContains("participantes", emailUsuario)
            .get().await()

        val chatExistente = existente.documents.find { doc ->
            val parts = doc.get("participantes") as? List<*>
            parts?.contains(outroEmail) == true
        }

        return if (chatExistente != null) {
            chatExistente.id
        } else {
            val novoChat = hashMapOf(
                "participantes" to listOf(emailUsuario, outroEmail),
                "ultimaMensagem" to "",
                "atualizadoEm" to System.currentTimeMillis(),
                "tipo" to "individual"
            )
            db.collection("chats").add(novoChat).await().id
        }
    }

    private fun enviarMensagem(texto: String) {
        lifecycleScope.launch {
            try {
                val mensagem = hashMapOf(
                    "remetente" to emailUsuario,
                    "texto" to texto,
                    "timestamp" to System.currentTimeMillis()
                )

                db.collection("chats").document(chatId)
                    .collection("mensagens").add(mensagem).await()

                db.collection("chats").document(chatId).update(
                    mapOf(
                        "ultimaMensagem" to texto,
                        "atualizadoEm" to System.currentTimeMillis()
                    )
                ).await()

            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun carregarMensagens() {
        val container = findViewById<LinearLayout>(R.id.containerMensagens)
        val scroll = findViewById<ScrollView>(R.id.scrollMensagens)

        db.collection("chats").document(chatId).collection("mensagens")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("CHAT", "Erro: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshots == null) return@addSnapshotListener

                container.removeAllViews()
                val inflater = LayoutInflater.from(this)

                snapshots.documents.forEach { doc ->
                    val remetente = doc.getString("remetente") ?: ""
                    val texto = doc.getString("texto") ?: ""

                    val view = inflater.inflate(android.R.layout.simple_list_item_1, container, false)
                    val tv = view.findViewById<TextView>(android.R.id.text1)

                    if (remetente == emailUsuario) {
                        tv.text = "Você: $texto"
                        tv.setTextColor(android.graphics.Color.parseColor("#F5E6D0"))
                    } else {
                        tv.text = texto
                        tv.setTextColor(android.graphics.Color.WHITE)
                    }
                    tv.textSize = 16f
                    view.setPadding(0, 12, 0, 12)

                    container.addView(view)
                }

                scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
            }
    }
}