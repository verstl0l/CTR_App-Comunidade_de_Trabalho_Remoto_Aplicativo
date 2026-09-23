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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChatGrupoActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""
    private var nomeUsuario: String = "Usuário"
    private var grupoId: String = ""

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
        setContentView(R.layout.activity_chat_grupo)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""
        grupoId = intent.getStringExtra("grupoId") ?: ""
        val nomeGrupo = intent.getStringExtra("nomeGrupo") ?: "Grupo"

        if (grupoId.isEmpty()) {
            Toast.makeText(this, "Grupo não encontrado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val btnSairGrupo = findViewById<Button>(R.id.btnSairGrupo)
        btnSairGrupo.setOnClickListener {
            confirmarSairGrupo()
        }

        val btnVoltar = findViewById<Button>(R.id.btnVoltarChatGrupo)
        val btnEnviar = findViewById<Button>(R.id.btnEnviarMensagemGrupo)
        val btnFoto = findViewById<Button>(R.id.btnEnviarFotoGrupo)
        val edtMensagem = findViewById<EditText>(R.id.edtMensagemGrupo)
        val txtNomeGrupo = findViewById<TextView>(R.id.txtNomeGrupoChat)

        txtNomeGrupo.text = nomeGrupo

        btnVoltar.setOnClickListener { finish() }

        lifecycleScope.launch {
            try {
                val userDoc = db.collection("usuarios").document(emailUsuario).get().await()
                nomeUsuario = userDoc.getString("nome") ?: "Usuário"
            } catch (e: Exception) {
                Log.e("CHAT_GRUPO", "Erro: ${e.message}")
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

    override fun onResume() {
        super.onResume()
        marcarMensagensComoLidas()
    }

    private fun marcarMensagensComoLidas() {
        if (grupoId.isEmpty()) return

        lifecycleScope.launch {
            try {
                val mensagens = db.collection("grupos").document(grupoId)
                    .collection("mensagens")
                    .whereEqualTo("lida", false)
                    .get()
                    .await()

                mensagens.documents.forEach { doc ->
                    val remetente = doc.getString("remetente") ?: ""
                    if (remetente != emailUsuario) {
                        db.collection("grupos").document(grupoId)
                            .collection("mensagens").document(doc.id)
                            .update("lida", true).await()
                    }
                }
            } catch (e: Exception) {
                // Silencioso
            }
        }
    }

    // ========== SAIR DO GRUPO ==========
    private fun confirmarSairGrupo() {
        lifecycleScope.launch {
            try {
                val grupoDoc = db.collection("grupos").document(grupoId).get().await()
                val criadorEmail = grupoDoc.getString("criadorEmail") ?: ""

                if (criadorEmail == emailUsuario) {
                    Toast.makeText(
                        this@ChatGrupoActivity,
                        "Você é o criador do grupo. Só pode excluí-lo.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                AlertDialog.Builder(this@ChatGrupoActivity)
                    .setTitle("Sair do grupo")
                    .setMessage("Tem certeza que deseja sair deste grupo?")
                    .setPositiveButton("Sair") { _, _ ->
                        lifecycleScope.launch {
                            try {
                                val membros = grupoDoc.get("membros") as? List<*> ?: emptyList<Any>()
                                val novosMembros = membros.filter { it != emailUsuario }

                                db.collection("grupos").document(grupoId)
                                    .update("membros", novosMembros).await()

                                Toast.makeText(this@ChatGrupoActivity, "Você saiu do grupo", Toast.LENGTH_SHORT).show()
                                finish()
                            } catch (e: Exception) {
                                Toast.makeText(this@ChatGrupoActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()

            } catch (e: Exception) {
                Toast.makeText(this@ChatGrupoActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun enviarMensagem(texto: String) {
        lifecycleScope.launch {
            try {
                val mensagem = hashMapOf(
                    "remetente" to emailUsuario,
                    "nomeRemetente" to nomeUsuario,
                    "texto" to texto,
                    "tipo" to "texto",
                    "timestamp" to System.currentTimeMillis(),
                    "lida" to false
                )

                db.collection("grupos").document(grupoId)
                    .collection("mensagens").add(mensagem).await()

            } catch (e: Exception) {
                Toast.makeText(this@ChatGrupoActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun enviarFoto(uri: Uri) {
        Toast.makeText(this, "📤 Enviando foto...", Toast.LENGTH_SHORT).show()

        MediaManager.get().upload(uri)
            .unsigned("fqb729sb")
            .option("folder", "chats_grupo/")
            .callback(object : com.cloudinary.android.callback.UploadCallback {
                override fun onStart(requestId: String?) {
                    Log.d("UPLOAD_GRUPO", "Iniciando upload...")
                }

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
                                        "timestamp" to System.currentTimeMillis(),
                                        "lida" to false
                                    )

                                    db.collection("grupos").document(grupoId)
                                        .collection("mensagens").add(mensagem).await()

                                    Toast.makeText(this@ChatGrupoActivity, "✅ Foto enviada!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(this@ChatGrupoActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@ChatGrupoActivity, "Erro: URL não encontrada", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    Log.e("UPLOAD_GRUPO", "Erro: ${error?.description}")
                    runOnUiThread {
                        Toast.makeText(this@ChatGrupoActivity, "Erro no upload: ${error?.description}", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            })
            .dispatch()
    }

    private fun carregarMensagens() {
        val container = findViewById<LinearLayout>(R.id.containerMensagensGrupo)
        val scroll = findViewById<ScrollView>(R.id.scrollMensagensGrupo)

        db.collection("grupos").document(grupoId)
            .collection("mensagens")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("CHAT_GRUPO", "Erro: ${error.message}")
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