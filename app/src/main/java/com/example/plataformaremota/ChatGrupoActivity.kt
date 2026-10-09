package com.example.plataformaremota

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cloudinary.android.MediaManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChatGrupoActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""
    private var nomeUsuario: String = ""
    private var grupoId: String = ""
    private var equipeId: String = ""
    private var nomeGrupo: String = ""

    private var membrosGrupoCache: List<String> = emptyList()

    private var respostaAtiva: RespostaInfo? = null
    private var typingHelper: TypingIndicatorHelper? = null

    private lateinit var adapter: MensagemAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var layoutManager: LinearLayoutManager

    private var paginacaoHelper: ChatPaginacaoHelper? = null
    private var deveAutoScroll: Boolean = true

    private val abrirPreview = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val acao = r.data?.getStringExtra("acao")
            if (acao == "enviar") {
                val uri = r.data?.getStringExtra("uri")?.let { Uri.parse(it) }
                val tipoMidia = r.data?.getStringExtra("tipo") ?: "foto"
                val legenda = r.data?.getStringExtra("legenda") ?: ""
                if (uri != null) {
                    when (tipoMidia) {
                        "foto" -> enviarFoto(uri, legenda)
                        "video" -> enviarVideo(uri, legenda)
                        "arquivo" -> enviarArquivo(uri, legenda)
                    }
                }
            }
        }
    }

    private val selecionarImagem = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val uri = r.data?.data
            if (uri != null) {
                val i = Intent(this, PreviewMidiaActivity::class.java).apply {
                    putExtra("uri", uri.toString())
                    putExtra("tipo", "foto")
                    putExtra("chatTipo", "grupo")
                    putExtra("chatId", grupoId)
                }
                abrirPreview.launch(i)
            }
        }
    }
    private val selecionarVideo = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val uri = r.data?.data
            if (uri != null) {
                val i = Intent(this, PreviewMidiaActivity::class.java).apply {
                    putExtra("uri", uri.toString())
                    putExtra("tipo", "video")
                    putExtra("chatTipo", "grupo")
                    putExtra("chatId", grupoId)
                }
                abrirPreview.launch(i)
            }
        }
    }
    private val selecionarArquivo = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val uri = r.data?.data
            if (uri != null) {
                val i = Intent(this, PreviewMidiaActivity::class.java).apply {
                    putExtra("uri", uri.toString())
                    putExtra("tipo", "arquivo")
                    putExtra("chatTipo", "grupo")
                    putExtra("chatId", grupoId)
                }
                abrirPreview.launch(i)
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
        nomeGrupo = intent.getStringExtra("nomeGrupo") ?: getString(R.string.grupo_padrao)

        if (grupoId.isEmpty()) {
            Toast.makeText(this, getString(R.string.chat_grupo_nao_encontrado), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<Button>(R.id.btnSairGrupo).setOnClickListener { confirmarSairGrupo() }
        findViewById<Button>(R.id.btnGerenciarMembros).setOnClickListener {
            val i = Intent(this, GerenciarMembrosGrupoActivity::class.java)
            i.putExtra("grupoId", grupoId)
            i.putExtra("equipeId", equipeId)
            startActivity(i)
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

        carregarDadosGrupo()

        btnEnviar.setOnClickListener {
            val t = edtMensagem.text.toString().trim()
            if (t.isEmpty()) return@setOnClickListener
            edtMensagem.text.clear()
            enviarMensagem(t)
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
                            txtDigitando.text = getString(R.string.chat_digitando, nome)
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

        recycler = findViewById(R.id.recyclerMensagensGrupo)
        adapter = MensagemAdapter(
            emailUsuario = emailUsuario,
            outroEmail = "",
            contexto = this,
            callbacks = object : MensagemAdapter.Callbacks {

                override fun onResponder(msgId: String, texto: String, remetente: String, tipo: String) {
                    dispararResposta(msgId, texto, remetente, nomeUsuario, tipo)
                }
                override fun onFotoClick(posicaoNaGaleria: Int, listaMidias: List<Pair<String, String>>) {
                    abrirGaleria(listaMidias, posicaoNaGaleria)
                }
                override fun onVideoClick(posicaoNaGaleria: Int, listaMidias: List<Pair<String, String>>) {
                    abrirGaleria(listaMidias, posicaoNaGaleria)
                }
                override fun onLongPressTexto(msgId: String, texto: String, remetente: String, ehRem: Boolean) {
                    mostrarOpcaoMensagem(msgId, texto, remetente, nomeUsuario, ehRem)
                }
                override fun onLongPressMidia(
                    tipo: String, url: String, nomeArq: String, mime: String,
                    msgId: String, ehRem: Boolean, texto: String, remetente: String
                ) {
                    mostrarMenuMidia(tipo, url, nomeArq, mime, msgId, ehRem, texto, remetente, nomeUsuario)
                }
                override fun onAbrirArquivo(url: String, mime: String, nome: String) {
                    abrirArquivoExterno(url, mime, nome)
                }
            }
        )

        layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recycler.layoutManager = layoutManager
        recycler.adapter = adapter

        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                val total = adapter.itemCount
                if (total == 0) return
                val ultimoVisivel = layoutManager.findLastCompletelyVisibleItemPosition()
                deveAutoScroll = (total - ultimoVisivel) <= 3
                val primeiroVisivel = layoutManager.findFirstCompletelyVisibleItemPosition()
                if (primeiroVisivel in 0..3) paginacaoHelper?.carregarMaisAntigas()
            }
        })

        paginacaoHelper = ChatPaginacaoHelper(
            collection = "grupos",
            documentId = grupoId,
            pageSize = 50,
            onListaAtualizada = { todas, inseriuNoTopo ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    adapter.submitList(ItemChat.deMensagens(this@ChatGrupoActivity, todas)) {
                        if (!inseriuNoTopo && adapter.itemCount > 0) recycler.scrollToPosition(adapter.itemCount - 1)
                    }
                }
            },
            onNovasMensagens = { novas ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (novas.isEmpty()) return@runOnUiThread
                    val estavaNoFim = deveAutoScroll
                    val mensagensAtuais = adapter.currentList
                        .filterIsInstance<ItemChat.MensagemItem>()
                        .map { it.mensagem }
                        .toMutableList()

                    mensagensAtuais.addAll(novas)
                    adapter.submitList(ItemChat.deMensagens(this@ChatGrupoActivity, mensagensAtuais)
) {
                        if (estavaNoFim && adapter.itemCount > 0) recycler.scrollToPosition(adapter.itemCount - 1)
                    }
                }
            }
        )
        paginacaoHelper?.iniciar()
    }

    override fun onResume() { super.onResume(); marcarMensagensComoLidas() }
    override fun onPause() { super.onPause(); typingHelper?.limpar() }

    override fun onDestroy() {
        super.onDestroy()
        typingHelper?.destruir(); typingHelper = null
        paginacaoHelper?.destruir(); paginacaoHelper = null
    }

    private fun carregarDadosGrupo() {
        lifecycleScope.launch {
            try {
                val grupoDoc = db.collection("grupos").document(grupoId).get().await()
                equipeId = grupoDoc.getString("equipeId") ?: ""
                val membros = (grupoDoc.get("membros") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                membrosGrupoCache = membros.filter { it.isNotEmpty() && it != emailUsuario }
                nomeUsuario = db.collection("usuarios").document(emailUsuario).get().await().getString("nome")
                    ?: getString(R.string.usuario_padrao)
            } catch (e: Exception) { Log.e("CHAT_GRUPO", "Erro: ${e.message}") }
        }
    }

    private suspend fun incrementarNaoLidasGrupo() {
        try {
            val grupoDoc = db.collection("grupos").document(grupoId).get().await()
            val naoLidasAtual = (grupoDoc.get("naoLidas") as? Map<String, Any>) ?: emptyMap()
            val naoLidasNovo = naoLidasAtual.toMutableMap()

            membrosGrupoCache.forEach { membro ->
                val valorAtual = (naoLidasNovo[membro] as? Number)?.toLong() ?: 0L
                naoLidasNovo[membro] = valorAtual + 1
            }

            db.collection("grupos").document(grupoId)
                .update("naoLidas", naoLidasNovo).await()

            Log.d("CHAT_GRUPO", "✅ naoLidas atualizado: $naoLidasNovo")
        } catch (e: Exception) {
            Log.e("CHAT_GRUPO", "❌ Erro incrementar: ${e.message}")
        }
    }

    private fun marcarMensagensComoLidas() {
        if (grupoId.isEmpty()) return
        lifecycleScope.launch {
            try {
                val msgs = db.collection("grupos").document(grupoId).collection("mensagens")
                    .whereEqualTo("lida", false).get().await()

                if (msgs.isEmpty) {
                    val grupoDoc = db.collection("grupos").document(grupoId).get().await()
                    val naoLidasAtual = (grupoDoc.get("naoLidas") as? Map<String, Any>) ?: emptyMap()
                    val naoLidasNovo = naoLidasAtual.toMutableMap()
                    naoLidasNovo[emailUsuario] = 0L

                    db.collection("grupos").document(grupoId)
                        .update("naoLidas", naoLidasNovo).await()

                    ChatResumoHelper.zerarNaoLidas(emailUsuario, grupoId)
                    return@launch
                }

                val batch = db.batch()
                msgs.documents.forEach { doc ->
                    if ((doc.getString("remetente") ?: "") != emailUsuario) batch.update(doc.reference, "lida", true)
                }
                batch.commit().await()

                val grupoDoc = db.collection("grupos").document(grupoId).get().await()
                val naoLidasAtual = (grupoDoc.get("naoLidas") as? Map<String, Any>) ?: emptyMap()
                val naoLidasNovo = naoLidasAtual.toMutableMap()
                naoLidasNovo[emailUsuario] = 0L

                db.collection("grupos").document(grupoId)
                    .update("naoLidas", naoLidasNovo).await()

                ChatResumoHelper.zerarNaoLidas(emailUsuario, grupoId)
            } catch (e: Exception) { Log.e("CHAT_GRUPO", "Erro: ${e.message}") }
        }
    }

    private suspend fun garantirCampoDigitando() {
        try {
            val docRef = db.collection("grupos").document(grupoId)
            val doc = docRef.get().await()
            if (doc.exists() && !doc.contains("digitando")) {
                docRef.update("digitando", emptyMap<String, Long>()).await()
            }
        } catch (e: Exception) { Log.e("CHAT_GRUPO", "Erro: ${e.message}") }
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
        findViewById<TextView>(R.id.txtRespondendoAGrupo).text = getString(R.string.chat_respondendo_a, nomeRem)
        findViewById<TextView>(R.id.txtTextoRespondendoGrupo).text = when (tipo) {
            "foto" -> getString(R.string.chat_tipo_foto)
            "video" -> getString(R.string.chat_tipo_video)
            "arquivo" -> getString(R.string.chat_tipo_arquivo)
            else -> texto
        }
        findViewById<LinearLayout>(R.id.containerRespondendoGrupo).visibility = View.VISIBLE
        findViewById<EditText>(R.id.edtMensagemGrupo).requestFocus()
    }

    private fun enviarMensagem(texto: String) {
        lifecycleScope.launch {
            try {
                val ts = System.currentTimeMillis()
                val m = hashMapOf<String, Any>(
                    "remetente" to emailUsuario, "nomeRemetente" to nomeUsuario,
                    "texto" to texto, "tipo" to "texto", "timestamp" to ts, "lida" to false
                )
                adicionarResposta(m)

                val msgRef = db.collection("grupos").document(grupoId).collection("mensagens").document()
                msgRef.set(m).await()

                val preview = hashMapOf<String, Any>(
                    "texto" to texto, "autorNome" to nomeUsuario, "tipo" to "texto", "timestamp" to ts
                )
                db.collection("grupos").document(grupoId).update(
                    mapOf(
                        "ultimaMensagem" to texto,
                        "ultimaMensagemPreview" to preview,
                        "atualizadoEm" to ts
                    )
                ).await()

                incrementarNaoLidasGrupo()

                try {
                    ChatResumoHelper.atualizarResumo(
                        emailUsuario = emailUsuario,
                        chatId = grupoId,
                        nome = nomeGrupo,
                        ultimaMsg = texto,
                        timestamp = ts,
                        tipo = "grupo",
                        incrementarNaoLidas = false
                    )
                } catch (e: Exception) { Log.e("CHAT_GRUPO", "Erro resumo: ${e.message}") }

                typingHelper?.limpar(); cancelarResposta()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ChatGrupoActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun enviarFoto(uri: Uri, legenda: String = "") {
        Toast.makeText(this, getString(R.string.chat_enviando_foto), Toast.LENGTH_SHORT).show()
        MediaManager.get().upload(uri).unsigned("fqb729sb").option("folder", "chats_grupo/")
            .callback(object : com.cloudinary.android.callback.UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val url = resultData?.get("secure_url") as? String
                    if (url != null) runOnUiThread { lifecycleScope.launch {
                        try {
                            val ts = System.currentTimeMillis()
                            val m = hashMapOf<String, Any>(
                                "remetente" to emailUsuario, "nomeRemetente" to nomeUsuario,
                                "texto" to legenda, "fotoUrl" to url, "tipo" to "foto", "timestamp" to ts, "lida" to false
                            )
                            adicionarResposta(m)

                            val msgRef = db.collection("grupos").document(grupoId).collection("mensagens").document()
                            msgRef.set(m).await()

                            val preview = hashMapOf<String, Any>(
                                "texto" to legenda.ifEmpty { "Foto" }, "autorNome" to nomeUsuario,
                                "tipo" to "foto", "timestamp" to ts
                            )
                            val textoPreview = if (legenda.isEmpty()) "📷 Foto" else "📷 $legenda"
                            db.collection("grupos").document(grupoId).update(
                                mapOf(
                                    "ultimaMensagem" to textoPreview,
                                    "ultimaMensagemPreview" to preview,
                                    "atualizadoEm" to ts
                                )
                            ).await()

                            incrementarNaoLidasGrupo()

                            try {
                                ChatResumoHelper.atualizarResumo(
                                    emailUsuario = emailUsuario, chatId = grupoId, nome = nomeGrupo,
                                    ultimaMsg = textoPreview, timestamp = ts, tipo = "grupo",
                                    incrementarNaoLidas = false
                                )
                            } catch (e: Exception) { Log.e("CHAT_GRUPO", "Erro resumo: ${e.message}") }

                            Toast.makeText(this@ChatGrupoActivity, getString(R.string.chat_foto_enviada), Toast.LENGTH_SHORT).show()
                            typingHelper?.limpar(); cancelarResposta()
                        } catch (e: Exception) {
                            Toast.makeText(
                                this@ChatGrupoActivity,
                                getString(R.string.erro_generico, e.message ?: ""),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } }
                }
                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    runOnUiThread {
                        Toast.makeText(
                            this@ChatGrupoActivity,
                            getString(R.string.erro_generico, error?.description ?: ""),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            }).dispatch()
    }

    private fun enviarVideo(uri: Uri, legenda: String = "") {
        Toast.makeText(this, getString(R.string.chat_enviando_video), Toast.LENGTH_SHORT).show()
        MediaManager.get().upload(uri).unsigned("fqb729sb")
            .option("resource_type", "video").option("folder", "chats_grupo_videos/")
            .callback(object : com.cloudinary.android.callback.UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val url = resultData?.get("secure_url") as? String
                    if (url != null) runOnUiThread { lifecycleScope.launch {
                        try {
                            val ts = System.currentTimeMillis()
                            val m = hashMapOf<String, Any>(
                                "remetente" to emailUsuario, "nomeRemetente" to nomeUsuario,
                                "texto" to legenda, "videoUrl" to url, "tipo" to "video", "timestamp" to ts, "lida" to false
                            )
                            adicionarResposta(m)

                            val msgRef = db.collection("grupos").document(grupoId).collection("mensagens").document()
                            msgRef.set(m).await()

                            val preview = hashMapOf<String, Any>(
                                "texto" to legenda.ifEmpty { "Vídeo" }, "autorNome" to nomeUsuario,
                                "tipo" to "video", "timestamp" to ts
                            )
                            val textoPreview = if (legenda.isEmpty()) "🎥 Vídeo" else "🎥 $legenda"
                            db.collection("grupos").document(grupoId).update(
                                mapOf(
                                    "ultimaMensagem" to textoPreview,
                                    "ultimaMensagemPreview" to preview,
                                    "atualizadoEm" to ts
                                )
                            ).await()

                            incrementarNaoLidasGrupo()

                            try {
                                ChatResumoHelper.atualizarResumo(
                                    emailUsuario = emailUsuario, chatId = grupoId, nome = nomeGrupo,
                                    ultimaMsg = textoPreview, timestamp = ts, tipo = "grupo",
                                    incrementarNaoLidas = false
                                )
                            } catch (e: Exception) { Log.e("CHAT_GRUPO", "Erro resumo: ${e.message}") }

                            Toast.makeText(this@ChatGrupoActivity, getString(R.string.chat_video_enviado), Toast.LENGTH_SHORT).show()
                            typingHelper?.limpar(); cancelarResposta()
                        } catch (e: Exception) {
                            Toast.makeText(
                                this@ChatGrupoActivity,
                                getString(R.string.erro_generico, e.message ?: ""),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } }
                }
                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    runOnUiThread {
                        Toast.makeText(
                            this@ChatGrupoActivity,
                            getString(R.string.erro_generico, error?.description ?: ""),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            }).dispatch()
    }

    private fun enviarArquivo(uri: Uri, legenda: String = "") {
        Toast.makeText(this, getString(R.string.chat_enviando_arquivo), Toast.LENGTH_SHORT).show()
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
                            val ts = System.currentTimeMillis()
                            val m = hashMapOf<String, Any>(
                                "remetente" to emailUsuario, "nomeRemetente" to nomeUsuario,
                                "texto" to legenda, "arquivoUrl" to url, "nomeArquivo" to nF,
                                "tamanhoArquivo" to tF, "mimeType" to mF,
                                "tipo" to "arquivo", "timestamp" to ts, "lida" to false
                            )
                            adicionarResposta(m)

                            val msgRef = db.collection("grupos").document(grupoId).collection("mensagens").document()
                            msgRef.set(m).await()

                            val preview = hashMapOf<String, Any>(
                                "texto" to legenda.ifEmpty { nF }, "autorNome" to nomeUsuario,
                                "tipo" to "arquivo", "timestamp" to ts
                            )
                            val textoPreview = if (legenda.isEmpty()) "📎 $nF" else "📎 $legenda"
                            db.collection("grupos").document(grupoId).update(
                                mapOf(
                                    "ultimaMensagem" to textoPreview,
                                    "ultimaMensagemPreview" to preview,
                                    "atualizadoEm" to ts
                                )
                            ).await()

                            incrementarNaoLidasGrupo()

                            try {
                                ChatResumoHelper.atualizarResumo(
                                    emailUsuario = emailUsuario, chatId = grupoId, nome = nomeGrupo,
                                    ultimaMsg = textoPreview, timestamp = ts, tipo = "grupo",
                                    incrementarNaoLidas = false
                                )
                            } catch (e: Exception) { Log.e("CHAT_GRUPO", "Erro resumo: ${e.message}") }

                            Toast.makeText(this@ChatGrupoActivity, getString(R.string.chat_arquivo_enviado), Toast.LENGTH_SHORT).show()
                            typingHelper?.limpar(); cancelarResposta()
                        } catch (e: Exception) {
                            Toast.makeText(
                                this@ChatGrupoActivity,
                                getString(R.string.erro_generico, e.message ?: ""),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } }
                }
                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    runOnUiThread {
                        Toast.makeText(
                            this@ChatGrupoActivity,
                            getString(R.string.erro_generico, error?.description ?: ""),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            }).dispatch()
    }

    private fun confirmarSairGrupo() {
        lifecycleScope.launch {
            try {
                val gd = db.collection("grupos").document(grupoId).get().await()
                if (gd.getString("criadorEmail") == emailUsuario) {
                    Toast.makeText(
                        this@ChatGrupoActivity,
                        getString(R.string.chat_grupo_criador_aviso),
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }
                AlertDialog.Builder(this@ChatGrupoActivity)
                    .setTitle(getString(R.string.chat_grupo_sair_titulo))
                    .setMessage(getString(R.string.chat_grupo_sair_msg))
                    .setPositiveButton(getString(R.string.chat_grupo_sair_confirmar)) { _, _ ->
                        lifecycleScope.launch {
                            try {
                                val membros = gd.get("membros") as? List<*> ?: emptyList<Any>()
                                db.collection("grupos").document(grupoId)
                                    .update("membros", membros.filter { it != emailUsuario }).await()
                                removerGrupoIdDoUsuario(emailUsuario, grupoId)
                                ChatResumoHelper.removerResumo(emailUsuario, grupoId)
                                Toast.makeText(
                                    this@ChatGrupoActivity,
                                    getString(R.string.chat_grupo_saiu),
                                    Toast.LENGTH_SHORT
                                ).show()
                                finish()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    this@ChatGrupoActivity,
                                    getString(R.string.erro_generico, e.message ?: ""),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }.setNegativeButton(R.string.cancelar, null).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ChatGrupoActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun mostrarOpcaoMensagem(msgId: String, texto: String, remetente: String, nomeRem: String, ehRem: Boolean) {
        lifecycleScope.launch {
            try {
                val jaFav = db.collection("favoritos").whereEqualTo("usuarioEmail", emailUsuario)
                    .whereEqualTo("mensagemId", msgId).limit(1).get().await().let { !it.isEmpty }

                val responder = getString(R.string.chat_responder)
                val favoritar = getString(R.string.chat_favoritar)
                val desfavoritar = getString(R.string.chat_desfavoritar)
                val info = getString(R.string.chat_informacoes)
                val apagarTodos = getString(R.string.chat_apagar_todos)

                val opcoes = mutableListOf(responder)
                opcoes.add(if (jaFav) desfavoritar else favoritar)
                opcoes.add(info)
                if (ehRem) opcoes.add(apagarTodos)

                AlertDialog.Builder(this@ChatGrupoActivity)
                    .setTitle(getString(R.string.chat_opcoes_titulo))
                    .setItems(opcoes.toTypedArray()) { _, w ->
                        when (opcoes[w]) {
                            responder -> dispararResposta(msgId, texto, remetente, nomeRem, "texto")
                            favoritar -> favoritar(msgId, texto, remetente, nomeRem, "texto")
                            desfavoritar -> desfavoritar(msgId)
                            info -> abrirInfoMensagem(msgId)
                            apagarTodos -> apagarMensagem(msgId)
                        }
                    }.show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ChatGrupoActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun mostrarMenuMidia(
        tipo: String, url: String, nomeArq: String, mime: String,
        msgId: String, ehRem: Boolean, texto: String, remetente: String, nomeRem: String
    ) {
        lifecycleScope.launch {
            try {
                val jaFav = db.collection("favoritos").whereEqualTo("usuarioEmail", emailUsuario)
                    .whereEqualTo("mensagemId", msgId).limit(1).get().await().let { !it.isEmpty }

                val responder = getString(R.string.chat_responder)
                val favoritar = getString(R.string.chat_favoritar)
                val desfavoritar = getString(R.string.chat_desfavoritar)
                val info = getString(R.string.chat_informacoes)
                val baixar = getString(R.string.chat_baixar)
                val abrir = getString(R.string.chat_abrir)
                val apagar = getString(R.string.chat_apagar)

                val opcoes = mutableListOf(responder)
                opcoes.add(if (jaFav) desfavoritar else favoritar)
                opcoes.add(info)
                opcoes.add(baixar)
                if (tipo == "arquivo") opcoes.add(abrir)
                if (ehRem) opcoes.add(apagar)

                AlertDialog.Builder(this@ChatGrupoActivity)
                    .setTitle(getString(R.string.chat_opcoes_titulo))
                    .setItems(opcoes.toTypedArray()) { _, w ->
                        when (opcoes[w]) {
                            responder -> dispararResposta(msgId, texto, remetente, nomeRem, tipo)
                            favoritar -> favoritar(msgId, texto, remetente, nomeRem, tipo)
                            desfavoritar -> desfavoritar(msgId)
                            info -> abrirInfoMensagem(msgId)
                            baixar -> Toast.makeText(
                                this@ChatGrupoActivity,
                                getString(R.string.chat_baixando),
                                Toast.LENGTH_SHORT
                            ).show()
                            abrir -> {
                                try {
                                    val i = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(url), mime.ifEmpty { "*/*" })
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    startActivity(i)
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        this@ChatGrupoActivity,
                                        getString(R.string.chat_nenhum_app),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            apagar -> apagarMensagem(msgId)
                        }
                    }.show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ChatGrupoActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
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
                Toast.makeText(this@ChatGrupoActivity, getString(R.string.chat_favoritado), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ChatGrupoActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun desfavoritar(msgId: String) {
        lifecycleScope.launch {
            try {
                db.collection("favoritos").whereEqualTo("usuarioEmail", emailUsuario)
                    .whereEqualTo("mensagemId", msgId).get().await()
                    .documents.forEach { it.reference.delete().await() }
                Toast.makeText(this@ChatGrupoActivity, getString(R.string.chat_removido_favoritos), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ChatGrupoActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
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
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.chat_nenhum_app), Toast.LENGTH_LONG).show()
        }
    }

    private fun apagarMensagem(msgId: String) {
        lifecycleScope.launch {
            try {
                db.collection("grupos").document(grupoId).collection("mensagens").document(msgId)
                    .update(
                        mapOf(
                            "apagada" to true, "texto" to "🚫 Mensagem apagada",
                            "fotoUrl" to null, "videoUrl" to null, "arquivoUrl" to null,
                            "nomeArquivo" to null, "tamanhoArquivo" to null,
                            "mimeType" to null, "respostaPara" to null
                        )
                    ).await()
                Toast.makeText(this@ChatGrupoActivity, getString(R.string.chat_msg_apagada_toast), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ChatGrupoActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
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

    private fun abrirInfoMensagem(msgId: String) {
        val msg = adapter.currentList
            .filterIsInstance<ItemChat.MensagemItem>()
            .find { it.mensagem.id == msgId }
            ?.mensagem

        if (msg == null) {
            Toast.makeText(this, getString(R.string.chat_msg_nao_encontrada), Toast.LENGTH_SHORT).show()
            return
        }

        val sdf = java.text.SimpleDateFormat(
            getString(R.string.chat_info_data_formato),
            java.util.Locale.getDefault()
        )

        val info = buildString {
            append(getString(R.string.chat_info_enviada, sdf.format(java.util.Date(msg.timestamp))) + "\n")
            append((if (msg.lida) getString(R.string.chat_info_lida_sim) else getString(R.string.chat_info_lida_nao)) + "\n")
            append(getString(R.string.chat_info_tipo, msg.tipo) + "\n")
            if (msg.nomeArquivo != null) {
                append(getString(R.string.chat_info_arquivo, msg.nomeArquivo) + "\n")
            }
            if (msg.tamanhoArquivo > 0) {
                append(getString(R.string.chat_info_tamanho, formatarTamanhoInfo(msg.tamanhoArquivo)) + "\n")
            }
            if (msg.respostaPara != null) {
                append(getString(R.string.chat_info_resposta_a, msg.respostaPara.nomeRemetente) + "\n")
            }
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.chat_info_titulo))
            .setMessage(info)
            .setPositiveButton(getString(R.string.chat_info_ok), null)
            .show()
    }

    private fun formatarTamanhoInfo(b: Long): String = when {
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