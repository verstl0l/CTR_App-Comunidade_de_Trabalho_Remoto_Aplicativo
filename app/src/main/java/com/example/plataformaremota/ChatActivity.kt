package com.example.plataformaremota

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
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

class ChatActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""
    private var outroEmail: String = ""
    private var chatId: String = ""

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
        setContentView(R.layout.activity_chat)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""
        outroEmail = intent.getStringExtra("outroEmail") ?: ""
        chatId = intent.getStringExtra("chatId") ?: ""

        if (chatId.isEmpty()) {
            lifecycleScope.launch {
                chatId = criarOuBuscarChat()
                if (!isFinishing && !isDestroyed) configurarUI()
            }
        } else {
            configurarUI()
        }
    }

    override fun onResume() {
        super.onResume()
        marcarMensagensComoLidas()
    }

    override fun onPause() {
        super.onPause()
        typingHelper?.limpar()
    }

    override fun onDestroy() {
        super.onDestroy()
        typingHelper?.destruir()
        typingHelper = null
    }

    private fun marcarMensagensComoLidas() {
        if (chatId.isEmpty()) return
        lifecycleScope.launch {
            try {
                val msgs = db.collection("chats").document(chatId).collection("mensagens")
                    .whereEqualTo("lida", false).get().await()
                if (msgs.isEmpty) return@launch
                val batch = db.batch()
                msgs.documents.forEach { doc ->
                    if ((doc.getString("remetente") ?: "") != emailUsuario) batch.update(doc.reference, "lida", true)
                }
                batch.commit().await()
            } catch (_: Exception) { }
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
        val txtDigitando = findViewById<TextView>(R.id.txtDigitando)
        val btnCancelarResposta = findViewById<Button>(R.id.btnCancelarResposta)

        btnVoltar.setOnClickListener { finish() }

        btnVerPerfil.setOnClickListener {
            val i = Intent(this, PerfilUsuarioActivity::class.java)
            i.putExtra("emailOutro", outroEmail)
            startActivity(i)
        }

        lifecycleScope.launch {
            try {
                val u = db.collection("usuarios").document(outroEmail).get().await()
                txtNomeOutro.text = u.getString("nome") ?: outroEmail
            } catch (e: Exception) { txtNomeOutro.text = outroEmail }
        }

        verificarBloqueio(edtMensagem, btnEnviar)

        btnEnviar.setOnClickListener {
            val texto = edtMensagem.text.toString().trim()
            if (texto.isEmpty()) return@setOnClickListener
            edtMensagem.text.clear()
            enviarMensagem(texto)
        }
        btnFoto.setOnClickListener {
            val i = Intent(Intent.ACTION_PICK); i.type = "image/*"
            selecionarImagem.launch(i)
        }
        btnVideo.setOnClickListener {
            val i = Intent(Intent.ACTION_PICK); i.type = "video/*"
            selecionarVideo.launch(i)
        }
        btnArquivo.setOnClickListener {
            val i = Intent(Intent.ACTION_GET_CONTENT); i.type = "*/*"
            i.addCategory(Intent.CATEGORY_OPENABLE)
            selecionarArquivo.launch(i)
        }
        btnCancelarResposta.setOnClickListener { cancelarResposta() }

        lifecycleScope.launch {
            garantirCampoDigitando()
            typingHelper = TypingIndicatorHelper(
                collection = "chats",
                documentId = chatId,
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

        edtMensagem.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                typingHelper?.onDigitou()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        carregarMensagens()
    }

    private suspend fun garantirCampoDigitando() {
        try {
            val docRef = db.collection("chats").document(chatId)
            val doc = docRef.get().await()
            if (doc.exists() && !doc.contains("digitando")) {
                docRef.update("digitando", emptyMap<String, Long>()).await()
            }
        } catch (e: Exception) {
            Log.e("CHAT", "Erro garantir digitando: ${e.message}")
        }
    }

    private fun cancelarResposta() {
        respostaAtiva = null
        findViewById<LinearLayout>(R.id.containerRespondendo).visibility = View.GONE
    }

    private fun verificarBloqueio(edt: EditText, btn: Button) {
        lifecycleScope.launch {
            try {
                val euBloqueei = db.collection("bloqueios")
                    .whereEqualTo("bloqueadorEmail", emailUsuario)
                    .whereEqualTo("bloqueadoEmail", outroEmail).limit(1).get().await()
                val eleMeBloqueou = db.collection("bloqueios")
                    .whereEqualTo("bloqueadorEmail", outroEmail)
                    .whereEqualTo("bloqueadoEmail", emailUsuario).limit(1).get().await()
                if (!euBloqueei.isEmpty) {
                    edt.isEnabled = false; edt.hint = "Você bloqueou este usuário"; btn.isEnabled = false
                } else if (!eleMeBloqueou.isEmpty) {
                    edt.isEnabled = false; edt.hint = "Você foi bloqueado por este usuário"; btn.isEnabled = false
                }
            } catch (e: Exception) { Log.e("CHAT", "Erro bloqueio: ${e.message}") }
        }
    }

    private suspend fun criarOuBuscarChat(): String {
        val existente = db.collection("chats").whereArrayContains("participantes", emailUsuario).get().await()
        val chatExistente = existente.documents.find { doc ->
            (doc.get("participantes") as? List<*>)?.contains(outroEmail) == true
        }
        return if (chatExistente != null) {
            garantirChatsIdsDenormalizados(chatExistente.id); chatExistente.id
        } else {
            val novo = hashMapOf<String, Any>(
                "participantes" to listOf(emailUsuario, outroEmail),
                "ultimaMensagem" to "", "atualizadoEm" to System.currentTimeMillis(),
                "tipo" to "individual",
                "digitando" to emptyMap<String, Long>()
            )
            val id = db.collection("chats").add(novo).await().id
            garantirChatsIdsDenormalizados(id); id
        }
    }

    private suspend fun garantirChatsIdsDenormalizados(chatId: String) {
        try {
            val doc = db.collection("chats").document(chatId).get().await()
            val partes = doc.get("participantes") as? List<*> ?: return
            partes.forEach { email ->
                val e = email as? String ?: return@forEach
                val ref = db.collection("usuarios").document(e)
                val ud = ref.get().await()
                val ids = (ud.get("chatsIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                if (chatId !in ids) ref.update("chatsIds", ids + chatId).await()
            }
        } catch (e: Exception) { Log.e("CHAT", "Erro denorm: ${e.message}") }
    }

    private fun adicionarResposta(m: HashMap<String, Any>) {
        respostaAtiva?.let { r ->
            m["respostaPara"] = hashMapOf(
                "msgId" to r.msgId, "texto" to r.texto, "remetente" to r.remetente,
                "nomeRemetente" to r.nomeRemetente, "tipo" to r.tipo
            )
        }
    }

    private fun enviarMensagem(texto: String) {
        lifecycleScope.launch {
            try {
                val m = hashMapOf<String, Any>(
                    "remetente" to emailUsuario, "texto" to texto, "tipo" to "texto",
                    "timestamp" to System.currentTimeMillis(), "lida" to false
                )
                adicionarResposta(m)
                db.collection("chats").document(chatId).collection("mensagens").add(m).await()
                db.collection("chats").document(chatId).update(
                    mapOf("ultimaMensagem" to texto, "atualizadoEm" to System.currentTimeMillis())
                ).await()
                typingHelper?.limpar()
                cancelarResposta()
            } catch (e: Exception) { Toast.makeText(this@ChatActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun dispararResposta(msgId: String, texto: String, remetente: String, tipo: String = "texto") {
        lifecycleScope.launch {
            try {
                val nomeRem = if (remetente == emailUsuario) "Você" else {
                    db.collection("usuarios").document(remetente).get().await().getString("nome") ?: remetente
                }
                respostaAtiva = RespostaInfo(msgId, texto, remetente, nomeRem, tipo)
                findViewById<TextView>(R.id.txtRespondendoA).text = "Respondendo a $nomeRem"
                findViewById<TextView>(R.id.txtTextoRespondendo).text = when (tipo) {
                    "foto" -> "📷 Foto"; "video" -> "🎥 Vídeo"; "arquivo" -> "📎 Arquivo"; else -> texto
                }
                findViewById<LinearLayout>(R.id.containerRespondendo).visibility = View.VISIBLE
                findViewById<EditText>(R.id.edtMensagem).requestFocus()
            } catch (e: Exception) { Log.e("CHAT", "Erro: ${e.message}") }
        }
    }

    private fun enviarFoto(uri: Uri) {
        Toast.makeText(this, "📤 Enviando foto...", Toast.LENGTH_SHORT).show()
        MediaManager.get().upload(uri).unsigned("fqb729sb").option("folder", "chats_pv/")
            .callback(object : com.cloudinary.android.callback.UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val url = resultData?.get("secure_url") as? String
                    if (url != null) runOnUiThread { lifecycleScope.launch {
                        try {
                            val m = hashMapOf<String, Any>(
                                "remetente" to emailUsuario, "texto" to "", "fotoUrl" to url,
                                "tipo" to "foto", "timestamp" to System.currentTimeMillis(), "lida" to false
                            )
                            adicionarResposta(m)
                            db.collection("chats").document(chatId).collection("mensagens").add(m).await()
                            Toast.makeText(this@ChatActivity, "✅ Foto enviada!", Toast.LENGTH_SHORT).show()
                            typingHelper?.limpar()
                            cancelarResposta()
                        } catch (e: Exception) { Toast.makeText(this@ChatActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
                    } }
                }
                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    runOnUiThread { Toast.makeText(this@ChatActivity, "Erro: ${error?.description}", Toast.LENGTH_LONG).show() }
                }
                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            }).dispatch()
    }

    private fun enviarVideo(uri: Uri) {
        Toast.makeText(this, "📤 Enviando vídeo...", Toast.LENGTH_SHORT).show()
        MediaManager.get().upload(uri).unsigned("fqb729sb")
            .option("resource_type", "video").option("folder", "chats_videos/")
            .callback(object : com.cloudinary.android.callback.UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val url = resultData?.get("secure_url") as? String
                    if (url != null) runOnUiThread { lifecycleScope.launch {
                        try {
                            val m = hashMapOf<String, Any>(
                                "remetente" to emailUsuario, "texto" to "", "videoUrl" to url,
                                "tipo" to "video", "timestamp" to System.currentTimeMillis(), "lida" to false
                            )
                            adicionarResposta(m)
                            db.collection("chats").document(chatId).collection("mensagens").add(m).await()
                            Toast.makeText(this@ChatActivity, "✅ Vídeo enviado!", Toast.LENGTH_SHORT).show()
                            typingHelper?.limpar()
                            cancelarResposta()
                        } catch (e: Exception) { Toast.makeText(this@ChatActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
                    } }
                }
                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    runOnUiThread { Toast.makeText(this@ChatActivity, "Erro: ${error?.description}", Toast.LENGTH_LONG).show() }
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
            .option("resource_type", "raw").option("folder", "chats_arquivos/")
            .callback(object : com.cloudinary.android.callback.UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val url = resultData?.get("secure_url") as? String
                    if (url != null) runOnUiThread { lifecycleScope.launch {
                        try {
                            val m = hashMapOf<String, Any>(
                                "remetente" to emailUsuario, "texto" to "", "arquivoUrl" to url,
                                "nomeArquivo" to nF, "tamanhoArquivo" to tF, "mimeType" to mF,
                                "tipo" to "arquivo", "timestamp" to System.currentTimeMillis(), "lida" to false
                            )
                            adicionarResposta(m)
                            db.collection("chats").document(chatId).collection("mensagens").add(m).await()
                            Toast.makeText(this@ChatActivity, "✅ Arquivo enviado!", Toast.LENGTH_SHORT).show()
                            typingHelper?.limpar()
                            cancelarResposta()
                        } catch (e: Exception) { Toast.makeText(this@ChatActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
                    } }
                }
                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    runOnUiThread { Toast.makeText(this@ChatActivity, "Erro: ${error?.description}", Toast.LENGTH_LONG).show() }
                }
                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            }).dispatch()
    }

    private fun carregarMensagens() {
        // ✅ GUARDA: se Activity ja foi destruida, nao carrega nada
        if (isFinishing || isDestroyed) return

        val container = findViewById<LinearLayout>(R.id.containerMensagens)
        val scroll = findViewById<ScrollView>(R.id.scrollMensagens)

        db.collection("chats").document(chatId).collection("mensagens")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                // ✅ GUARDA: se Activity foi destruida enquanto o listener rodava, aborta
                if (isFinishing || isDestroyed) return@addSnapshotListener
                if (error != null || snapshots == null) return@addSnapshotListener

                container.removeAllViews()
                val inflater = LayoutInflater.from(this)

                val listaMidias = mutableListOf<Pair<String, String>>()
                snapshots.documents.forEach { doc ->
                    when (doc.getString("tipo") ?: "texto") {
                        "foto" -> (doc.getString("fotoUrl") ?: "").takeIf { it.isNotEmpty() }?.let { listaMidias.add("foto" to it) }
                        "video" -> (doc.getString("videoUrl") ?: "").takeIf { it.isNotEmpty() }?.let { listaMidias.add("video" to it) }
                    }
                }

                var idx = 0

                snapshots.documents.forEach { doc ->
                    // ✅ GUARDA por mensagem (belt and suspenders)
                    if (isFinishing || isDestroyed) return@forEach

                    val msgId = doc.id
                    val remetente = doc.getString("remetente") ?: ""
                    val texto = doc.getString("texto") ?: ""
                    val tipo = doc.getString("tipo") ?: "texto"
                    val fotoUrl = doc.getString("fotoUrl") ?: ""
                    val videoUrl = doc.getString("videoUrl") ?: ""
                    val arquivoUrl = doc.getString("arquivoUrl") ?: ""
                    val nomeArquivo = doc.getString("nomeArquivo") ?: "arquivo"
                    val tamArquivo = doc.getLong("tamanhoArquivo") ?: 0L
                    val mimeType = doc.getString("mimeType") ?: ""
                    val respostaPara = doc.get("respostaPara") as? Map<*, *>
                    val ehRem = remetente == emailUsuario

                    when (tipo) {
                        "texto" -> {
                            val view = inflater.inflate(R.layout.item_mensagem_texto, container, false)
                            renderizarTexto(view, msgId, texto, remetente, "", respostaPara, ehRem)
                            container.addView(view)
                        }
                        "foto" -> {
                            val view = inflater.inflate(R.layout.item_mensagem_foto, container, false)
                            val img = view.findViewById<ImageView>(R.id.imgMensagemFoto)
                            val txtNome = view.findViewById<TextView>(R.id.txtNomeFoto)
                            val containerBalao = view.findViewById<LinearLayout>(R.id.containerBalao)
                            val imgInd = view.findViewById<ImageView>(R.id.imgIndicadorResposta)

                            txtNome.text = if (ehRem) "Você" else outroEmail

                            // ✅ applicationContext em vez de this
                            if (!isFinishing && !isDestroyed) {
                                Glide.with(applicationContext).load(fotoUrl).into(img)
                            }

                            aplicarAlinhamentoRelative(containerBalao, ehRem)

                            val pos = idx; idx++
                            SwipeToReplyHelper.attach(containerBalao, imgInd) { dispararResposta(msgId, "", remetente, "foto") }
                            containerBalao.setOnClickListener { abrirGaleria(listaMidias, pos) }
                            containerBalao.setOnLongClickListener {
                                mostrarMenuMidia("foto", fotoUrl, "", mimeType, msgId, ehRem, texto, remetente); true
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

                            txtNome.text = if (ehRem) "Você" else outroEmail
                            aplicarAlinhamentoRelative(containerBalao, ehRem)

                            try {
                                videoView.setVideoURI(Uri.parse(videoUrl))
                            } catch (e: Exception) {
                                Log.e("CHAT", "Erro video: ${e.message}")
                            }

                            videoView.setOnErrorListener { _, _, _ ->
                                btnPlay.text = "⚠"; btnPlay.visibility = View.VISIBLE; true
                            }

                            btnPlay.setOnClickListener {
                                try {
                                    if (videoView.isPlaying) {
                                        videoView.pause(); btnPlay.text = "▶"; btnPlay.visibility = View.VISIBLE
                                    } else {
                                        videoView.start(); btnPlay.visibility = View.GONE
                                    }
                                } catch (e: Exception) { Log.e("CHAT", "Erro play: ${e.message}") }
                            }
                            videoView.setOnCompletionListener {
                                btnPlay.text = "▶"; btnPlay.visibility = View.VISIBLE
                            }

                            val pos = idx; idx++
                            SwipeToReplyHelper.attach(
                                viewToTouch = overlay,
                                containerBalao = containerBalao,
                                imgIndicador = imgInd,
                                onResponder = { dispararResposta(msgId, "", remetente, "video") }
                            )
                            overlay.setOnClickListener { abrirGaleria(listaMidias, pos) }
                            overlay.setOnLongClickListener {
                                mostrarMenuMidia("video", videoUrl, "", "", msgId, ehRem, texto, remetente); true
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

                            txtNomeRem.text = if (ehRem) "Você" else outroEmail
                            txtNomeArq.text = nomeArquivo
                            txtTam.text = formatarTamanho(tamArquivo)
                            aplicarAlinhamentoRelative(containerBalao, ehRem)

                            SwipeToReplyHelper.attach(containerBalao, imgInd) { dispararResposta(msgId, "", remetente, "arquivo") }
                            containerBalao.setOnClickListener { abrirArquivoExterno(arquivoUrl, mimeType, nomeArquivo) }
                            containerBalao.setOnLongClickListener {
                                mostrarMenuMidia("arquivo", arquivoUrl, nomeArquivo, mimeType, msgId, ehRem, texto, remetente); true
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
        nomeRemetente: String, respostaPara: Map<*, *>?, ehRemetente: Boolean
    ) {
        val containerBalao = view.findViewById<LinearLayout>(R.id.containerBalao)
        val imgInd = view.findViewById<ImageView>(R.id.imgIndicadorResposta)
        val txtTexto = view.findViewById<TextView>(R.id.txtTextoMensagem)
        val containerCit = view.findViewById<LinearLayout>(R.id.containerCitacao)
        val txtNomeCit = view.findViewById<TextView>(R.id.txtNomeCitado)
        val txtTextoCit = view.findViewById<TextView>(R.id.txtTextoCitado)

        aplicarAlinhamentoRelative(containerBalao, ehRemetente)

        if (ehRemetente) {
            containerBalao.setBackgroundResource(R.drawable.bg_bolha_enviada)
            txtTexto.setTextColor(ContextCompat.getColor(this, R.color.accent_dark))
        } else {
            containerBalao.setBackgroundResource(R.drawable.bg_bolha_recebida)
            txtTexto.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
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

        SwipeToReplyHelper.attach(containerBalao, imgInd) { dispararResposta(msgId, texto, remetente, "texto") }
        containerBalao.setOnLongClickListener {
            mostrarOpcaoMensagem(msgId, texto, remetente, ehRemetente); true
        }
    }

    private fun mostrarOpcaoMensagem(msgId: String, texto: String, remetente: String, ehRem: Boolean) {
        lifecycleScope.launch {
            try {
                val jaFav = db.collection("favoritos")
                    .whereEqualTo("usuarioEmail", emailUsuario).whereEqualTo("mensagemId", msgId)
                    .limit(1).get().await().let { !it.isEmpty }
                val opcoes = mutableListOf("💬 Responder")
                opcoes.add(if (jaFav) "⭐ Remover dos favoritos" else "⭐ Favoritar")
                if (ehRem) opcoes.add("🗑 Apagar para todos")

                AlertDialog.Builder(this@ChatActivity).setTitle("Opções")
                    .setItems(opcoes.toTypedArray()) { _, w ->
                        when (opcoes[w]) {
                            "💬 Responder" -> dispararResposta(msgId, texto, remetente, "texto")
                            "⭐ Favoritar" -> favoritar(msgId, texto, remetente, "texto")
                            "⭐ Remover dos favoritos" -> desfavoritar(msgId)
                            "🗑 Apagar para todos" -> apagarMensagem(msgId)
                        }
                    }.show()
            } catch (e: Exception) { Toast.makeText(this@ChatActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun mostrarMenuMidia(
        tipo: String, url: String, nomeArq: String, mime: String,
        msgId: String, ehRem: Boolean, texto: String, remetente: String
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

                AlertDialog.Builder(this@ChatActivity).setTitle("Opções")
                    .setItems(opcoes.toTypedArray()) { _, w ->
                        when (opcoes[w]) {
                            "💬 Responder" -> dispararResposta(msgId, texto, remetente, tipo)
                            "⭐ Favoritar" -> favoritar(msgId, texto, remetente, tipo)
                            "⭐ Remover dos favoritos" -> desfavoritar(msgId)
                            "⬇ Baixar" -> {
                                val nome = when (tipo) {
                                    "foto" -> "CTR_foto_${System.currentTimeMillis()}.jpg"
                                    "video" -> "CTR_video_${System.currentTimeMillis()}.mp4"
                                    else -> nomeArq
                                }
                                val pasta = when (tipo) {
                                    "foto" -> Environment.DIRECTORY_PICTURES
                                    "video" -> Environment.DIRECTORY_MOVIES
                                    else -> Environment.DIRECTORY_DOWNLOADS
                                }
                                baixarArquivo(url, nome, pasta)
                            }
                            "📂 Abrir" -> abrirArquivoExterno(url, mime, nomeArq)
                            "🗑 Apagar" -> apagarMensagem(msgId)
                        }
                    }.show()
            } catch (e: Exception) { Toast.makeText(this@ChatActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun favoritar(msgId: String, texto: String, remetente: String, tipoMidia: String) {
        lifecycleScope.launch {
            try {
                val nomeRem = if (remetente == emailUsuario) "Você" else {
                    db.collection("usuarios").document(remetente).get().await().getString("nome") ?: remetente
                }
                db.collection("favoritos").add(hashMapOf(
                    "usuarioEmail" to emailUsuario, "mensagemId" to msgId, "chatId" to chatId,
                    "tipoChat" to "pv", "texto" to texto, "remetente" to remetente,
                    "nomeRemetente" to nomeRem, "tipoMidia" to tipoMidia,
                    "criadoEm" to System.currentTimeMillis()
                )).await()
                Toast.makeText(this@ChatActivity, "⭐ Favoritado", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(this@ChatActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun desfavoritar(msgId: String) {
        lifecycleScope.launch {
            try {
                db.collection("favoritos").whereEqualTo("usuarioEmail", emailUsuario)
                    .whereEqualTo("mensagemId", msgId).get().await()
                    .documents.forEach { it.reference.delete().await() }
                Toast.makeText(this@ChatActivity, "Removido", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(this@ChatActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun abrirGaleria(lista: List<Pair<String, String>>, pos: Int) {
        val urls = lista.map { it.second }.toTypedArray()
        val tipos = lista.map { it.first }.toTypedArray()
        val i = Intent(this, GaleriaMidiaActivity::class.java)
        i.putExtra("urls", urls); i.putExtra("tipos", tipos); i.putExtra("posicaoInicial", pos)
        startActivity(i)
    }

    private fun abrirArquivoExterno(url: String, mime: String, nome: String) {
        try {
            val i = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(url), mime.ifEmpty { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(i)
        } catch (e: Exception) { Toast.makeText(this, "Nenhum app", Toast.LENGTH_LONG).show() }
    }

    private fun baixarArquivo(url: String, nome: String, pasta: String) {
        try {
            val req = DownloadManager.Request(Uri.parse(url))
            req.setTitle(nome); req.setDescription("Baixando do CTR...")
            req.allowScanningByMediaScanner()
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            req.setDestinationInExternalPublicDir(pasta, nome)
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            Toast.makeText(this, "⬇ Baixando...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(this, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    private fun apagarMensagem(msgId: String) {
        lifecycleScope.launch {
            try {
                db.collection("chats").document(chatId).collection("mensagens").document(msgId).delete().await()
                Toast.makeText(this@ChatActivity, "Apagada", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(this@ChatActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
        }
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