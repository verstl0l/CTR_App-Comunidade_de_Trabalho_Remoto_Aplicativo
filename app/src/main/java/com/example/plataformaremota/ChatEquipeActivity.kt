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

    // ✅ Launcher FOTO
    private val selecionarImagem = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) enviarFoto(uri)
        }
    }

    // ✅ Launcher VÍDEO
    private val selecionarVideo = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) enviarVideo(uri)
        }
    }

    // ✅ Launcher ARQUIVO
    private val selecionarArquivo = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) enviarArquivo(uri)
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
        val btnVideo = findViewById<Button>(R.id.btnEnviarVideoEquipe)
        val btnArquivo = findViewById<Button>(R.id.btnEnviarArquivoEquipe)
        val btnGrupos = findViewById<Button>(R.id.btnVerGrupos)
        val edtMensagem = findViewById<EditText>(R.id.edtMensagemEquipe)
        val txtNomeEquipe = findViewById<TextView>(R.id.txtNomeEquipeChat)

        btnVoltar.setOnClickListener { finish() }

        btnGrupos.setOnClickListener {
            val intent = Intent(this, InfoEquipeActivity::class.java)
            intent.putExtra("equipeId", equipeId)
            startActivity(intent)
        }

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

        // ✅ FOTO
        btnFoto.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            selecionarImagem.launch(intent)
        }

        // ✅ VÍDEO
        btnVideo.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "video/*"
            selecionarVideo.launch(intent)
        }

        // ✅ ARQUIVO
        btnArquivo.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*"
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            selecionarArquivo.launch(intent)
        }

        carregarMensagens()
    }

    // ========== ENVIAR TEXTO ==========
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

    // ========== ENVIAR FOTO ==========
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

    // ========== ENVIAR VÍDEO ==========
    private fun enviarVideo(uri: Uri) {
        Toast.makeText(this, "📤 Enviando vídeo...", Toast.LENGTH_SHORT).show()

        MediaManager.get().upload(uri)
            .unsigned("fqb729sb")
            .option("resource_type", "video")
            .option("folder", "chats_equipe_videos/")
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
                                        "videoUrl" to url,
                                        "tipo" to "video",
                                        "timestamp" to System.currentTimeMillis()
                                    )
                                    db.collection("chats_equipe").document(equipeId)
                                        .collection("mensagens").add(mensagem).await()
                                    Toast.makeText(this@ChatEquipeActivity, "✅ Vídeo enviado!", Toast.LENGTH_SHORT).show()
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

    // ========== ENVIAR ARQUIVO ==========
    private fun enviarArquivo(uri: Uri) {
        Toast.makeText(this, "📤 Enviando arquivo...", Toast.LENGTH_SHORT).show()

        var nomeArquivo = "arquivo"
        var tamanhoArquivo = 0L
        var mimeType = "application/octet-stream"

        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (nameIndex != -1) nomeArquivo = cursor.getString(nameIndex) ?: "arquivo"
                    if (sizeIndex != -1) tamanhoArquivo = cursor.getLong(sizeIndex)
                }
            }
            mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        } catch (e: Exception) { }

        val nomeFinal = nomeArquivo
        val tamanhoFinal = tamanhoArquivo
        val mimeFinal = mimeType

        MediaManager.get().upload(uri)
            .unsigned("fqb729sb")
            .option("resource_type", "raw")
            .option("folder", "chats_equipe_arquivos/")
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
                                        "arquivoUrl" to url,
                                        "nomeArquivo" to nomeFinal,
                                        "tamanhoArquivo" to tamanhoFinal,
                                        "mimeType" to mimeFinal,
                                        "tipo" to "arquivo",
                                        "timestamp" to System.currentTimeMillis()
                                    )
                                    db.collection("chats_equipe").document(equipeId)
                                        .collection("mensagens").add(mensagem).await()
                                    Toast.makeText(this@ChatEquipeActivity, "✅ Arquivo enviado!", Toast.LENGTH_SHORT).show()
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

    // ========== CARREGAR MENSAGENS ==========
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
                    val videoUrl = doc.getString("videoUrl") ?: ""
                    val arquivoUrl = doc.getString("arquivoUrl") ?: ""
                    val nomeArquivo = doc.getString("nomeArquivo") ?: "arquivo"
                    val tamanhoArquivo = doc.getLong("tamanhoArquivo") ?: 0L
                    val mimeType = doc.getString("mimeType") ?: ""   // ✅ LINHA QUE FALTAVA

                    when (tipo) {
                        // ========== FOTO ==========
                        "foto" -> {
                            val view = inflater.inflate(R.layout.item_mensagem_foto, container, false)
                            val img = view.findViewById<ImageView>(R.id.imgMensagemFoto)
                            val txtNome = view.findViewById<TextView>(R.id.txtNomeFoto)

                            txtNome.text = if (remetente == emailUsuario) "Você" else nomeRemetente
                            Glide.with(this).load(fotoUrl).into(img)

                            // ✅ Clique → tela cheia
                            view.setOnClickListener {
                                val intent = Intent(this, VisualizarMidiaActivity::class.java)
                                intent.putExtra("tipo", "foto")
                                intent.putExtra("url", fotoUrl)
                                startActivity(intent)
                            }

                            container.addView(view)
                        }

                        // ========== VÍDEO ==========
                        "video" -> {
                            val view = inflater.inflate(R.layout.item_mensagem_video, container, false)
                            val videoView = view.findViewById<android.widget.VideoView>(R.id.videoMensagem)
                            val btnPlay = view.findViewById<Button>(R.id.btnPlayVideo)
                            val txtNome = view.findViewById<TextView>(R.id.txtNomeVideo)

                            txtNome.text = if (remetente == emailUsuario) "Você" else nomeRemetente

                            val mediaController = android.widget.MediaController(this)
                            mediaController.setAnchorView(videoView)
                            videoView.setMediaController(mediaController)
                            videoView.setVideoURI(Uri.parse(videoUrl))

                            btnPlay.setOnClickListener {
                                if (videoView.isPlaying) {
                                    videoView.pause()
                                    btnPlay.text = "▶"
                                    btnPlay.visibility = android.view.View.VISIBLE
                                } else {
                                    videoView.start()
                                    btnPlay.visibility = android.view.View.GONE
                                }
                            }

                            videoView.setOnCompletionListener {
                                btnPlay.text = "▶"
                                btnPlay.visibility = android.view.View.VISIBLE
                            }

                            // ✅ Clique → tela cheia
                            videoView.setOnClickListener {
                                val intent = Intent(this, VisualizarMidiaActivity::class.java)
                                intent.putExtra("tipo", "video")
                                intent.putExtra("url", videoUrl)
                                startActivity(intent)
                            }

                            container.addView(view)
                        }

                        // ========== ARQUIVO ==========
                        "arquivo" -> {
                            val view = inflater.inflate(R.layout.item_mensagem_arquivo, container, false)
                            val txtNomeRem = view.findViewById<TextView>(R.id.txtNomeArquivoRemetente)
                            val txtNomeArq = view.findViewById<TextView>(R.id.txtNomeArquivo)
                            val txtTamanho = view.findViewById<TextView>(R.id.txtTamanhoArquivo)

                            txtNomeRem.text = if (remetente == emailUsuario) "Você" else nomeRemetente
                            txtNomeArq.text = nomeArquivo
                            txtTamanho.text = formatarTamanho(tamanhoArquivo)

                            view.setOnClickListener {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(arquivoUrl), mimeType.ifEmpty { "*/*" })
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(this, "Nenhum app para abrir", Toast.LENGTH_LONG).show()
                                }
                            }

                            container.addView(view)
                        }

                        // ========== TEXTO ==========
                        else -> {
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
                }

                scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
            }
    }

    private fun formatarTamanho(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}