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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cloudinary.android.MediaManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ChatEquipeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""
    private var nomeUsuario: String = "Usuário"
    private var equipeId: String = ""

    private var membrosEquipeCache: List<String> = emptyList()

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
                    putExtra("chatTipo", "equipe")
                    putExtra("chatId", equipeId)
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
                    putExtra("chatTipo", "equipe")
                    putExtra("chatId", equipeId)
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
                    putExtra("chatTipo", "equipe")
                    putExtra("chatId", equipeId)
                }
                abrirPreview.launch(i)
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
            Toast.makeText(this, "Equipe não encontrada", Toast.LENGTH_SHORT).show(); finish(); return
        }

        val btnVoltar = findViewById<Button>(R.id.btnVoltarChatEquipe)
        val btnEnviar = findViewById<Button>(R.id.btnEnviarMensagemEquipe)
        val btnFoto = findViewById<Button>(R.id.btnEnviarFotoEquipe)
        val btnVideo = findViewById<Button>(R.id.btnEnviarVideoEquipe)
        val btnArquivo = findViewById<Button>(R.id.btnEnviarArquivoEquipe)
        val btnGrupos = findViewById<Button>(R.id.btnVerGrupos)
        val edtMensagem = findViewById<EditText>(R.id.edtMensagemEquipe)
        val txtNomeEquipe = findViewById<TextView>(R.id.txtNomeEquipeChat)
        val txtDigitando = findViewById<TextView>(R.id.txtDigitandoEquipe)
        val btnCancelarResposta = findViewById<Button>(R.id.btnCancelarRespostaEquipe)

        btnVoltar.setOnClickListener { finish() }
        btnGrupos.setOnClickListener {
            val i = Intent(this, InfoEquipeActivity::class.java)
            i.putExtra("equipeId", equipeId); startActivity(i)
        }

        lifecycleScope.launch {
            try {
                nomeUsuario = db.collection("usuarios").document(emailUsuario).get().await().getString("nome") ?: "Usuário"
                txtNomeEquipe.text = db.collection("equipes").document(equipeId).get().await().getString("nome") ?: "Equipe"
            } catch (e: Exception) { Log.e("CHAT_EQUIPE", "Erro: ${e.message}") }
        }

        carregarMembrosEquipe()

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
                collection = "chats_equipe",
                documentId = equipeId,
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

        recycler = findViewById(R.id.recyclerMensagensEquipe)
        adapter = MensagemAdapter(
            emailUsuario = emailUsuario,
            outroEmail = "",
            contexto = this,
            callbacks = object : MensagemAdapter.Callbacks {
                override fun onInfoMensagem(mensagem: Mensagem) {
                    val sdf = java.text.SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", java.util.Locale("pt", "BR"))
                    val info = buildString {
                        append("Enviada: ${sdf.format(java.util.Date(mensagem.timestamp))}\n")
                        append("Lida: ${if (mensagem.lida) "✅" else "❌"}\n")
                        append("Tipo: ${mensagem.tipo}\n")
                        if (mensagem.nomeArquivo != null) append("Arquivo: ${mensagem.nomeArquivo}\n")
                        if (mensagem.tamanhoArquivo > 0) append("Tamanho: ${mensagem.tamanhoArquivo} bytes\n")
                        if (mensagem.respostaPara != null) append("Resposta a: ${mensagem.respostaPara.nomeRemetente}\n")
                    }
                    androidx.appcompat.app.AlertDialog.Builder(this@ChatEquipeActivity)
                        .setTitle("ℹ️ Informações da mensagem")
                        .setMessage(info)
                        .setPositiveButton("OK", null)
                        .show()
                }

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
            collection = "chats_equipe",
            documentId = equipeId,
            pageSize = 50,
            onListaAtualizada = { todas, inseriuNoTopo ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    adapter.submitList(ItemChat.deMensagens(todas)) {
                        if (!inseriuNoTopo && adapter.itemCount > 0)
                            recycler.scrollToPosition(adapter.itemCount - 1)
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
                    adapter.submitList(ItemChat.deMensagens(mensagensAtuais)) {
                        if (estavaNoFim && adapter.itemCount > 0)
                            recycler.scrollToPosition(adapter.itemCount - 1)
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

    private fun carregarMembrosEquipe() {
        lifecycleScope.launch {
            try {
                val snap = db.collection("membros_equipe").whereEqualTo("equipeId", equipeId).get().await()
                membrosEquipeCache = snap.documents.mapNotNull { it.getString("email") }
                    .filter { it.isNotEmpty() && it != emailUsuario }
            } catch (e: Exception) { Log.e("CHAT_EQUIPE", "Erro membros: ${e.message}") }
        }
    }

    private fun montarUpdatesNaoLidas(): Map<String, Any> {
        return membrosEquipeCache.associate { email -> "naoLidas.$email" to FieldValue.increment(1) }
    }

    private fun marcarMensagensComoLidas() {
        if (equipeId.isEmpty()) return
        lifecycleScope.launch {
            try {
                val msgs = db.collection("chats_equipe").document(equipeId).collection("mensagens")
                    .whereEqualTo("lida", false).get().await()
                if (msgs.isEmpty) return@launch
                val batch = db.batch()
                msgs.documents.forEach { doc ->
                    if ((doc.getString("remetente") ?: "") != emailUsuario) batch.update(doc.reference, "lida", true)
                }
                batch.commit().await()
            } catch (e: Exception) { Log.e("CHAT_EQUIPE", "Erro: ${e.message}") }
        }
    }

    private suspend fun garantirCampoDigitando() {
        try {
            val docRef = db.collection("chats_equipe").document(equipeId)
            val doc = docRef.get().await()
            if (doc.exists() && !doc.contains("digitando")) {
                docRef.update("digitando", emptyMap<String, Long>()).await()
            }
        } catch (e: Exception) { Log.e("CHAT_EQUIPE", "Erro: ${e.message}") }
    }

    private fun cancelarResposta() {
        respostaAtiva = null
        findViewById<LinearLayout>(R.id.containerRespondendoEquipe).visibility = View.GONE
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
        findViewById<TextView>(R.id.txtRespondendoAEquipe).text = "Respondendo a $nomeRem"
        findViewById<TextView>(R.id.txtTextoRespondendoEquipe).text = when (tipo) {
            "foto" -> "📷 Foto"; "video" -> "🎥 Vídeo"; "arquivo" -> "📎 Arquivo"; else -> texto
        }
        findViewById<LinearLayout>(R.id.containerRespondendoEquipe).visibility = View.VISIBLE
        findViewById<EditText>(R.id.edtMensagemEquipe).requestFocus()
    }

    // ============================================================
    // ✅ CORRIGIDO: set + merge
    // ============================================================
    private fun enviarMensagem(texto: String) {
        lifecycleScope.launch {
            try {
                val ts = System.currentTimeMillis()
                val m = hashMapOf<String, Any>(
                    "remetente" to emailUsuario, "nomeRemetente" to nomeUsuario,
                    "texto" to texto, "tipo" to "texto", "timestamp" to ts, "lida" to false
                )
                adicionarResposta(m)
                val batch = db.batch()
                val msgRef = db.collection("chats_equipe").document(equipeId).collection("mensagens").document()
                batch.set(msgRef, m)
                val preview = hashMapOf<String, Any>(
                    "texto" to texto, "autorNome" to nomeUsuario, "tipo" to "texto", "timestamp" to ts
                )
                val chatUpdates = hashMapOf<String, Any>(
                    "ultimaMensagem" to texto, "ultimaMensagemPreview" to preview, "atualizadoEm" to ts
                )
                chatUpdates.putAll(montarUpdatesNaoLidas())
                // ✅ set com merge (permite criar se não existir)
                batch.set(
                    db.collection("chats_equipe").document(equipeId),
                    chatUpdates,
                    SetOptions.merge()
                )
                batch.commit().await()
                typingHelper?.limpar(); cancelarResposta()
            } catch (e: Exception) {
                Toast.makeText(this@ChatEquipeActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ============================================================
    // ✅ CORRIGIDO: set + merge
    // ============================================================
    private fun enviarFoto(uri: Uri, legenda: String = "") {
        Toast.makeText(this, "📤 Enviando foto...", Toast.LENGTH_SHORT).show()
        MediaManager.get().upload(uri).unsigned("fqb729sb").option("folder", "chats_equipe/")
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
                            val batch = db.batch()
                            val msgRef = db.collection("chats_equipe").document(equipeId).collection("mensagens").document()
                            batch.set(msgRef, m)
                            val preview = hashMapOf<String, Any>(
                                "texto" to legenda.ifEmpty { "Foto" }, "autorNome" to nomeUsuario,
                                "tipo" to "foto", "timestamp" to ts
                            )
                            val chatUpdates = hashMapOf<String, Any>(
                                "ultimaMensagem" to if (legenda.isEmpty()) "📷 Foto" else "📷 $legenda",
                                "ultimaMensagemPreview" to preview, "atualizadoEm" to ts
                            )
                            chatUpdates.putAll(montarUpdatesNaoLidas())
                            batch.set(
                                db.collection("chats_equipe").document(equipeId),
                                chatUpdates,
                                SetOptions.merge()
                            )
                            batch.commit().await()
                            Toast.makeText(this@ChatEquipeActivity, "✅ Foto enviada!", Toast.LENGTH_SHORT).show()
                            typingHelper?.limpar(); cancelarResposta()
                        } catch (e: Exception) {
                            Toast.makeText(this@ChatEquipeActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } }
                }
                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    runOnUiThread { Toast.makeText(this@ChatEquipeActivity, "Erro: ${error?.description}", Toast.LENGTH_LONG).show() }
                }
                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            }).dispatch()
    }

    // ============================================================
    // ✅ CORRIGIDO: set + merge
    // ============================================================
    private fun enviarVideo(uri: Uri, legenda: String = "") {
        Toast.makeText(this, "📤 Enviando vídeo...", Toast.LENGTH_SHORT).show()
        MediaManager.get().upload(uri).unsigned("fqb729sb")
            .option("resource_type", "video").option("folder", "chats_equipe_videos/")
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
                            val batch = db.batch()
                            val msgRef = db.collection("chats_equipe").document(equipeId).collection("mensagens").document()
                            batch.set(msgRef, m)
                            val preview = hashMapOf<String, Any>(
                                "texto" to legenda.ifEmpty { "Vídeo" }, "autorNome" to nomeUsuario,
                                "tipo" to "video", "timestamp" to ts
                            )
                            val chatUpdates = hashMapOf<String, Any>(
                                "ultimaMensagem" to if (legenda.isEmpty()) "🎥 Vídeo" else "🎥 $legenda",
                                "ultimaMensagemPreview" to preview, "atualizadoEm" to ts
                            )
                            chatUpdates.putAll(montarUpdatesNaoLidas())
                            batch.set(
                                db.collection("chats_equipe").document(equipeId),
                                chatUpdates,
                                SetOptions.merge()
                            )
                            batch.commit().await()
                            Toast.makeText(this@ChatEquipeActivity, "✅ Vídeo enviado!", Toast.LENGTH_SHORT).show()
                            typingHelper?.limpar(); cancelarResposta()
                        } catch (e: Exception) {
                            Toast.makeText(this@ChatEquipeActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } }
                }
                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    runOnUiThread { Toast.makeText(this@ChatEquipeActivity, "Erro: ${error?.description}", Toast.LENGTH_LONG).show() }
                }
                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            }).dispatch()
    }

    // ============================================================
    // ✅ CORRIGIDO: set + merge
    // ============================================================
    private fun enviarArquivo(uri: Uri, legenda: String = "") {
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
            .option("resource_type", "raw").option("folder", "chats_equipe_arquivos/")
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
                            val batch = db.batch()
                            val msgRef = db.collection("chats_equipe").document(equipeId).collection("mensagens").document()
                            batch.set(msgRef, m)
                            val preview = hashMapOf<String, Any>(
                                "texto" to legenda.ifEmpty { nF }, "autorNome" to nomeUsuario,
                                "tipo" to "arquivo", "timestamp" to ts
                            )
                            val chatUpdates = hashMapOf<String, Any>(
                                "ultimaMensagem" to if (legenda.isEmpty()) "📎 $nF" else "📎 $legenda",
                                "ultimaMensagemPreview" to preview, "atualizadoEm" to ts
                            )
                            chatUpdates.putAll(montarUpdatesNaoLidas())
                            batch.set(
                                db.collection("chats_equipe").document(equipeId),
                                chatUpdates,
                                SetOptions.merge()
                            )
                            batch.commit().await()
                            Toast.makeText(this@ChatEquipeActivity, "✅ Arquivo enviado!", Toast.LENGTH_SHORT).show()
                            typingHelper?.limpar(); cancelarResposta()
                        } catch (e: Exception) {
                            Toast.makeText(this@ChatEquipeActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } }
                }
                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    runOnUiThread { Toast.makeText(this@ChatEquipeActivity, "Erro: ${error?.description}", Toast.LENGTH_LONG).show() }
                }
                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            }).dispatch()
    }

    private fun mostrarOpcaoMensagem(msgId: String, texto: String, remetente: String, nomeRem: String, ehRem: Boolean) {
        lifecycleScope.launch {
            try {
                val jaFav = db.collection("favoritos").whereEqualTo("usuarioEmail", emailUsuario)
                    .whereEqualTo("mensagemId", msgId).limit(1).get().await().let { !it.isEmpty }
                val opcoes = mutableListOf("💬 Responder")
                opcoes.add(if (jaFav) "⭐ Remover dos favoritos" else "⭐ Favoritar")
                opcoes.add("ℹ️ Informações")
                if (ehRem) opcoes.add("🗑 Apagar para todos")

                AlertDialog.Builder(this@ChatEquipeActivity).setTitle("Opções")
                    .setItems(opcoes.toTypedArray()) { _, w ->
                        when (opcoes[w]) {
                            "💬 Responder" -> dispararResposta(msgId, texto, remetente, nomeRem, "texto")
                            "⭐ Favoritar" -> favoritar(msgId, texto, remetente, nomeRem, "texto")
                            "⭐ Remover dos favoritos" -> desfavoritar(msgId)
                            "ℹ️ Informações" -> abrirInfoMensagem(msgId)
                            "🗑 Apagar para todos" -> apagarMensagem(msgId)
                        }
                    }.show()
            } catch (e: Exception) { Toast.makeText(this@ChatEquipeActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
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
                val opcoes = mutableListOf("💬 Responder")
                opcoes.add(if (jaFav) "⭐ Remover dos favoritos" else "⭐ Favoritar")
                opcoes.add("ℹ️ Informações")
                opcoes.add("⬇ Baixar")
                if (tipo == "arquivo") opcoes.add("📂 Abrir")
                if (ehRem) opcoes.add("🗑 Apagar")

                AlertDialog.Builder(this@ChatEquipeActivity).setTitle("Opções")
                    .setItems(opcoes.toTypedArray()) { _, w ->
                        when (opcoes[w]) {
                            "💬 Responder" -> dispararResposta(msgId, texto, remetente, nomeRem, tipo)
                            "⭐ Favoritar" -> favoritar(msgId, texto, remetente, nomeRem, tipo)
                            "⭐ Remover dos favoritos" -> desfavoritar(msgId)
                            "ℹ️ Informações" -> abrirInfoMensagem(msgId)
                            "⬇ Baixar" -> Toast.makeText(this@ChatEquipeActivity, "⬇ Baixando...", Toast.LENGTH_SHORT).show()
                            "📂 Abrir" -> {
                                try {
                                    val i = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(url), mime.ifEmpty { "*/*" })
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    startActivity(i)
                                } catch (e: Exception) { Toast.makeText(this@ChatEquipeActivity, "Nenhum app", Toast.LENGTH_LONG).show() }
                            }
                            "🗑 Apagar" -> apagarMensagem(msgId)
                        }
                    }.show()
            } catch (e: Exception) { Toast.makeText(this@ChatEquipeActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun favoritar(msgId: String, texto: String, remetente: String, nomeRem: String, tipoMidia: String) {
        lifecycleScope.launch {
            try {
                db.collection("favoritos").add(hashMapOf(
                    "usuarioEmail" to emailUsuario, "mensagemId" to msgId, "chatId" to equipeId,
                    "tipoChat" to "equipe", "texto" to texto, "remetente" to remetente,
                    "nomeRemetente" to nomeRem, "tipoMidia" to tipoMidia,
                    "criadoEm" to System.currentTimeMillis()
                )).await()
                Toast.makeText(this@ChatEquipeActivity, "⭐ Favoritado", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(this@ChatEquipeActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun desfavoritar(msgId: String) {
        lifecycleScope.launch {
            try {
                db.collection("favoritos").whereEqualTo("usuarioEmail", emailUsuario)
                    .whereEqualTo("mensagemId", msgId).get().await()
                    .documents.forEach { it.reference.delete().await() }
                Toast.makeText(this@ChatEquipeActivity, "Removido", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(this@ChatEquipeActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
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

    private fun apagarMensagem(msgId: String) {
        lifecycleScope.launch {
            try {
                db.collection("chats_equipe").document(equipeId).collection("mensagens").document(msgId).delete().await()
                Toast.makeText(this@ChatEquipeActivity, "Apagada", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(this@ChatEquipeActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    // ============================================================
// ABRIR INFO DA MENSAGEM
// ============================================================
    private fun abrirInfoMensagem(msgId: String) {
        val msg = adapter.currentList
            .filterIsInstance<ItemChat.MensagemItem>()
            .find { it.mensagem.id == msgId }
            ?.mensagem

        if (msg == null) {
            Toast.makeText(this, "Mensagem não encontrada", Toast.LENGTH_SHORT).show()
            return
        }

        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", java.util.Locale("pt", "BR"))
        val info = buildString {
            append("Enviada: ${sdf.format(java.util.Date(msg.timestamp))}\n")
            append("Lida: ${if (msg.lida) "✅" else "❌"}\n")
            append("Tipo: ${msg.tipo}\n")
            if (msg.nomeArquivo != null) append("Arquivo: ${msg.nomeArquivo}\n")
            if (msg.tamanhoArquivo > 0) {
                append("Tamanho: ${formatarTamanhoInfo(msg.tamanhoArquivo)}\n")
            }
            if (msg.respostaPara != null) {
                append("Resposta a: ${msg.respostaPara.nomeRemetente}\n")
            }
        }
        AlertDialog.Builder(this)
            .setTitle("ℹ️ Informações da mensagem")
            .setMessage(info)
            .setPositiveButton("OK", null)
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