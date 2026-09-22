package com.example.plataformaremota

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChatEquipeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""
    private var nomeUsuario: String = "Usuário"
    private var equipeId: String = ""

    private val selecionarImagem = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageUri = result.data?.data
            if (imageUri != null) {
                enviarFoto(imageUri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_equipe)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""
        equipeId = intent.getStringExtra("equipeId") ?: ""

        if (equipeId.isEmpty()) {
            Toast.makeText(this, "Equipe não encontrada", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val btnVoltar = findViewById<Button>(R.id.btnVoltarChatEquipe)
        val btnEnviar = findViewById<Button>(R.id.btnEnviarMensagemEquipe)
        val btnFoto = findViewById<Button>(R.id.btnEnviarFotoEquipe)
        val btnGrupos = findViewById<Button>(R.id.btnVerGrupos)
        val edtMensagem = findViewById<EditText>(R.id.edtMensagemEquipe)
        val txtNomeEquipe = findViewById<TextView>(R.id.txtNomeEquipeChat)

        btnVoltar.setOnClickListener { finish() }

        btnGrupos.setOnClickListener {
            val intent = Intent(this, InfoEquipeActivity::class.java)
            intent.putExtra("equipeId", equipeId)
            startActivity(intent)
        }

        // Busca nome do usuário e da equipe
        lifecycleScope.launch {
            try {
                val userDoc = db.collection("usuarios").document(emailUsuario).get().await()
                nomeUsuario = userDoc.getString("nome") ?: "Usuário"

                val equipeDoc = db.collection("equipes").document(equipeId).get().await()
                txtNomeEquipe.text = equipeDoc.getString("nome") ?: "Equipe"
            } catch (e: Exception) {
                Log.e("CHAT_EQUIPE", "Erro: ${e.message}")
            }
        }

        btnEnviar.setOnClickListener {
            val texto = edtMensagem.text.toString().trim()
            if (texto.isEmpty()) return@setOnClickListener

            edtMensagem.text.clear()
            enviarMensagem(texto)
        }

        btnFoto.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            selecionarImagem.launch(intent)
        }

        carregarMensagens()
    }

    private fun enviarMensagem(texto: String) {
        lifecycleScope.launch {
            try {
                val mensagem = hashMapOf(
                    "remetente" to emailUsuario,
                    "nomeRemetente" to nomeUsuario,
                    "texto" to texto,
                    "tipo" to "texto",
                    "timestamp" to System.currentTimeMillis()
                )

                db.collection("chats_equipe").document(equipeId)
                    .collection("mensagens").add(mensagem).await()

                db.collection("chats_equipe").document(equipeId).set(
                    mapOf(
                        "equipeId" to equipeId,
                        "ultimaMensagem" to texto,
                        "atualizadoEm" to System.currentTimeMillis()
                    )
                )

            } catch (e: Exception) {
                Toast.makeText(this@ChatEquipeActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun enviarFoto(uri: Uri) {
        Toast.makeText(this, "📤 Enviando foto...", Toast.LENGTH_SHORT).show()

        MediaManager.get().upload(uri)
            .unsigned("fqb729sb")
            .option("folder", "chats_equipe/")
            .callback(object : com.cloudinary.android.callback.UploadCallback {
                override fun onStart(requestId: String?) {}

                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}

                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val url = resultData?.get("secure_url") as? String
                    if (url != null) {
                        runOnUiThread {
                            lifecycleScope.launch {
                                try {
                                    val mensagem = hashMapOf(
                                        "remetente" to emailUsuario,
                                        "nomeRemetente" to nomeUsuario,
                                        "texto" to "",
                                        "fotoUrl" to url,
                                        "tipo" to "foto",
                                        "timestamp" to System.currentTimeMillis()
                                    )

                                    db.collection("chats_equipe").document(equipeId)
                                        .collection("mensagens").add(mensagem).await()

                                    Toast.makeText(this@ChatEquipeActivity, "✅ Foto enviada!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(this@ChatEquipeActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }

                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    runOnUiThread {
                        Toast.makeText(this@ChatEquipeActivity, "Erro: ${error?.description}", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            })
            .dispatch()
    }

    private fun carregarMensagens() {
        val container = findViewById<LinearLayout>(R.id.containerMensagensEquipe)
        val scroll = findViewById<ScrollView>(R.id.scrollMensagensEquipe)

        db.collection("chats_equipe").document(equipeId)
            .collection("mensagens")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("CHAT_EQUIPE", "Erro: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshots == null) return@addSnapshotListener

                container.removeAllViews()
                val inflater = LayoutInflater.from(this)

                snapshots.documents.forEach { doc ->
                    val remetente = doc.getString("remetente") ?: ""
                    val nomeRemetente = doc.getString("nomeRemetente") ?: "Usuário"
                    val texto = doc.getString("texto") ?: ""
                    val tipo = doc.getString("tipo") ?: "texto"
                    val fotoUrl = doc.getString("fotoUrl") ?: ""

                    if (tipo == "foto") {
                        val view = inflater.inflate(R.layout.item_mensagem_foto, container, false)
                        val img = view.findViewById<ImageView>(R.id.imgMensagemFoto)
                        val txtNome = view.findViewById<TextView>(R.id.txtNomeFoto)

                        txtNome.text = if (remetente == emailUsuario) "Você" else nomeRemetente
                        Glide.with(this).load(fotoUrl).into(img)

                        container.addView(view)
                    } else {
                        val view = inflater.inflate(android.R.layout.simple_list_item_1, container, false)
                        val tv = view.findViewById<TextView>(android.R.id.text1)

                        if (remetente == emailUsuario) {
                            tv.text = "Você: $texto"
                            tv.setTextColor(android.graphics.Color.parseColor("#F5E6D0"))
                        } else {
                            tv.text = "$nomeRemetente: $texto"
                            tv.setTextColor(android.graphics.Color.WHITE)
                        }
                        tv.textSize = 16f
                        view.setPadding(0, 12, 0, 12)

                        container.addView(view)
                    }
                }

                scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
            }
    }
}