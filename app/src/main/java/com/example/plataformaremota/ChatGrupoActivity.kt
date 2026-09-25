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
import android.widget.VideoView
import android.widget.MediaController
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
    private var equipeId: String = ""

    // ========== LAUNCHERS ==========
    private val selecionarImagem = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageUri = result.data?.data
            if (imageUri != null) enviarFoto(imageUri)
        }
    }

    private val selecionarVideo = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val videoUri = result.data?.data
            if (videoUri != null) enviarVideo(videoUri)
        }
    }

    private val selecionarArquivo = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val arquivoUri = result.data?.data
            if (arquivoUri != null) enviarArquivo(arquivoUri)
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
        btnSairGrupo.setOnClickListener { confirmarSairGrupo() }

        val btnGerenciarMembros = findViewById<Button>(R.id.btnGerenciarMembros)
        btnGerenciarMembros.setOnClickListener {
            val intent = Intent(this, GerenciarMembrosGrupoActivity::class.java)
            intent.putExtra("grupoId", grupoId)
            intent.putExtra("equipeId", equipeId)
            startActivity(intent)
        }

        val btnVoltar = findViewById<Button>(R.id.btnVoltarChatGrupo)
        val btnEnviar = findViewById<Button>(R.id.btnEnviarMensagemGrupo)
        val btnFoto = findViewById<Button>(R.id.btnEnviarFotoGrupo)
        val btnVideo = findViewById<Button>(R.id.btnEnviarVideoGrupo)
        val btnArquivo = findViewById<Button>(R.id.btnEnviarArquivoGrupo)
        val edtMensagem = findViewById<EditText>(R.id.edtMensagemGrupo)
        val txtNomeGrupo = findViewById<TextView>(R.id.txtNomeGrupoChat)

        txtNomeGrupo.text = nomeGrupo

        btnVoltar.setOnClickListener { finish() }

        lifecycleScope.launch {
            try {
                val grupoDoc = db.collection("grupos").document(grupoId).get().await()
                equipeId = grupoDoc.getString("equipeId") ?: ""

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

    override fun onResume() {
        super.onResume()
        marcarMensagensComoLidas()
    }

    // ============================================================
    // ✅ Marca como lidas com WriteBatch
    // ============================================================
    private fun marcarMensagensComoLidas() {
        if (grupoId.isEmpty()) return

        lifecycleScope.launch {
            try {
                val mensagens = db.collection("grupos").document(grupoId)
                    .collection("mensagens")
                    .whereEqualTo("lida", false)
                    .get()
                    .await()

                if (mensagens.isEmpty) return@launch

                val batch = db.batch()
                mensagens.documents.forEach { doc ->
                    val remetente = doc.getString("remetente") ?: ""
                    if (remetente != emailUsuario) {
                        batch.update(doc.reference, "lida", true)
                    }
                }
                batch.commit().await()

            } catch (e: Exception) {
                Log.e("CHAT_GRUPO", "Erro ao marcar como lidas: ${e.message}")
            }
        }
    }

    // ============================================================
    // SAIR DO GRUPO
    // ============================================================
    private fun confirmarSairGrupo() {
        lifecycleScope.launch {
            try {
                val grupoDoc = db.collection("grupos").document(grupoId).get().await()
                val criadorEmail = grupoDoc.getString("criadorEmail") ?: ""

                if (criadorEmail == emailUsuario) {
                    Toast.makeText(this@ChatGrupoActivity, "Você é o criador do grupo. Só pode excluí-lo.", Toast.LENGTH_LONG).show()
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

                                // ✅ Remove o grupoId da lista do usuário
                                removerGrupoIdDoUsuario(emailUsuario, grupoId)

                                Toast.makeText(this@ChatGrupoActivity, "Você saiu do grupo", Toast.LENGTH_SHORT).show()
                                finish()
                            } catch (e: Exception) {
                                Toast.makeText(this@ChatGrupoActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    .setNegativeButton(R.string.cancelar, null)
                    .show()

            } catch (e: Exception) {
                Toast.makeText(this@ChatGrupoActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ============================================================
    // ENVIAR TEXTO
    // ============================================================
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
                Toast.makeText(
                    this@ChatGrupoActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ============================================================
    // ENVIAR FOTO
    // ============================================================
    private fun enviarFoto(uri: Uri) {
        Toast.makeText(this, "📤 Enviando foto...", Toast.LENGTH_SHORT).show()

        MediaManager.get().upload(uri)
            .unsigned("fqb729sb")
            .option("folder", "chats_grupo/")
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
                    }
                }
                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    runOnUiThread {
                        Toast.makeText(this@ChatGrupoActivity, "Erro: ${error?.description}", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            })
            .dispatch()
    }

    // ============================================================
    // ENVIAR VÍDEO
    // ============================================================
    private fun enviarVideo(uri: Uri) {
        Toast.makeText(this, "📤 Enviando vídeo...", Toast.LENGTH_SHORT).show()

        MediaManager.get().upload(uri)
            .unsigned("fqb729sb")
            .option("resource_type", "video")
            .option("folder", "chats_grupo_videos/")
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
                                        "timestamp" to System.currentTimeMillis(),
                                        "lida" to false
                                    )
                                    db.collection("grupos").document(grupoId)
                                        .collection("mensagens").add(mensagem).await()
                                    Toast.makeText(this@ChatGrupoActivity, "✅ Vídeo enviado!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(this@ChatGrupoActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }
                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    runOnUiThread {
                        Toast.makeText(this@ChatGrupoActivity, "Erro: ${error?.description}", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            })
            .dispatch()
    }

    // ============================================================
    // ENVIAR ARQUIVO
    // ============================================================
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
        } catch (_: Exception) { }

        val nomeFinal = nomeArquivo
        val tamanhoFinal = tamanhoArquivo
        val mimeFinal = mimeType

        MediaManager.get().upload(uri)
            .unsigned("fqb729sb")
            .option("resource_type", "raw")
            .option("folder", "chats_grupo_arquivos/")
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
                                        "timestamp" to System.currentTimeMillis(),
                                        "lida" to false
                                    )
                                    db.collection("grupos").document(grupoId)
                                        .collection("mensagens").add(mensagem).await()
                                    Toast.makeText(this@ChatGrupoActivity, "✅ Arquivo enviado!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(this@ChatGrupoActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }
                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    runOnUiThread {
                        Toast.makeText(this@ChatGrupoActivity, "Erro: ${error?.description}", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            })
            .dispatch()
    }

    // ============================================================
    // CARREGAR MENSAGENS
    // ============================================================
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
                    val msgId = doc.id
                    val remetente = doc.getString("remetente") ?: ""
                    val nomeRemetente = doc.getString("nomeRemetente") ?: "Usuário"
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
                        // ========== FOTO ==========
                        "foto" -> {
                            val view = inflater.inflate(R.layout.item_mensagem_foto, container, false)
                            val img = view.findViewById<ImageView>(R.id.imgMensagemFoto)
                            val txtNome = view.findViewById<TextView>(R.id.txtNomeFoto)

                            txtNome.text = if (ehRemetente) "Você" else nomeRemetente
                            Glide.with(this).load(fotoUrl).into(img)

                            view.setOnClickListener {
                                val intent = Intent(this, VisualizarMidiaActivity::class.java)
                                intent.putExtra("tipo", "foto")
                                intent.putExtra("url", fotoUrl)
                                startActivity(intent)
                            }

                            // ✅ Long press → Favoritar
                            view.setOnLongClickListener {
                                mostrarMenuMidia("foto", fotoUrl, "", mimeType, msgId, ehRemetente, texto, remetente)
                                true
                            }

                            container.addView(view)
                        }

                        // ========== VÍDEO ==========
                        "video" -> {
                            val view = inflater.inflate(R.layout.item_mensagem_video, container, false)
                            val videoView = view.findViewById<VideoView>(R.id.videoMensagem)
                            val btnPlay = view.findViewById<Button>(R.id.btnPlayVideo)
                            val txtNome = view.findViewById<TextView>(R.id.txtNomeVideo)

                            txtNome.text = if (ehRemetente) "Você" else nomeRemetente

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

                            videoView.setOnClickListener {
                                val intent = Intent(this, VisualizarMidiaActivity::class.java)
                                intent.putExtra("tipo", "video")
                                intent.putExtra("url", videoUrl)
                                startActivity(intent)
                            }

                            // ✅ Long press → Favoritar
                            view.setOnLongClickListener {
                                mostrarMenuMidia("video", videoUrl, "", "", msgId, ehRemetente, texto, remetente)
                                true
                            }

                            container.addView(view)
                        }

                        // ========== ARQUIVO ==========
                        "arquivo" -> {
                            val view = inflater.inflate(R.layout.item_mensagem_arquivo, container, false)
                            val txtNomeRem = view.findViewById<TextView>(R.id.txtNomeArquivoRemetente)
                            val txtNomeArq = view.findViewById<TextView>(R.id.txtNomeArquivo)
                            val txtTamanho = view.findViewById<TextView>(R.id.txtTamanhoArquivo)

                            txtNomeRem.text = if (ehRemetente) "Você" else nomeRemetente
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

                            // ✅ Long press → Favoritar
                            view.setOnLongClickListener {
                                mostrarMenuMidia("arquivo", arquivoUrl, nomeArquivo, mimeType, msgId, ehRemetente, texto, remetente)
                                true
                            }

                            container.addView(view)
                        }

                        // ========== TEXTO ==========
                        else -> {
                            val view = inflater.inflate(android.R.layout.simple_list_item_1, container, false)
                            val tv = view.findViewById<TextView>(android.R.id.text1)

                            if (ehRemetente) {
                                tv.text = "Você: $texto"
                                tv.setTextColor(android.graphics.Color.parseColor("#F5E6D0"))
                            } else {
                                tv.text = "$nomeRemetente: $texto"
                                tv.setTextColor(android.graphics.Color.WHITE)
                            }
                            tv.textSize = 16f
                            view.setPadding(0, 12, 0, 12)

                            // ✅ Long press → Favoritar (todos) ou Apagar (só remetente)
                            view.setOnLongClickListener {
                                mostrarOpcaoMensagem(msgId, texto, remetente, ehRemetente)
                                true
                            }

                            container.addView(view)
                        }
                    }
                }

                scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
            }
    }

    // ============================================================
    // MENU DE MENSAGEM DE TEXTO
    // ============================================================
    private fun mostrarOpcaoMensagem(
        msgId: String,
        texto: String,
        remetente: String,
        ehRemetente: Boolean
    ) {
        lifecycleScope.launch {
            try {
                val jaFavorito = db.collection("favoritos")
                    .whereEqualTo("usuarioEmail", emailUsuario)
                    .whereEqualTo("mensagemId", msgId)
                    .limit(1)
                    .get().await()
                    .let { !it.isEmpty }

                val opcoes = mutableListOf<String>()
                opcoes.add(if (jaFavorito) getString(R.string.desfavoritar) else getString(R.string.favoritar))
                if (ehRemetente) opcoes.add("🗑 Apagar para todos")

                AlertDialog.Builder(this@ChatGrupoActivity)
                    .setTitle(R.string.opcoes)
                    .setItems(opcoes.toTypedArray()) { _, which ->
                        when (opcoes[which]) {
                            getString(R.string.favoritar) -> favoritarMensagem(msgId, texto, remetente, "texto")
                            getString(R.string.desfavoritar) -> desfavoritarMensagem(msgId)
                            "🗑 Apagar para todos" -> apagarMensagem(msgId)
                        }
                    }
                    .show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ChatGrupoActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ============================================================
    // MENU DE MÍDIA
    // ============================================================
    private fun mostrarMenuMidia(
        tipo: String,
        url: String,
        nomeArquivo: String,
        mimeType: String,
        msgId: String,
        ehRemetente: Boolean,
        texto: String,
        remetente: String
    ) {
        lifecycleScope.launch {
            try {
                val jaFavorito = db.collection("favoritos")
                    .whereEqualTo("usuarioEmail", emailUsuario)
                    .whereEqualTo("mensagemId", msgId)
                    .limit(1)
                    .get().await()
                    .let { !it.isEmpty }

                val opcoes = mutableListOf<String>()
                opcoes.add(if (jaFavorito) getString(R.string.desfavoritar) else getString(R.string.favoritar))
                opcoes.add(getString(R.string.baixar))
                if (tipo == "arquivo") opcoes.add(getString(R.string.abrir))
                if (ehRemetente) opcoes.add("🗑 Apagar")

                AlertDialog.Builder(this@ChatGrupoActivity)
                    .setTitle(R.string.opcoes)
                    .setItems(opcoes.toTypedArray()) { _, which ->
                        when (opcoes[which]) {
                            getString(R.string.favoritar) -> favoritarMensagem(msgId, texto, remetente, tipo)
                            getString(R.string.desfavoritar) -> desfavoritarMensagem(msgId)
                            getString(R.string.baixar) -> Toast.makeText(this@ChatGrupoActivity, "⬇ Baixando...", Toast.LENGTH_SHORT).show()
                            getString(R.string.abrir) -> {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(url), mimeType.ifEmpty { "*/*" })
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(this@ChatGrupoActivity, "Nenhum app para abrir", Toast.LENGTH_LONG).show()
                                }
                            }
                            "🗑 Apagar" -> apagarMensagem(msgId)
                        }
                    }
                    .show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ChatGrupoActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ============================================================
    // FAVORITAR / DESFAVORITAR
    // ============================================================
    private fun favoritarMensagem(msgId: String, texto: String, remetente: String, tipoMidia: String) {
        lifecycleScope.launch {
            try {
                val nomeRemetente = if (remetente == emailUsuario) "Você" else {
                    val u = db.collection("usuarios").document(remetente).get().await()
                    u.getString("nome") ?: remetente
                }

                db.collection("favoritos").add(
                    hashMapOf(
                        "usuarioEmail" to emailUsuario,
                        "mensagemId" to msgId,
                        "chatId" to grupoId,
                        "tipoChat" to "grupo",
                        "texto" to texto,
                        "remetente" to remetente,
                        "nomeRemetente" to nomeRemetente,
                        "tipoMidia" to tipoMidia,
                        "criadoEm" to System.currentTimeMillis()
                    )
                ).await()

                Toast.makeText(this@ChatGrupoActivity, "⭐ Adicionado aos favoritos", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ChatGrupoActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun desfavoritarMensagem(msgId: String) {
        lifecycleScope.launch {
            try {
                val favoritos = db.collection("favoritos")
                    .whereEqualTo("usuarioEmail", emailUsuario)
                    .whereEqualTo("mensagemId", msgId)
                    .get().await()

                favoritos.documents.forEach { it.reference.delete().await() }

                Toast.makeText(this@ChatGrupoActivity, "Removido dos favoritos", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ChatGrupoActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ============================================================
    // APAGAR / HELPERS
    // ============================================================
    private fun apagarMensagem(msgId: String) {
        lifecycleScope.launch {
            try {
                db.collection("grupos").document(grupoId)
                    .collection("mensagens").document(msgId)
                    .delete().await()
                Toast.makeText(this@ChatGrupoActivity, "Mensagem apagada", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@ChatGrupoActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun removerGrupoIdDoUsuario(email: String, grupoId: String) {
        try {
            val userRef = db.collection("usuarios").document(email)
            val userDoc = userRef.get().await()
            val ids = (userDoc.get("gruposIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

            if (grupoId in ids) {
                userRef.update("gruposIds", ids - grupoId).await()
            }
        } catch (e: Exception) {
            Log.e("CHAT_GRUPO", "Erro ao remover grupoId: ${e.message}")
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