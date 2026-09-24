package com.example.plataformaremota

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
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

class ChatActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""
    private var outroEmail: String = ""
    private var chatId: String = ""

    private val selecionarImagem = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) enviarFoto(uri)
        }
    }

    private val selecionarVideo = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) enviarVideo(uri)
        }
    }

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

    override fun onResume() {
        super.onResume()
        marcarMensagensComoLidas()
    }

    private fun marcarMensagensComoLidas() {
        if (chatId.isEmpty()) return
        lifecycleScope.launch {
            try {
                val mensagens = db.collection("chats").document(chatId)
                    .collection("mensagens")
                    .whereEqualTo("lida", false)
                    .get().await()

                mensagens.documents.forEach { doc ->
                    val remetente = doc.getString("remetente") ?: ""
                    if (remetente != emailUsuario) {
                        db.collection("chats").document(chatId)
                            .collection("mensagens").document(doc.id)
                            .update("lida", true).await()
                    }
                }
            } catch (e: Exception) { }
        }
    }

    private fun configurarUI() {
        val btnVoltar = findViewById<Button>(R.id.btnVoltarChat)
        val btnVerPerfil = findViewById<Button>(R.id.btnVerPerfil)
        val btnEnviar = findViewById<Button>(R.id.btnEnviar)
        val btnFoto = findViewById<Button>(R.id.btnEnviarFoto)
        val btnVideo = findViewById<Button>(R.id.btnEnviarVideo)
        val btnArquivo = findViewById<Button>(R.id.btnEnviarArquivo)
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

        btnFoto.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            selecionarImagem.launch(intent)
        }

        btnVideo.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "video/*"
            selecionarVideo.launch(intent)
        }

        btnArquivo.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*"
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            selecionarArquivo.launch(intent)
        }

        carregarMensagens()
    }

    private fun verificarBloqueio(edtMensagem: EditText, btnEnviar: Button) {
        lifecycleScope.launch {
            try {
                val euBloqueei = db.collection("bloqueios")
                    .whereEqualTo("bloqueadorEmail", emailUsuario)
                    .whereEqualTo("bloqueadoEmail", outroEmail)
                    .limit(1).get().await()

                val eleMeBloqueou = db.collection("bloqueios")
                    .whereEqualTo("bloqueadorEmail", outroEmail)
                    .whereEqualTo("bloqueadoEmail", emailUsuario)
                    .limit(1).get().await()

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
                    "tipo" to "texto",
                    "timestamp" to System.currentTimeMillis(),
                    "lida" to false
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

    private fun enviarFoto(uri: Uri) {
        Toast.makeText(this, "📤 Enviando foto...", Toast.LENGTH_SHORT).show()

        MediaManager.get().upload(uri)
            .unsigned("fqb729sb")
            .option("folder", "chats_pv/")
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
                                        "texto" to "",
                                        "fotoUrl" to url,
                                        "tipo" to "foto",
                                        "timestamp" to System.currentTimeMillis(),
                                        "lida" to false
                                    )
                                    db.collection("chats").document(chatId)
                                        .collection("mensagens").add(mensagem).await()
                                    Toast.makeText(this@ChatActivity, "✅ Foto enviada!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(this@ChatActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }
                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    runOnUiThread {
                        Toast.makeText(this@ChatActivity, "Erro: ${error?.description}", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            })
            .dispatch()
    }

    private fun enviarVideo(uri: Uri) {
        Toast.makeText(this, "📤 Enviando vídeo...", Toast.LENGTH_SHORT).show()

        MediaManager.get().upload(uri)
            .unsigned("fqb729sb")
            .option("resource_type", "video")
            .option("folder", "chats_videos/")
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
                                        "texto" to "",
                                        "videoUrl" to url,
                                        "tipo" to "video",
                                        "timestamp" to System.currentTimeMillis(),
                                        "lida" to false
                                    )
                                    db.collection("chats").document(chatId)
                                        .collection("mensagens").add(mensagem).await()
                                    Toast.makeText(this@ChatActivity, "✅ Vídeo enviado!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(this@ChatActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }
                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    runOnUiThread {
                        Toast.makeText(this@ChatActivity, "Erro: ${error?.description}", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            })
            .dispatch()
    }

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
            .option("folder", "chats_arquivos/")
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
                                        "texto" to "",
                                        "arquivoUrl" to url,
                                        "nomeArquivo" to nomeFinal,
                                        "tamanhoArquivo" to tamanhoFinal,
                                        "mimeType" to mimeFinal,
                                        "tipo" to "arquivo",
                                        "timestamp" to System.currentTimeMillis(),
                                        "lida" to false
                                    )
                                    db.collection("chats").document(chatId)
                                        .collection("mensagens").add(mensagem).await()
                                    Toast.makeText(this@ChatActivity, "✅ Arquivo enviado!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(this@ChatActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }
                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    runOnUiThread {
                        Toast.makeText(this@ChatActivity, "Erro: ${error?.description}", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            })
            .dispatch()
    }

    private fun carregarMensagens() {
        val container = findViewById<LinearLayout>(R.id.containerMensagens)
        val scroll = findViewById<ScrollView>(R.id.scrollMensagens)

        db.collection("chats").document(chatId).collection("mensagens")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) return@addSnapshotListener
                if (snapshots == null) return@addSnapshotListener

                container.removeAllViews()
                val inflater = LayoutInflater.from(this)

                val listaMidias = mutableListOf<Pair<String, String>>()

                snapshots.documents.forEach { doc ->
                    val tipo = doc.getString("tipo") ?: "texto"
                    when (tipo) {
                        "foto" -> {
                            val url = doc.getString("fotoUrl") ?: ""
                            if (url.isNotEmpty()) listaMidias.add("foto" to url)
                        }
                        "video" -> {
                            val url = doc.getString("videoUrl") ?: ""
                            if (url.isNotEmpty()) listaMidias.add("video" to url)
                        }
                    }
                }

                var indexMidia = 0

                snapshots.documents.forEach { doc ->
                    val msgId = doc.id
                    val remetente = doc.getString("remetente") ?: ""
                    val texto = doc.getString("texto") ?: ""
                    val tipo = doc.getString("tipo") ?: "texto"
                    val fotoUrl = doc.getString("fotoUrl") ?: ""
                    val videoUrl = doc.getString("videoUrl") ?: ""
                    val arquivoUrl = doc.getString("arquivoUrl") ?: ""
                    val nomeArquivo = doc.getString("nomeArquivo") ?: "arquivo"
                    val tamanhoArquivo = doc.getLong("tamanhoArquivo") ?: 0L
                    val mimeType = doc.getString("mimeType") ?: ""

                    val ehRemetente = remetente == emailUsuario

                    when (tipo) {
                        "foto" -> {
                            val view = inflater.inflate(R.layout.item_mensagem_foto, container, false)
                            val img = view.findViewById<ImageView>(R.id.imgMensagemFoto)
                            val txtNome = view.findViewById<TextView>(R.id.txtNomeFoto)

                            txtNome.text = if (ehRemetente) "Você" else outroEmail
                            Glide.with(this).load(fotoUrl).into(img)

                            val posicaoNaLista = indexMidia
                            indexMidia++

                            view.setOnClickListener {
                                abrirGaleria(listaMidias, posicaoNaLista)
                            }

                            view.setOnLongClickListener {
                                mostrarMenuMidia("foto", fotoUrl, "", mimeType, msgId, ehRemetente)
                                true
                            }
                            container.addView(view)
                        }

                        "video" -> {
                            val view = inflater.inflate(R.layout.item_mensagem_video, container, false)
                            val videoView = view.findViewById<VideoView>(R.id.videoMensagem)
                            val btnPlay = view.findViewById<Button>(R.id.btnPlayVideo)
                            val txtNome = view.findViewById<TextView>(R.id.txtNomeVideo)

                            txtNome.text = if (ehRemetente) "Você" else outroEmail

                            val mediaController = MediaController(this)
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

                            val posicaoNaLista = indexMidia
                            indexMidia++

                            videoView.setOnClickListener {
                                abrirGaleria(listaMidias, posicaoNaLista)
                            }

                            view.setOnLongClickListener {
                                mostrarMenuMidia("video", videoUrl, "", "", msgId, ehRemetente)
                                true
                            }
                            container.addView(view)
                        }

                        "arquivo" -> {
                            val view = inflater.inflate(R.layout.item_mensagem_arquivo, container, false)
                            val txtNomeRem = view.findViewById<TextView>(R.id.txtNomeArquivoRemetente)
                            val txtNomeArq = view.findViewById<TextView>(R.id.txtNomeArquivo)
                            val txtTamanho = view.findViewById<TextView>(R.id.txtTamanhoArquivo)

                            txtNomeRem.text = if (ehRemetente) "Você" else outroEmail
                            txtNomeArq.text = nomeArquivo
                            txtTamanho.text = formatarTamanho(tamanhoArquivo)

                            view.setOnClickListener {
                                abrirArquivoExterno(arquivoUrl, mimeType, nomeArquivo)
                            }

                            view.setOnLongClickListener {
                                mostrarMenuMidia("arquivo", arquivoUrl, nomeArquivo, mimeType, msgId, ehRemetente)
                                true
                            }
                            container.addView(view)
                        }

                        else -> {
                            val view = inflater.inflate(android.R.layout.simple_list_item_1, container, false)
                            val tv = view.findViewById<TextView>(android.R.id.text1)

                            if (ehRemetente) {
                                tv.text = "Você: $texto"
                                tv.setTextColor(android.graphics.Color.parseColor("#F5E6D0"))
                            } else {
                                tv.text = texto
                                tv.setTextColor(android.graphics.Color.WHITE)
                            }
                            tv.textSize = 16f
                            view.setPadding(0, 12, 0, 12)

                            if (ehRemetente) {
                                view.setOnLongClickListener {
                                    mostrarOpcaoApagar(msgId)
                                    true
                                }
                            }
                            container.addView(view)
                        }
                    }
                }

                scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
            }
    }

    private fun abrirGaleria(listaMidias: List<Pair<String, String>>, posicaoInicial: Int) {
        val urls = listaMidias.map { it.second }.toTypedArray()
        val tipos = listaMidias.map { it.first }.toTypedArray()

        val intent = Intent(this, GaleriaMidiaActivity::class.java)
        intent.putExtra("urls", urls)
        intent.putExtra("tipos", tipos)
        intent.putExtra("posicaoInicial", posicaoInicial)
        startActivity(intent)
    }

    private fun mostrarMenuMidia(
        tipo: String,
        url: String,
        nomeArquivo: String,
        mimeType: String,
        msgId: String,
        ehRemetente: Boolean
    ) {
        val opcoes = mutableListOf<String>()
        opcoes.add("⬇ Baixar")
        if (tipo == "arquivo") opcoes.add("📂 Abrir")
        if (ehRemetente) opcoes.add("🗑 Apagar")

        AlertDialog.Builder(this@ChatActivity)
            .setTitle("Opções")
            .setItems(opcoes.toTypedArray()) { _, which ->
                when (opcoes[which]) {
                    "⬇ Baixar" -> {
                        val nome = when (tipo) {
                            "foto" -> "CTR_foto_${System.currentTimeMillis()}.jpg"
                            "video" -> "CTR_video_${System.currentTimeMillis()}.mp4"
                            else -> nomeArquivo
                        }
                        val pasta = when (tipo) {
                            "foto" -> Environment.DIRECTORY_PICTURES
                            "video" -> Environment.DIRECTORY_MOVIES
                            else -> Environment.DIRECTORY_DOWNLOADS
                        }
                        baixarArquivo(url, nome, pasta)
                    }
                    "📂 Abrir" -> abrirArquivoExterno(url, mimeType, nomeArquivo)
                    "🗑 Apagar" -> apagarMensagem(msgId)
                }
            }
            .show()
    }

    private fun abrirArquivoExterno(url: String, mimeType: String, nome: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(url), mimeType.ifEmpty { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Nenhum app para abrir '$nome'", Toast.LENGTH_LONG).show()
        }
    }

    private fun baixarArquivo(url: String, nomeArquivo: String, pastaDestino: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
            request.setTitle(nomeArquivo)
            request.setDescription("Baixando do CTR...")
            request.allowScanningByMediaScanner()
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(pastaDestino, nomeArquivo)

            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            Toast.makeText(this, "⬇ Baixando...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao baixar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun mostrarOpcaoApagar(msgId: String) {
        AlertDialog.Builder(this@ChatActivity)
            .setTitle("Apagar mensagem")
            .setItems(arrayOf("Apagar para todos")) { _, _ ->
                apagarMensagem(msgId)
            }
            .show()
    }

    private fun apagarMensagem(msgId: String) {
        lifecycleScope.launch {
            try {
                db.collection("chats").document(chatId)
                    .collection("mensagens").document(msgId)
                    .delete().await()
                Toast.makeText(this@ChatActivity, "Mensagem apagada", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
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