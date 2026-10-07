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
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
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
                    putExtra("chatTipo", "pv")
                    putExtra("chatId", chatId)
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
                    putExtra("chatTipo", "pv")
                    putExtra("chatId", chatId)
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
                    putExtra("chatTipo", "pv")
                    putExtra("chatId", chatId)
                }
                abrirPreview.launch(i)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""
        outroEmail = intent.getStringExtra("outroEmail")?.trim()?.lowercase() ?: ""
        chatId = intent.getStringExtra("chatId")?.trim() ?: ""

        if (emailUsuario.isEmpty()) {
            Toast.makeText(this, "Sessão expirada. Faça login novamente.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (outroEmail.isEmpty() && chatId.isEmpty()) {
            Toast.makeText(this, "Conversa inválida", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (chatId.isNotEmpty() && (outroEmail.isEmpty() || !outroEmail.contains("@"))) {
            lifecycleScope.launch {
                try {
                    val chatDoc = db.collection("chats").document(chatId).get().await()
                    if (!chatDoc.exists()) {
                        Toast.makeText(this@ChatActivity, "Conversa não encontrada", Toast.LENGTH_SHORT).show()
                        finish()
                        return@launch
                    }

                    val participantes = chatDoc.get("participantes") as? List<*>
                    val outro = participantes?.firstOrNull { it != emailUsuario } as? String

                    if (outro.isNullOrEmpty()) {
                        Toast.makeText(this@ChatActivity, "Conversa inválida", Toast.LENGTH_SHORT).show()
                        finish()
                        return@launch
                    }

                    outroEmail = outro.trim().lowercase()
                    Log.d("CHAT_DEBUG", "✅ outroEmail corrigido: '$outroEmail'")
                    if (!isFinishing && !isDestroyed) configurarUI()
                } catch (e: Exception) {
                    Log.e("CHAT", "Erro: ${e.message}")
                    Toast.makeText(this@ChatActivity, "Erro ao abrir conversa", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            return
        }

        if (chatId.isNotEmpty()) {
            configurarUI()
            return
        }

        lifecycleScope.launch {
            try {
                chatId = criarOuBuscarChat()
                if (!isFinishing && !isDestroyed) configurarUI()
            } catch (e: Exception) {
                Log.e("CHAT", "Erro ao criar chat: ${e.message}")
                Toast.makeText(this@ChatActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
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
        paginacaoHelper?.destruir()
        paginacaoHelper = null
    }

    // ============================================================
    // MARCAR COMO LIDAS — agora zera naoLidas no doc do chat
    // ============================================================
    private fun marcarMensagensComoLidas() {
        if (chatId.isEmpty()) return
        lifecycleScope.launch {
            try {
                val msgs = db.collection("chats").document(chatId).collection("mensagens")
                    .whereEqualTo("lida", false).get().await()

                if (msgs.isEmpty) {
                    // ✅ Zera mesmo sem mensagens
                    val chatDoc = db.collection("chats").document(chatId).get().await()
                    val naoLidasAtual = (chatDoc.get("naoLidas") as? Map<String, Any>) ?: emptyMap()
                    val naoLidasNovo = naoLidasAtual.toMutableMap()
                    naoLidasNovo[emailUsuario] = 0L

                    db.collection("chats").document(chatId)
                        .update("naoLidas", naoLidasNovo).await()

                    ChatResumoHelper.zerarNaoLidas(emailUsuario, chatId)
                    return@launch
                }

                val batch = db.batch()
                msgs.documents.forEach { doc ->
                    if ((doc.getString("remetente") ?: "") != emailUsuario) {
                        batch.update(doc.reference, "lida", true)
                    }
                }
                batch.commit().await()

                // ✅ Zera naoLidas no doc do chat
                val chatDoc = db.collection("chats").document(chatId).get().await()
                val naoLidasAtual = (chatDoc.get("naoLidas") as? Map<String, Any>) ?: emptyMap()
                val naoLidasNovo = naoLidasAtual.toMutableMap()
                naoLidasNovo[emailUsuario] = 0L

                db.collection("chats").document(chatId)
                    .update("naoLidas", naoLidasNovo).await()

                ChatResumoHelper.zerarNaoLidas(emailUsuario, chatId)
            } catch (e: Exception) {
                Log.e("CHAT", "Erro marcar como lidas: ${e.message}")
            }
        }
    }

    // ============================================================
    // CONFIGURAR UI
    // ============================================================
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
            if (outroEmail.isEmpty()) {
                Toast.makeText(this, "Não é possível ver o perfil", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val i = Intent(this, PerfilUsuarioActivity::class.java)
            i.putExtra("emailOutro", outroEmail)
            startActivity(i)
        }

        if (outroEmail.isNotEmpty()) {
            lifecycleScope.launch {
                try {
                    val u = db.collection("usuarios").document(outroEmail).get().await()
                    txtNomeOutro.text = u.getString("nome") ?: outroEmail
                } catch (e: Exception) {
                    txtNomeOutro.text = outroEmail
                }
            }
        } else {
            txtNomeOutro.text = "Conversa"
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

        recycler = findViewById(R.id.recyclerMensagens)
        adapter = MensagemAdapter(
            emailUsuario = emailUsuario,
            outroEmail = outroEmail,
            contexto = this,
            callbacks = object : MensagemAdapter.Callbacks {

                override fun onResponder(msgId: String, texto: String, remetente: String, tipo: String) {
                    dispararResposta(msgId, texto, remetente, tipo)
                }

                override fun onFotoClick(posicaoNaGaleria: Int, listaMidias: List<Pair<String, String>>) {
                    abrirGaleria(listaMidias, posicaoNaGaleria)
                }

                override fun onVideoClick(posicaoNaGaleria: Int, listaMidias: List<Pair<String, String>>) {
                    abrirGaleria(listaMidias, posicaoNaGaleria)
                }

                override fun onLongPressTexto(msgId: String, texto: String, remetente: String, ehRem: Boolean) {
                    mostrarOpcaoMensagem(msgId, texto, remetente, ehRem)
                }

                override fun onLongPressMidia(
                    tipo: String, url: String, nomeArq: String, mime: String,
                    msgId: String, ehRem: Boolean, texto: String, remetente: String
                ) {
                    mostrarMenuMidia(tipo, url, nomeArq, mime, msgId, ehRem, texto, remetente)
                }

                override fun onAbrirArquivo(url: String, mime: String, nome: String) {
                    abrirArquivoExterno(url, mime, nome)
                }
            }
        )

        layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
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
                if (primeiroVisivel in 0..3) {
                    paginacaoHelper?.carregarMaisAntigas()
                }
            }
        })

        paginacaoHelper = ChatPaginacaoHelper(
            collection = "chats",
            documentId = chatId,
            pageSize = 50,
            onListaAtualizada = { todas, inseriuNoTopo ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    adapter.submitList(ItemChat.deMensagens(todas)) {
                        if (!inseriuNoTopo && adapter.itemCount > 0) {
                            recycler.scrollToPosition(adapter.itemCount - 1)
                        }
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

    // ============================================================
    // RECARREGAR MENSAGENS
    // ============================================================
    private fun recarregarMensagens() {
        paginacaoHelper?.destruir()
        paginacaoHelper = null

        paginacaoHelper = ChatPaginacaoHelper(
            collection = "chats",
            documentId = chatId,
            pageSize = 50,
            onListaAtualizada = { todas, inseriuNoTopo ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    adapter.submitList(ItemChat.deMensagens(todas)) {
                        if (!inseriuNoTopo && adapter.itemCount > 0) {
                            recycler.scrollToPosition(adapter.itemCount - 1)
                        }
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
                "digitando" to emptyMap<String, Long>(),
                "naoLidas" to emptyMap<String, Long>()
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

    // ============================================================
    // ✅ ATUALIZAR naoLidas (usando Map, sem notação de ponto)
    // ============================================================
    private suspend fun incrementarNaoLidas(outro: String) {
        try {
            val chatDoc = db.collection("chats").document(chatId).get().await()
            val naoLidasAtual = (chatDoc.get("naoLidas") as? Map<String, Any>) ?: emptyMap()
            val naoLidasNovo = naoLidasAtual.toMutableMap()
            val valorAtual = (naoLidasNovo[outro] as? Number)?.toLong() ?: 0L
            naoLidasNovo[outro] = valorAtual + 1

            db.collection("chats").document(chatId)
                .update("naoLidas", naoLidasNovo).await()

            Log.d("CHAT_DEBUG", "✅ naoLidas atualizado: $naoLidasNovo")
        } catch (e: Exception) {
            Log.e("CHAT_DEBUG", "❌ Erro incrementar naoLidas: ${e.message}")
        }
    }

    // ============================================================
    // ✅ ENVIAR MENSAGEM DE TEXTO
    // ============================================================
    private fun enviarMensagem(texto: String) {
        lifecycleScope.launch {
            try {
                val ts = System.currentTimeMillis()
                val m = hashMapOf<String, Any>(
                    "remetente" to emailUsuario, "texto" to texto, "tipo" to "texto",
                    "timestamp" to ts, "lida" to false
                )
                adicionarResposta(m)

                // 1. Salva a mensagem
                val msgRef = db.collection("chats").document(chatId).collection("mensagens").document()
                msgRef.set(m).await()

                // 2. Preview + updated_at
                val preview = hashMapOf<String, Any>(
                    "texto" to texto,
                    "autorNome" to emailUsuario,
                    "tipo" to "texto",
                    "timestamp" to ts
                )
                db.collection("chats").document(chatId).update(
                    mapOf(
                        "ultimaMensagem" to texto,
                        "ultimaMensagemPreview" to preview,
                        "atualizadoEm" to ts
                    )
                ).await()

                // 3. Incrementa naoLidas do outro (via Map)
                incrementarNaoLidas(outroEmail)

                Log.d("CHAT_DEBUG", "=== ENVIAR MENSAGEM ===")
                Log.d("CHAT_DEBUG", "emailUsuario='$emailUsuario' outroEmail='$outroEmail' chatId='$chatId'")

                // 4. Atualiza resumo do OUTRO
                try {
                    ChatResumoHelper.atualizarResumo(
                        emailUsuario = outroEmail,
                        chatId = chatId,
                        nome = emailUsuario,
                        ultimaMsg = texto,
                        timestamp = ts,
                        tipo = "pv",
                        incrementarNaoLidas = true
                    )
                    Log.d("CHAT_DEBUG", "✅ Resumo do OUTRO atualizado")
                } catch (e: Exception) {
                    Log.e("CHAT_DEBUG", "❌ Erro resumo OUTRO: ${e.message}")
                }

                // 5. Atualiza resumo MEU
                try {
                    ChatResumoHelper.atualizarResumo(
                        emailUsuario = emailUsuario,
                        chatId = chatId,
                        nome = outroEmail,
                        ultimaMsg = texto,
                        timestamp = ts,
                        tipo = "pv",
                        incrementarNaoLidas = false
                    )
                    Log.d("CHAT_DEBUG", "✅ Resumo MEU atualizado")
                } catch (e: Exception) {
                    Log.e("CHAT_DEBUG", "❌ Erro resumo MEU: ${e.message}")
                }

                recarregarMensagens()

                typingHelper?.limpar()
                cancelarResposta()
            } catch (e: Exception) {
                Log.e("CHAT_DEBUG", "❌ Erro enviarMensagem: ${e.message}")
                Toast.makeText(this@ChatActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
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

    // ============================================================
    // ✅ ENVIAR FOTO
    // ============================================================
    private fun enviarFoto(uri: Uri, legenda: String = "") {
        Toast.makeText(this, "📤 Enviando foto...", Toast.LENGTH_SHORT).show()
        MediaManager.get().upload(uri).unsigned("fqb729sb").option("folder", "chats_pv/")
            .callback(object : com.cloudinary.android.callback.UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val url = resultData?.get("secure_url") as? String
                    if (url != null) runOnUiThread { lifecycleScope.launch {
                        try {
                            val ts = System.currentTimeMillis()
                            val m = hashMapOf<String, Any>(
                                "remetente" to emailUsuario, "texto" to legenda, "fotoUrl" to url,
                                "tipo" to "foto", "timestamp" to ts, "lida" to false
                            )
                            adicionarResposta(m)

                            val msgRef = db.collection("chats").document(chatId).collection("mensagens").document()
                            msgRef.set(m).await()

                            val preview = hashMapOf<String, Any>(
                                "texto" to legenda.ifEmpty { "Foto" },
                                "autorNome" to emailUsuario,
                                "tipo" to "foto",
                                "timestamp" to ts
                            )
                            val textoPreview = if (legenda.isEmpty()) "📷 Foto" else "📷 $legenda"
                            db.collection("chats").document(chatId).update(
                                mapOf(
                                    "ultimaMensagem" to textoPreview,
                                    "ultimaMensagemPreview" to preview,
                                    "atualizadoEm" to ts
                                )
                            ).await()

                            incrementarNaoLidas(outroEmail)

                            try {
                                ChatResumoHelper.atualizarResumo(
                                    emailUsuario = outroEmail, chatId = chatId, nome = emailUsuario,
                                    ultimaMsg = textoPreview, timestamp = ts, tipo = "pv",
                                    incrementarNaoLidas = true
                                )
                            } catch (e: Exception) { Log.e("CHAT_DEBUG", "Erro resumo OUTRO: ${e.message}") }

                            try {
                                ChatResumoHelper.atualizarResumo(
                                    emailUsuario = emailUsuario, chatId = chatId, nome = outroEmail,
                                    ultimaMsg = textoPreview, timestamp = ts, tipo = "pv",
                                    incrementarNaoLidas = false
                                )
                            } catch (e: Exception) { Log.e("CHAT_DEBUG", "Erro resumo MEU: ${e.message}") }

                            recarregarMensagens()
                            Toast.makeText(this@ChatActivity, "✅ Foto enviada!", Toast.LENGTH_SHORT).show()
                            typingHelper?.limpar()
                            cancelarResposta()
                        } catch (e: Exception) {
                            Log.e("CHAT_DEBUG", "Erro enviarFoto: ${e.message}")
                            Toast.makeText(this@ChatActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } }
                }
                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    runOnUiThread { Toast.makeText(this@ChatActivity, "Erro: ${error?.description}", Toast.LENGTH_LONG).show() }
                }
                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            }).dispatch()
    }

    // ============================================================
    // ✅ ENVIAR VÍDEO
    // ============================================================
    private fun enviarVideo(uri: Uri, legenda: String = "") {
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
                            val ts = System.currentTimeMillis()
                            val m = hashMapOf<String, Any>(
                                "remetente" to emailUsuario, "texto" to legenda, "videoUrl" to url,
                                "tipo" to "video", "timestamp" to ts, "lida" to false
                            )
                            adicionarResposta(m)

                            val msgRef = db.collection("chats").document(chatId).collection("mensagens").document()
                            msgRef.set(m).await()

                            val preview = hashMapOf<String, Any>(
                                "texto" to legenda.ifEmpty { "Vídeo" },
                                "autorNome" to emailUsuario,
                                "tipo" to "video",
                                "timestamp" to ts
                            )
                            val textoPreview = if (legenda.isEmpty()) "🎥 Vídeo" else "🎥 $legenda"
                            db.collection("chats").document(chatId).update(
                                mapOf(
                                    "ultimaMensagem" to textoPreview,
                                    "ultimaMensagemPreview" to preview,
                                    "atualizadoEm" to ts
                                )
                            ).await()

                            incrementarNaoLidas(outroEmail)

                            try {
                                ChatResumoHelper.atualizarResumo(
                                    emailUsuario = outroEmail, chatId = chatId, nome = emailUsuario,
                                    ultimaMsg = textoPreview, timestamp = ts, tipo = "pv",
                                    incrementarNaoLidas = true
                                )
                            } catch (e: Exception) { Log.e("CHAT_DEBUG", "Erro resumo OUTRO: ${e.message}") }

                            try {
                                ChatResumoHelper.atualizarResumo(
                                    emailUsuario = emailUsuario, chatId = chatId, nome = outroEmail,
                                    ultimaMsg = textoPreview, timestamp = ts, tipo = "pv",
                                    incrementarNaoLidas = false
                                )
                            } catch (e: Exception) { Log.e("CHAT_DEBUG", "Erro resumo MEU: ${e.message}") }

                            recarregarMensagens()
                            Toast.makeText(this@ChatActivity, "✅ Vídeo enviado!", Toast.LENGTH_SHORT).show()
                            typingHelper?.limpar()
                            cancelarResposta()
                        } catch (e: Exception) {
                            Log.e("CHAT_DEBUG", "Erro enviarVideo: ${e.message}")
                            Toast.makeText(this@ChatActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } }
                }
                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    runOnUiThread { Toast.makeText(this@ChatActivity, "Erro: ${error?.description}", Toast.LENGTH_LONG).show() }
                }
                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            }).dispatch()
    }

    // ============================================================
    // ✅ ENVIAR ARQUIVO
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
            .option("resource_type", "raw").option("folder", "chats_arquivos/")
            .callback(object : com.cloudinary.android.callback.UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val url = resultData?.get("secure_url") as? String
                    if (url != null) runOnUiThread { lifecycleScope.launch {
                        try {
                            val ts = System.currentTimeMillis()
                            val m = hashMapOf<String, Any>(
                                "remetente" to emailUsuario, "texto" to legenda, "arquivoUrl" to url,
                                "nomeArquivo" to nF, "tamanhoArquivo" to tF, "mimeType" to mF,
                                "tipo" to "arquivo", "timestamp" to ts, "lida" to false
                            )
                            adicionarResposta(m)

                            val msgRef = db.collection("chats").document(chatId).collection("mensagens").document()
                            msgRef.set(m).await()

                            val preview = hashMapOf<String, Any>(
                                "texto" to legenda.ifEmpty { nF },
                                "autorNome" to emailUsuario,
                                "tipo" to "arquivo",
                                "timestamp" to ts
                            )
                            val textoPreview = if (legenda.isEmpty()) "📎 $nF" else "📎 $legenda"
                            db.collection("chats").document(chatId).update(
                                mapOf(
                                    "ultimaMensagem" to textoPreview,
                                    "ultimaMensagemPreview" to preview,
                                    "atualizadoEm" to ts
                                )
                            ).await()

                            incrementarNaoLidas(outroEmail)

                            try {
                                ChatResumoHelper.atualizarResumo(
                                    emailUsuario = outroEmail, chatId = chatId, nome = emailUsuario,
                                    ultimaMsg = textoPreview, timestamp = ts, tipo = "pv",
                                    incrementarNaoLidas = true
                                )
                            } catch (e: Exception) { Log.e("CHAT_DEBUG", "Erro resumo OUTRO: ${e.message}") }

                            try {
                                ChatResumoHelper.atualizarResumo(
                                    emailUsuario = emailUsuario, chatId = chatId, nome = outroEmail,
                                    ultimaMsg = textoPreview, timestamp = ts, tipo = "pv",
                                    incrementarNaoLidas = false
                                )
                            } catch (e: Exception) { Log.e("CHAT_DEBUG", "Erro resumo MEU: ${e.message}") }

                            recarregarMensagens()
                            Toast.makeText(this@ChatActivity, "✅ Arquivo enviado!", Toast.LENGTH_SHORT).show()
                            typingHelper?.limpar()
                            cancelarResposta()
                        } catch (e: Exception) {
                            Log.e("CHAT_DEBUG", "Erro enviarArquivo: ${e.message}")
                            Toast.makeText(this@ChatActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } }
                }
                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    runOnUiThread { Toast.makeText(this@ChatActivity, "Erro: ${error?.description}", Toast.LENGTH_LONG).show() }
                }
                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            }).dispatch()
    }

    private fun mostrarOpcaoMensagem(msgId: String, texto: String, remetente: String, ehRem: Boolean) {
        lifecycleScope.launch {
            try {
                val jaFav = db.collection("favoritos")
                    .whereEqualTo("usuarioEmail", emailUsuario).whereEqualTo("mensagemId", msgId)
                    .limit(1).get().await().let { !it.isEmpty }
                val opcoes = mutableListOf("💬 Responder")
                opcoes.add(if (jaFav) "⭐ Remover dos favoritos" else "⭐ Favoritar")
                opcoes.add("ℹ️ Informações")
                if (ehRem) opcoes.add("🗑 Apagar para todos")

                AlertDialog.Builder(this@ChatActivity).setTitle("Opções")
                    .setItems(opcoes.toTypedArray()) { _, w ->
                        when (opcoes[w]) {
                            "💬 Responder" -> dispararResposta(msgId, texto, remetente, "texto")
                            "⭐ Favoritar" -> favoritar(msgId, texto, remetente, "texto")
                            "⭐ Remover dos favoritos" -> desfavoritar(msgId)
                            "ℹ️ Informações" -> abrirInfoMensagem(msgId)
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
                opcoes.add("ℹ️ Informações")
                opcoes.add("⬇ Baixar")
                if (tipo == "arquivo") opcoes.add("📂 Abrir")
                if (ehRem) opcoes.add("🗑 Apagar")

                AlertDialog.Builder(this@ChatActivity).setTitle("Opções")
                    .setItems(opcoes.toTypedArray()) { _, w ->
                        when (opcoes[w]) {
                            "💬 Responder" -> dispararResposta(msgId, texto, remetente, tipo)
                            "⭐ Favoritar" -> favoritar(msgId, texto, remetente, tipo)
                            "⭐ Remover dos favoritos" -> desfavoritar(msgId)
                            "ℹ️ Informações" -> abrirInfoMensagem(msgId)
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
                db.collection("chats").document(chatId).collection("mensagens").document(msgId)
                    .update(
                        mapOf(
                            "apagada" to true,
                            "texto" to "🚫 Mensagem apagada",
                            "fotoUrl" to null,
                            "videoUrl" to null,
                            "arquivoUrl" to null,
                            "nomeArquivo" to null,
                            "tamanhoArquivo" to null,
                            "mimeType" to null,
                            "respostaPara" to null
                        )
                    ).await()
                Toast.makeText(this@ChatActivity, "Mensagem apagada", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

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