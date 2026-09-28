package com.example.plataformaremota

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

    private var respostaAtiva: RespostaInfo? = null
    private var typingHelper: TypingIndicatorHelper? = null

    private val selecionarImagem = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) { r.data?.data?.let { enviarFoto(it) } }
    }
    private val selecionarVideo = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) { r.data?.data?.let { enviarVideo(it) } }
    }
    private val selecionarArquivo = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) { r.data?.data?.let { enviarArquivo(it) } }
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
            Toast.makeText(this, "Grupo não encontrado", Toast.LENGTH_SHORT).show(); finish(); return
        }

        findViewById<Button>(R.id.btnSairGrupo).setOnClickListener { confirmarSairGrupo() }
        findViewById<Button>(R.id.btnGerenciarMembros).setOnClickListener {
            val i = Intent(this, GerenciarMembrosGrupoActivity::class.java)
            i.putExtra("grupoId", grupoId); i.putExtra("equipeId", equipeId); startActivity(i)
        }

        val btnVoltar = findViewById<Button>(R.id.btnVoltarChatGrupo)
        val btnEnviar = findViewById<Button>(R.id.btnEnviarMensagemGrupo)
        val btnFoto = findViewById<Button>(R.id.btnEnviarFotoGrupo)
        val btnVideo = findViewById<Button>(R.id.btnEnviarVideoGrupo)
        val btnArquivo = findViewById<Button>(R.id.btnEnviarArquivoGrupo)
        val edtMensagem = findViewById<EditText>(R.id.edtMensagemGrupo)
        val txtNomeGrupo = findViewById<TextView>(R.id.txtNomeGrupoChat)
        val txtDigitando = findViewById<TextView>(R.id.txtDigitandoGrupo)
        val btnCancelarResposta = findViewById<Button>(R.id.btnCancelarRespostaGrupo)

        txtNomeGrupo.text = nomeGrupo
        btnVoltar.setOnClickListener { finish() }

        lifecycleScope.launch {
            try {
                equipeId = db.collection("grupos").document(grupoId).get().await().getString("equipeId") ?: ""
                nomeUsuario = db.collection("usuarios").document(emailUsuario).get().await().getString("nome") ?: "Usuário"
            } catch (e: Exception) { Log.e("CHAT_GRUPO", "Erro: ${e.message}") }
        }

        btnEnviar.setOnClickListener {
            val t = edtMensagem.text.toString().trim()
            if (t.isEmpty()) return@setOnClickListener
            edtMensagem.text.clear(); enviarMensagem(t)
        }
        btnFoto.setOnClickListener {
            val i = Intent(Intent.ACTION_PICK); i.type = "image/*"; selecionarImagem.launch(i)
        }
        btnVideo.setOnClickListener {
            val i = Intent(Intent.ACTION_PICK); i.type = "video/*"; selecionarVideo.launch(i)
        }
        btnArquivo.setOnClickListener {
            val i = Intent(Intent.ACTION_GET_CONTENT); i.type = "*/*"
            i.addCategory(Intent.CATEGORY_OPENABLE); selecionarArquivo.launch(i)
        }
        btnCancelarResposta.setOnClickListener { cancelarResposta() }

        lifecycleScope.launch {
            garantirCampoDigitando()
            typingHelper = TypingIndicatorHelper(
                collection = "grupos",
                documentId = grupoId,
                emailUsuario = emailUsuario,
                onStatusChanged = { nome, estaDigitando ->
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        if (estaDigitando) {
                            txtDigitando.text = "$nome está digitando..."
                            txtDigitando.visibility = View.VISIBLE
                        } else {
                            txtDigitando.visibility = View.GONE
                        }
                    }
                }
            )
            typingHelper?.iniciar()
        }

        edtMensagem.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                typingHelper?.onDigitou()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        carregarMensagens()
    }

    override fun onResume() { super.onResume(); marcarMensagensComoLidas() }

    override fun onPause() { super.onPause(); typingHelper?.limpar() }

    override fun onDestroy() {
        super.onDestroy()
        typingHelper?.destruir()
        typingHelper = null
    }

    private suspend fun garantirCampoDigitando() {
        try {
            val docRef = db.collection("grupos").document(grupoId)
            val doc = docRef.get().await()
            if (doc.exists() && !doc.contains("digitando")) {
                docRef.update("digitando", emptyMap<String, Long>()).await()
            }
        } catch (e: Exception) { Log.e("CHAT_GRUPO", "Erro garantir digitando: ${e.message}") }
    }

    private fun marcarMensagensComoLidas() {
        if (grupoId.isEmpty()) return
        lifecycleScope.launch {
            try {
                val msgs = db.collection("grupos").document(grupoId).collection("mensagens")
                    .whereEqualTo("lida", false).get().await()
                if (msgs.isEmpty) return@launch
                val batch = db.batch()
                msgs.documents.forEach { doc ->
                    if ((doc.getString("remetente") ?: "") != emailUsuario) batch.update(doc.reference, "lida", true)
                }
                batch.commit().await()
            } catch (e: Exception) { Log.e("CHAT_GRUPO", "Erro: ${e.message}") }
        }
    }

    private fun cancelarResposta() {
        respostaAtiva = null
        findViewById<LinearLayout>(R.id.containerRespondendoGrupo).visibility = View.GONE
    }

    private fun adicionarResposta(m: HashMap<String, Any>) {
        respostaAtiva?.let { r ->
            m["respostaPara"] = hashMapOf(
                "msgId" to r.msgId, "texto" to r.texto, "remetente" to r.remetente,
                "nomeRemetente" to r.nomeRemetente, "tipo" to r.tipo
            )
        }
    }

    private fun dispararResposta(msgId: String, texto: String, remetente: String, nomeRem: String, tipo: String = "texto") {
        respostaAtiva = RespostaInfo(msgId, texto, remetente, nomeRem, tipo)
        findViewById<TextView>(R.id.txtRespondendoAGrupo).text = "Respondendo a $nomeRem"
        findViewById<TextView>(R.id.txtTextoRespondendoGrupo).text = when (tipo) {
            "foto" -> "📷 Foto"; "video" -> "🎥 Vídeo"; "arquivo" -> "📎 Arquivo"; else -> texto
        }
        findViewById<LinearLayout>(R.id.containerRespondendoGrupo).visibility = View.VISIBLE
        findViewById<EditText>(R.id.edtMensagemGrupo).requestFocus()
    }

    private fun confirmarSairGrupo() {
        lifecycleScope.launch {
            try {
                val gd = db.collection("grupos").document(grupoId).get().await()
                if (gd.getString("criadorEmail") == emailUsuario) {
                    Toast.makeText(this@ChatGrupoActivity, "Você é o criador. Só pode excluir.", Toast.LENGTH_LONG).show()
                    return@launch
                }
                AlertDialog.Builder(this@ChatGrupoActivity)
                    .setTitle("Sair do grupo").setMessage("Tem certeza?")
                    .setPositiveButton("Sair") { _, _ ->
                        lifecycleScope.launch {
                            try {
                                val membros = gd.get("membros") as? List<*> ?: emptyList<Any>()
                                db.collection("grupos").document(grupoId)
                                    .update("membros", membros.filter { it != emailUsuario }).await()
                                removerGrupoIdDoUsuario(emailUsuario, grupoId)
                                Toast.makeText(this@ChatGrupoActivity, "Você saiu", Toast.LENGTH_SHORT).show(); finish()
                            } catch (e: Exception) { Toast.makeText(this@ChatGrupoActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
                        }
                    }.setNegativeButton(R.string.cancelar, null).show()
            } catch (e: Exception) { Toast.makeText(this@ChatGrupoActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun enviarMensagem(texto: String) {
        lifecycleScope.launch {
            try {
                val m = hashMapOf<String, Any>(
                    "remetente" to emailUsuario, "nomeRemetente" to nomeUsuario,
                    "texto" to texto, "tipo" to "texto",
                    "timestamp" to System.currentTimeMillis(), "lida" to false
                )
                adicionarResposta(m)
                db.collection("grupos").document(grupoId).collection("mensagens").add(m).await()
                typingHelper?.limpar()
                cancelarResposta()
            } catch (e: Exception) { Toast.makeText(this@ChatGrupoActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun enviarFoto(uri: Uri) {
        Toast.makeText(this, "📤 Enviando foto...", Toast.LENGTH_SHORT).show()
        MediaManager.get().upload(uri).unsigned("fqb729sb").option("folder", "chats_grupo/")
            .callback(object : com.cloudinary.android.callback.UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val url = resultData?.get("secure_url") as? String
                    if (url != null) runOnUiThread { lifecycleScope.launch {
                        try {
                            val m = hashMapOf<String, Any>(
                                "remetente" to emailUsuario, "nomeRemetente" to nomeUsuario,
                                "texto" to "", "fotoUrl" to url, "tipo" to "foto",
                                "timestamp" to System.currentTimeMillis(), "lida" to false
                            )
                            adicionarResposta(m)
                            db.collection("grupos").document(grupoId).collection("mensagens").add(m).await()
                            Toast.makeText(this@ChatGrupoActivity, "✅ Foto enviada!", Toast.LENGTH_SHORT).show()
                            typingHelper?.limpar(); cancelarResposta()
                        } catch (e: Exception) { Toast.makeText(this@ChatGrupoActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
                    } }
                }
                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    runOnUiThread { Toast.makeText(this@ChatGrupoActivity, "Erro: ${error?.description}", Toast.LENGTH_LONG).show() }
                }
                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            }).dispatch()
    }

    private fun enviarVideo(uri: Uri) {
        Toast.makeText(this, "📤 Enviando vídeo...", Toast.LENGTH_SHORT).show()
        MediaManager.get().upload(uri).unsigned("fqb729sb")
            .option("resource_type", "video").option("folder", "chats_grupo_videos/")
            .callback(object : com.cloudinary.android.callback.UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val url = resultData?.get("secure_url") as? String
                    if (url != null) runOnUiThread { lifecycleScope.launch {
                        try {
                            val m = hashMapOf<String, Any>(
                                "remetente" to emailUsuario, "nomeRemetente" to nomeUsuario,
                                "texto" to "", "videoUrl" to url, "tipo" to "video",
                                "timestamp" to System.currentTimeMillis(), "lida" to false
                            )
                            adicionarResposta(m)
                            db.collection("grupos").document(grupoId).collection("mensagens").add(m).await()
                            Toast.makeText(this@ChatGrupoActivity, "✅ Vídeo enviado!", Toast.LENGTH_SHORT).show()
                            typingHelper?.limpar(); cancelarResposta()
                        } catch (e: Exception) { Toast.makeText(this@ChatGrupoActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
                    } }
                }
                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    runOnUiThread { Toast.makeText(this@ChatGrupoActivity, "Erro: ${error?.description}", Toast.LENGTH_LONG).show() }
                }
                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            }).dispatch()
    }

    private fun enviarArquivo(uri: Uri) {
        Toast.makeText(this, "📤 Enviando arquivo...", Toast.LENGTH_SHORT).show()
        var nome = "arquivo"; var tam = 0L; var mime = "application/octet-stream"
        try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val s = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (n != -1) nome = c.getString(n) ?: "arquivo"
                    if (s != -1) tam = c.getLong(s)
                }
            }
            mime = contentResolver.getType(uri) ?: "application/octet-stream"
        } catch (_: Exception) { }

        val nF = nome; val tF = tam; val mF = mime
        MediaManager.get().upload(uri).unsigned("fqb729sb")
            .option("resource_type", "raw").option("folder", "chats_grupo_arquivos/")
            .callback(object : com.cloudinary.android.callback.UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val url = resultData?.get("secure_url") as? String
                    if (url != null) runOnUiThread { lifecycleScope.launch {
                        try {
                            val m = hashMapOf<String, Any>(
                                "remetente" to emailUsuario, "nomeRemetente" to nomeUsuario,
                                "texto" to "", "arquivoUrl" to url, "nomeArquivo" to nF,
                                "tamanhoArquivo" to tF, "mimeType" to mF,
                                "tipo" to "arquivo", "timestamp" to System.currentTimeMillis(), "lida" to false
                            )
                            adicionarResposta(m)
                            db.collection("grupos").document(grupoId).collection("mensagens").add(m).await()
                            Toast.makeText(this@ChatGrupoActivity, "✅ Arquivo enviado!", Toast.LENGTH_SHORT).show()
                            typingHelper?.limpar(); cancelarResposta()
                        } catch (e: Exception) { Toast.makeText(this@ChatGrupoActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
                    } }
                }
                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    runOnUiThread { Toast.makeText(this@ChatGrupoActivity, "Erro: ${error?.description}", Toast.LENGTH_LONG).show() }
                }
                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            }).dispatch()
    }

    private fun carregarMensagens() {
        if (isFinishing || isDestroyed) return

        val container = findViewById<LinearLayout>(R.id.containerMensagensGrupo)
        val scroll = findViewById<ScrollView>(R.id.scrollMensagensGrupo)

        db.collection("grupos").document(grupoId).collection("mensagens")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (isFinishing || isDestroyed) return@addSnapshotListener
                if (error != null || snapshots == null) return@addSnapshotListener

                container.removeAllViews()
                val inflater = LayoutInflater.from(this)

                snapshots.documents.forEach { doc ->
                    if (isFinishing || isDestroyed) return@forEach

                    val msgId = doc.id
                    val remetente = doc.getString("remetente") ?: ""
                    val nomeRem = doc.getString("nomeRemetente") ?: "Usuário"
                    val texto = doc.getString("texto") ?: ""
                    val tipo = doc.getString("tipo") ?: "texto"
                    val fotoUrl = doc.getString("fotoUrl") ?: ""
                    val videoUrl = doc.getString("videoUrl") ?: ""
                    val arquivoUrl = doc.getString("arquivoUrl") ?: ""
                    val nomeArq = doc.getString("nomeArquivo") ?: "arquivo"
                    val tamArq = doc.getLong("tamanhoArquivo") ?: 0L
                    val mime = doc.getString("mimeType") ?: ""
                    val respostaPara = doc.get("respostaPara") as? Map<*, *>
                    val ehRem = remetente == emailUsuario

                    when (tipo) {
                        "texto" -> {
                            val view = inflater.inflate(R.layout.item_mensagem_texto, container, false)
                            renderizarTexto(view, msgId, texto, remetente, nomeRem, respostaPara, ehRem)
                            container.addView(view)
                        }
                        "foto" -> {
                            val view = inflater.inflate(R.layout.item_mensagem_foto, container, false)
                            val img = view.findViewById<ImageView>(R.id.imgMensagemFoto)
                            val txtNome = view.findViewById<TextView>(R.id.txtNomeFoto)
                            val containerBalao = view.findViewById<LinearLayout>(R.id.containerBalao)
                            val imgInd = view.findViewById<ImageView>(R.id.imgIndicadorResposta)

                            txtNome.text = if (ehRem) "Você" else nomeRem

                            if (!isFinishing && !isDestroyed) {
                                Glide.with(applicationContext).load(fotoUrl).into(img)
                            }

                            aplicarAlinhamentoRelative(containerBalao, ehRem)

                            SwipeToReplyHelper.attach(containerBalao, imgInd) { dispararResposta(msgId, "", remetente, nomeRem, "foto") }
                            containerBalao.setOnClickListener {
                                val i = Intent(this, VisualizarMidiaActivity::class.java)
                                i.putExtra("tipo", "foto"); i.putExtra("url", fotoUrl); startActivity(i)
                            }
                            containerBalao.setOnLongClickListener {
                                mostrarMenuMidia("foto", fotoUrl, "", mime, msgId, ehRem, texto, remetente, nomeRem); true
                            }
                            container.addView(view)
                        }
                        "video" -> {
                            val view = inflater.inflate(R.layout.item_mensagem_video, container, false)
                            val videoView = view.findViewById<VideoView>(R.id.videoMensagem)
                            val btnPlay = view.findViewById<Button>(R.id.btnPlayVideo)
                            val txtNome = view.findViewById<TextView>(R.id.txtNomeVideo)
                            val containerBalao = view.findViewById<LinearLayout>(R.id.containerBalao)
                            val imgInd = view.findViewById<ImageView>(R.id.imgIndicadorResposta)
                            val overlay = view.findViewById<View>(R.id.overlayVideo)

                            txtNome.text = if (ehRem) "Você" else nomeRem
                            aplicarAlinhamentoRelative(containerBalao, ehRem)

                            try {
                                videoView.setVideoURI(Uri.parse(videoUrl))
                            } catch (e: Exception) { Log.e("CHAT_GRUPO", "Erro video: ${e.message}") }

                            videoView.setOnErrorListener { _, _, _ -> btnPlay.text = "⚠"; btnPlay.visibility = View.VISIBLE; true }

                            btnPlay.setOnClickListener {
                                try {
                                    if (videoView.isPlaying) {
                                        videoView.pause(); btnPlay.text = "▶"; btnPlay.visibility = View.VISIBLE
                                    } else {
                                        videoView.start(); btnPlay.visibility = View.GONE
                                    }
                                } catch (e: Exception) { Log.e("CHAT_GRUPO", "Erro play: ${e.message}") }
                            }
                            videoView.setOnCompletionListener { btnPlay.text = "▶"; btnPlay.visibility = View.VISIBLE }

                            SwipeToReplyHelper.attach(
                                viewToTouch = overlay, containerBalao = containerBalao,
                                imgIndicador = imgInd, onResponder = { dispararResposta(msgId, "", remetente, nomeRem, "video") }
                            )
                            overlay.setOnClickListener {
                                val i = Intent(this, VisualizarMidiaActivity::class.java)
                                i.putExtra("tipo", "video"); i.putExtra("url", videoUrl); startActivity(i)
                            }
                            overlay.setOnLongClickListener {
                                mostrarMenuMidia("video", videoUrl, "", "", msgId, ehRem, texto, remetente, nomeRem); true
                            }
                            container.addView(view)
                        }
                        "arquivo" -> {
                            val view = inflater.inflate(R.layout.item_mensagem_arquivo, container, false)
                            val txtNomeRem = view.findViewById<TextView>(R.id.txtNomeArquivoRemetente)
                            val txtNomeArq = view.findViewById<TextView>(R.id.txtNomeArquivo)
                            val txtTam = view.findViewById<TextView>(R.id.txtTamanhoArquivo)
                            val containerBalao = view.findViewById<LinearLayout>(R.id.containerBalao)
                            val imgInd = view.findViewById<ImageView>(R.id.imgIndicadorResposta)

                            txtNomeRem.text = if (ehRem) "Você" else nomeRem
                            txtNomeArq.text = nomeArq
                            txtTam.text = formatarTamanho(tamArq)
                            aplicarAlinhamentoRelative(containerBalao, ehRem)

                            SwipeToReplyHelper.attach(containerBalao, imgInd) { dispararResposta(msgId, "", remetente, nomeRem, "arquivo") }
                            containerBalao.setOnClickListener {
                                try {
                                    val i = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(arquivoUrl), mime.ifEmpty { "*/*" })
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    startActivity(i)
                                } catch (e: Exception) { Toast.makeText(this, "Nenhum app", Toast.LENGTH_LONG).show() }
                            }
                            containerBalao.setOnLongClickListener {
                                mostrarMenuMidia("arquivo", arquivoUrl, nomeArq, mime, msgId, ehRem, texto, remetente, nomeRem); true
                            }
                            container.addView(view)
                        }
                    }
                }
                scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
            }
    }

    private fun aplicarAlinhamentoRelative(containerBalao: LinearLayout, ehRemetente: Boolean) {
        val params = containerBalao.layoutParams as RelativeLayout.LayoutParams
        if (ehRemetente) {
            params.addRule(RelativeLayout.ALIGN_PARENT_END, 1)
            params.removeRule(RelativeLayout.ALIGN_PARENT_START)
        } else {
            params.addRule(RelativeLayout.ALIGN_PARENT_START, 1)
            params.removeRule(RelativeLayout.ALIGN_PARENT_END)
        }
        containerBalao.layoutParams = params
    }

    private fun renderizarTexto(
        view: View, msgId: String, texto: String, remetente: String,
        nomeRem: String, respostaPara: Map<*, *>?, ehRem: Boolean
    ) {
        val containerBalao = view.findViewById<LinearLayout>(R.id.containerBalao)
        val imgInd = view.findViewById<ImageView>(R.id.imgIndicadorResposta)
        val txtNomeRem = view.findViewById<TextView>(R.id.txtNomeRemetenteMensagem)
        val txtTexto = view.findViewById<TextView>(R.id.txtTextoMensagem)
        val containerCit = view.findViewById<LinearLayout>(R.id.containerCitacao)
        val txtNomeCit = view.findViewById<TextView>(R.id.txtNomeCitado)
        val txtTextoCit = view.findViewById<TextView>(R.id.txtTextoCitado)

        txtNomeRem.visibility = View.VISIBLE
        txtNomeRem.text = if (ehRem) "Você" else nomeRem
        aplicarAlinhamentoRelative(containerBalao, ehRem)

        if (ehRem) {
            containerBalao.setBackgroundResource(R.drawable.bg_bolha_enviada)
            txtTexto.setTextColor(ContextCompat.getColor(this, R.color.accent_dark))
            txtNomeRem.setTextColor(ContextCompat.getColor(this, R.color.accent_dark))
        } else {
            containerBalao.setBackgroundResource(R.drawable.bg_bolha_recebida)
            txtTexto.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            txtNomeRem.setTextColor(ContextCompat.getColor(this, R.color.accent))
        }

        txtTexto.text = texto

        if (respostaPara != null) {
            containerCit.visibility = View.VISIBLE
            txtNomeCit.text = respostaPara["nomeRemetente"] as? String ?: ""
            txtTextoCit.text = when (respostaPara["tipo"] as? String ?: "texto") {
                "foto" -> "📷 Foto"; "video" -> "🎥 Vídeo"; "arquivo" -> "📎 Arquivo"
                else -> respostaPara["texto"] as? String ?: ""
            }
        } else {
            containerCit.visibility = View.GONE
        }

        SwipeToReplyHelper.attach(containerBalao, imgInd) { dispararResposta(msgId, texto, remetente, nomeRem, "texto") }
        containerBalao.setOnLongClickListener {
            mostrarOpcaoMensagem(msgId, texto, remetente, nomeRem, ehRem); true
        }
    }

    private fun mostrarOpcaoMensagem(msgId: String, texto: String, remetente: String, nomeRem: String, ehRem: Boolean) {
        lifecycleScope.launch {
            try {
                val jaFav = db.collection("favoritos")
                    .whereEqualTo("usuarioEmail", emailUsuario).whereEqualTo("mensagemId", msgId)
                    .limit(1).get().await().let { !it.isEmpty }
                val opcoes = mutableListOf("💬 Responder")
                opcoes.add(if (jaFav) "⭐ Remover dos favoritos" else "⭐ Favoritar")
                if (ehRem) opcoes.add("🗑 Apagar para todos")

                AlertDialog.Builder(this@ChatGrupoActivity).setTitle("Opções")
                    .setItems(opcoes.toTypedArray()) { _, w ->
                        when (opcoes[w]) {
                            "💬 Responder" -> dispararResposta(msgId, texto, remetente, nomeRem, "texto")
                            "⭐ Favoritar" -> favoritar(msgId, texto, remetente, nomeRem, "texto")
                            "⭐ Remover dos favoritos" -> desfavoritar(msgId)
                            "🗑 Apagar para todos" -> apagarMensagem(msgId)
                        }
                    }.show()
            } catch (e: Exception) { Toast.makeText(this@ChatGrupoActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun mostrarMenuMidia(
        tipo: String, url: String, nomeArq: String, mime: String,
        msgId: String, ehRem: Boolean, texto: String, remetente: String, nomeRem: String
    ) {
        lifecycleScope.launch {
            try {
                val jaFav = db.collection("favoritos")
                    .whereEqualTo("usuarioEmail", emailUsuario).whereEqualTo("mensagemId", msgId)
                    .limit(1).get().await().let { !it.isEmpty }
                val opcoes = mutableListOf("💬 Responder")
                opcoes.add(if (jaFav) "⭐ Remover dos favoritos" else "⭐ Favoritar")
                opcoes.add("⬇ Baixar")
                if (tipo == "arquivo") opcoes.add("📂 Abrir")
                if (ehRem) opcoes.add("🗑 Apagar")

                AlertDialog.Builder(this@ChatGrupoActivity).setTitle("Opções")
                    .setItems(opcoes.toTypedArray()) { _, w ->
                        when (opcoes[w]) {
                            "💬 Responder" -> dispararResposta(msgId, texto, remetente, nomeRem, tipo)
                            "⭐ Favoritar" -> favoritar(msgId, texto, remetente, nomeRem, tipo)
                            "⭐ Remover dos favoritos" -> desfavoritar(msgId)
                            "⬇ Baixar" -> Toast.makeText(this@ChatGrupoActivity, "⬇ Baixando...", Toast.LENGTH_SHORT).show()
                            "📂 Abrir" -> {
                                try {
                                    val i = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(url), mime.ifEmpty { "*/*" })
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    startActivity(i)
                                } catch (e: Exception) { Toast.makeText(this@ChatGrupoActivity, "Nenhum app", Toast.LENGTH_LONG).show() }
                            }
                            "🗑 Apagar" -> apagarMensagem(msgId)
                        }
                    }.show()
            } catch (e: Exception) { Toast.makeText(this@ChatGrupoActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun favoritar(msgId: String, texto: String, remetente: String, nomeRem: String, tipoMidia: String) {
        lifecycleScope.launch {
            try {
                db.collection("favoritos").add(hashMapOf(
                    "usuarioEmail" to emailUsuario, "mensagemId" to msgId, "chatId" to grupoId,
                    "tipoChat" to "grupo", "texto" to texto, "remetente" to remetente,
                    "nomeRemetente" to nomeRem, "tipoMidia" to tipoMidia,
                    "criadoEm" to System.currentTimeMillis()
                )).await()
                Toast.makeText(this@ChatGrupoActivity, "⭐ Favoritado", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(this@ChatGrupoActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun desfavoritar(msgId: String) {
        lifecycleScope.launch {
            try {
                db.collection("favoritos").whereEqualTo("usuarioEmail", emailUsuario)
                    .whereEqualTo("mensagemId", msgId).get().await()
                    .documents.forEach { it.reference.delete().await() }
                Toast.makeText(this@ChatGrupoActivity, "Removido", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(this@ChatGrupoActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun apagarMensagem(msgId: String) {
        lifecycleScope.launch {
            try {
                db.collection("grupos").document(grupoId).collection("mensagens").document(msgId).delete().await()
                Toast.makeText(this@ChatGrupoActivity, "Apagada", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(this@ChatGrupoActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private suspend fun removerGrupoIdDoUsuario(email: String, gid: String) {
        try {
            val ref = db.collection("usuarios").document(email)
            val doc = ref.get().await()
            val ids = (doc.get("gruposIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            if (gid in ids) ref.update("gruposIds", ids - gid).await()
        } catch (e: Exception) { Log.e("CHAT_GRUPO", "Erro: ${e.message}") }
    }

    private fun formatarTamanho(b: Long): String = when {
        b < 1024 -> "$b B"
        b < 1024 * 1024 -> "${b / 1024} KB"
        b < 1024 * 1024 * 1024 -> String.format("%.1f MB", b / (1024.0 * 1024.0))
        else -> String.format("%.1f GB", b / (1024.0 * 1024.0 * 1024.0))
    }

    data class RespostaInfo(
        val msgId: String, val texto: String, val remetente: String,
        val nomeRemetente: String, val tipo: String
    )
}