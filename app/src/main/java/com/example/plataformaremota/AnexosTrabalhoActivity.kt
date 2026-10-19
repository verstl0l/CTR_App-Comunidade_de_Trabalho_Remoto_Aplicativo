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
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AnexosTrabalhoActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""
    private var nomeUsuario: String = ""
    private var trabalhoId: String = ""
    private var tituloTrabalho: String = ""
    private var equipeId: String = ""

    // ✅ Controle de quem pode anexar
    private var podeAnexar: Boolean = false

    // ✅ NOVO: dados do trabalho pra checar visibilidade de anexos privados
    private var criadorTrabalho: String = ""
    private var participanteTrabalho: String = ""
    private var donoEquipe: String = ""

    // Launcher
    private val selecionarArquivo = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) perguntarPrivacidade(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_anexos_trabalho)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""
        trabalhoId = intent.getStringExtra("trabalhoId") ?: ""
        tituloTrabalho = intent.getStringExtra("tituloTrabalho") ?: getString(R.string.anexos_trabalho_padrao)

        nomeUsuario = getString(R.string.usuario_padrao)

        if (trabalhoId.isEmpty()) {
            Toast.makeText(this, getString(R.string.anexos_nao_encontrado), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val btnVoltar = findViewById<Button>(R.id.btnVoltarAnexos)
        btnVoltar.setOnClickListener { finish() }

        val txtTitulo = findViewById<TextView>(R.id.txtTituloTrabalhoAnexos)
        txtTitulo.text = tituloTrabalho

        val btnAdicionar = findViewById<Button>(R.id.btnAdicionarAnexo)

        //   Botão para ir aos Comentários
        val btnIrParaComentarios = findViewById<ImageButton>(R.id.btnIrParaComentarios)
        btnIrParaComentarios.setOnClickListener {
            val intent = Intent(this, ComentariosTrabalhoActivity::class.java)
            intent.putExtra("trabalhoId", trabalhoId)
            intent.putExtra("tituloTrabalho", tituloTrabalho)
            startActivity(intent)
            finish()
        }

        lifecycleScope.launch {
            try {
                val userDoc = db.collection("usuarios").document(emailUsuario).get().await()
                nomeUsuario = userDoc.getString("nome") ?: getString(R.string.usuario_padrao)

                val trabalhoDoc = db.collection("trabalhos").document(trabalhoId).get().await()
                equipeId = trabalhoDoc.getString("equipeId") ?: ""

                criadorTrabalho = trabalhoDoc.getString("criadorEmail") ?: ""
                participanteTrabalho = trabalhoDoc.getString("participanteEmail")
                    ?: trabalhoDoc.getString("responsavelEmail")
                            ?: ""

                // Dono da equipe
                try {
                    val eqDoc = db.collection("equipes").document(equipeId).get().await()
                    donoEquipe = eqDoc.getString("criadorEmail") ?: ""
                } catch (e: Exception) {
                    Log.e("ANEXOS", "Erro ao carregar dono: ${e.message}")
                }

                podeAnexar = criadorTrabalho.equals(emailUsuario, ignoreCase = true) ||
                        participanteTrabalho.equals(emailUsuario, ignoreCase = true)

                if (podeAnexar) {
                    btnAdicionar.visibility = View.VISIBLE
                    btnAdicionar.setOnClickListener { abrirSeletorArquivo() }
                } else {
                    btnAdicionar.visibility = View.GONE
                }

            } catch (e: Exception) {
                Log.e("ANEXOS", "Erro ao carregar dados: ${e.message}")
            }
        }

        carregarAnexos()
    }

    // ============================================================
    // FLUXO DE PRIVACIDADE (2 dialogs)
    // ============================================================
    private fun perguntarPrivacidade(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.anexo_privacidade_titulo))
            .setMessage(getString(R.string.anexo_privacidade_msg))
            .setPositiveButton(getString(R.string.anexo_privacidade_sim)) { _, _ ->
                confirmarPrivado(uri)
            }
            .setNegativeButton(getString(R.string.anexo_privacidade_nao)) { _, _ ->
                fazerUpload(uri, privado = false)
            }
            .show()
    }

    private fun confirmarPrivado(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.anexo_privado_ok_titulo))
            .setMessage(getString(R.string.anexo_privado_ok_msg))
            .setPositiveButton(getString(R.string.anexo_privado_ok_confirmar)) { _, _ ->
                fazerUpload(uri, privado = true)
            }
            .setNegativeButton(getString(R.string.cancelar), null)
            .show()
    }

    // ============================================================
    // CARREGAR LISTA
    // ============================================================
    private fun carregarAnexos() {
        val container = findViewById<LinearLayout>(R.id.containerAnexos)
        val containerVazio = findViewById<LinearLayout>(R.id.containerVazio)
        container.removeAllViews()

        lifecycleScope.launch {
            try {
                val anexos = db.collection("trabalhos").document(trabalhoId)
                    .collection("anexos")
                    .orderBy("enviadoEm", Query.Direction.DESCENDING)
                    .get()
                    .await()

                if (anexos.isEmpty) {
                    containerVazio.visibility = View.VISIBLE
                    return@launch
                }

                containerVazio.visibility = View.GONE

                val inflater = LayoutInflater.from(this@AnexosTrabalhoActivity)

                anexos.documents.forEach { doc ->
                    val anexoId = doc.id
                    val nome = doc.getString("nome") ?: getString(R.string.anexo_nome_arquivo_padrao)
                    val url = doc.getString("url") ?: ""
                    val tipo = doc.getString("tipo") ?: "outro"
                    val mimeType = doc.getString("mimeType") ?: "application/octet-stream"
                    val tamanho = doc.getLong("tamanho") ?: 0L
                    val enviadoPor = doc.getString("enviadoPor") ?: ""
                    val nomeEnviadoPor = doc.getString("nomeEnviadoPor") ?: getString(R.string.usuario_padrao)
                    val enviadoEm = doc.getLong("enviadoEm") ?: 0L
                    val privado = doc.getBoolean("privado") ?: false

                    // ✅ Lógica de visibilidade
                    val podeVerArquivo = podeVerAnexo(
                        enviadoPor = enviadoPor,
                        privado = privado
                    )

                    val view = inflater.inflate(R.layout.item_anexo, container, false)

                    val cardIcone = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardIconeAnexo)
                    val txtIcone = view.findViewById<TextView>(R.id.txtIconeAnexo)
                    val txtNome = view.findViewById<TextView>(R.id.txtNomeAnexo)
                    val txtInfo = view.findViewById<TextView>(R.id.txtInfoAnexo)
                    val txtData = view.findViewById<TextView>(R.id.txtDataAnexo)
                    val btnMenu = view.findViewById<Button>(R.id.btnMenuAnexo)

                    if (podeVerArquivo) {
                        // Mostra o arquivo normal
                        txtNome.text = nome
                        txtInfo.text = getString(
                            R.string.anexo_info_formato,
                            AnexoHelper.formatarTamanho(tamanho),
                            nomeEnviadoPor
                        )
                        txtData.text = formatarDataRelativa(enviadoEm)
                        configurarIcone(cardIcone, txtIcone, tipo)

                        view.setOnClickListener {
                            abrirOuBaixar(url, mimeType, nome, tipo)
                        }

                        btnMenu.setOnClickListener {
                            val podeRemover = enviadoPor == emailUsuario || podeAnexar
                            mostrarMenuAnexo(anexoId, url, mimeType, nome, tipo, podeRemover)
                        }
                    } else {
                        // Arquivo privado de terceiro — esconde o conteúdo
                        txtNome.text = getString(R.string.anexo_privado_nome)
                        txtInfo.text = getString(R.string.anexo_privado_info, nomeEnviadoPor)
                        txtData.text = formatarDataRelativa(enviadoEm)

                        // Ícone de "cadeado"
                        txtIcone.text = "🔒"
                        cardIcone.setCardBackgroundColor(
                            ContextCompat.getColor(this@AnexosTrabalhoActivity, R.color.bg_surface_hover)
                        )
                        txtIcone.setTextColor(
                            ContextCompat.getColor(this@AnexosTrabalhoActivity, R.color.text_secondary)
                        )

                        view.setOnClickListener {
                            Toast.makeText(
                                this@AnexosTrabalhoActivity,
                                getString(R.string.anexo_privado_aviso),
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        btnMenu.visibility = View.GONE
                    }

                    container.addView(view)
                }

            } catch (e: Exception) {
                Log.e("ANEXOS", getString(R.string.erro_generico, e.message ?: ""))
            }
        }
    }

    // ============================================================
    // REGRA DE VISIBILIDADE
    // ============================================================
    private fun podeVerAnexo(enviadoPor: String, privado: Boolean): Boolean {
        if (!privado) return true // não-privado: todos veem

        // Privado: quem enviou sempre vê
        if (enviadoPor.equals(emailUsuario, ignoreCase = true)) return true

        // Dono da equipe sempre vê
        if (donoEquipe.equals(emailUsuario, ignoreCase = true)) return true

        // Criador do trabalho vê
        if (criadorTrabalho.equals(emailUsuario, ignoreCase = true)) return true

        // Participante do trabalho vê
        if (participanteTrabalho.equals(emailUsuario, ignoreCase = true)) return true

        return false
    }

    // ============================================================
    // CONFIGURAR ICONE
    // ============================================================
    private fun configurarIcone(
        cardIcone: com.google.android.material.card.MaterialCardView,
        txtIcone: TextView,
        tipo: String
    ) {
        val texto = when (tipo) {
            "pdf" -> "PDF"
            "imagem" -> "IMG"
            "video" -> "VID"
            "documento" -> "DOC"
            "planilha" -> "XLS"
            "apresentacao" -> "PPT"
            "audio" -> "AUD"
            "compactado" -> "ZIP"
            "texto" -> "TXT"
            else -> "ARQ"
        }

        txtIcone.text = texto
        cardIcone.setCardBackgroundColor(
            ContextCompat.getColor(this@AnexosTrabalhoActivity, R.color.bg_surface_hover)
        )
        txtIcone.setTextColor(
            ContextCompat.getColor(this@AnexosTrabalhoActivity, R.color.accent)
        )
    }

    // ============================================================
    // SELECIONAR / FAZER UPLOAD
    // ============================================================
    private fun abrirSeletorArquivo() {
        if (!podeAnexar) {
            Toast.makeText(this, getString(R.string.anexos_sem_permissao), Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        selecionarArquivo.launch(intent)
    }

    private fun fazerUpload(uri: Uri, privado: Boolean) {
        if (!podeAnexar) {
            Toast.makeText(this, getString(R.string.anexos_sem_permissao), Toast.LENGTH_SHORT).show()
            return
        }

        val containerProgresso = findViewById<LinearLayout>(R.id.containerProgresso)
        val txtStatus = findViewById<TextView>(R.id.txtProgressoStatus)
        val progressBar = findViewById<ProgressBar>(R.id.progressUpload)

        containerProgresso.visibility = View.VISIBLE
        txtStatus.text = getString(R.string.anexos_enviando)
        progressBar.progress = 0

        AnexoHelper.uploadAnexo(
            context = this,
            trabalhoId = trabalhoId,
            uri = uri,
            emailUsuario = emailUsuario,
            nomeUsuario = nomeUsuario,
            privado = privado,  // ✅ NOVO
            onProgress = { progresso ->
                runOnUiThread {
                    progressBar.progress = (progresso * 100).toInt()
                }
            },
            onSucesso = { _ ->
                runOnUiThread {
                    containerProgresso.visibility = View.GONE
                    Toast.makeText(this, getString(R.string.anexos_adicionado), Toast.LENGTH_SHORT).show()
                    carregarAnexos()
                    notificarMembros()
                }
            },
            onErro = { erro ->
                runOnUiThread {
                    containerProgresso.visibility = View.GONE
                    Toast.makeText(this, getString(R.string.erro_generico, erro), Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    // ============================================================
    // NOTIFICAR
    // ============================================================
    private fun notificarMembros() {
        if (equipeId.isEmpty()) return

        lifecycleScope.launch {
            try {
                val membros = db.collection("membros_equipe")
                    .whereEqualTo("equipeId", equipeId)
                    .get()
                    .await()

                val emails = membros.documents
                    .mapNotNull { it.getString("email") }
                    .filter { it != emailUsuario }

                emails.forEach { email ->
                    NotificacaoHelper.criar(
                        destinatario = email,
                        tipo = "novo_anexo",
                        titulo = getString(R.string.anexo_notif_titulo, tituloTrabalho),
                        mensagem = getString(R.string.anexo_notif_msg, nomeUsuario),
                        referenciaId = trabalhoId,
                        referenciaTipo = "trabalho",
                        remetente = emailUsuario,
                        nomeRemetente = nomeUsuario
                    )
                }
            } catch (e: Exception) {
                Log.e("ANEXOS", "Erro ao notificar: ${e.message}")
            }
        }
    }

    // ============================================================
    // MENU / ABRIR / BAIXAR / REMOVER
    // ============================================================
    private fun mostrarMenuAnexo(
        anexoId: String,
        url: String,
        mimeType: String,
        nome: String,
        tipo: String,
        podeRemover: Boolean
    ) {
        val abrir = getString(R.string.anexo_acao_abrir)
        val baixar = getString(R.string.anexo_acao_baixar)
        val remover = getString(R.string.anexo_acao_remover)

        val opcoes = mutableListOf<String>()
        opcoes.add(abrir)
        opcoes.add(baixar)
        if (podeRemover) opcoes.add(remover)

        AlertDialog.Builder(this)
            .setTitle(nome)
            .setItems(opcoes.toTypedArray()) { _, which ->
                when (opcoes[which]) {
                    abrir -> abrirArquivo(url, mimeType, nome)
                    baixar -> baixarAnexo(url, nome, tipo)
                    remover -> confirmarRemocao(anexoId, nome)
                }
            }
            .show()
    }

    private fun abrirOuBaixar(url: String, mimeType: String, nome: String, tipo: String) {
        if (tipo in listOf("imagem", "video", "pdf")) {
            abrirArquivo(url, mimeType, nome)
        } else {
            baixarAnexo(url, nome, tipo)
        }
    }

    private fun abrirArquivo(url: String, mimeType: String, nome: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(url), mimeType.ifEmpty { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.anexo_sem_app, nome), Toast.LENGTH_LONG).show()
        }
    }

    private fun baixarAnexo(url: String, nome: String, tipo: String) {
        try {
            val pasta = when (tipo) {
                "imagem" -> Environment.DIRECTORY_PICTURES
                "video" -> Environment.DIRECTORY_MOVIES
                else -> Environment.DIRECTORY_DOWNLOADS
            }

            val nomeFinal = if (nome.contains(".")) nome else "$nome.${extensaoPorTipo(tipo)}"

            val request = DownloadManager.Request(Uri.parse(url))
            request.setTitle(nomeFinal)
            request.setDescription(getString(R.string.anexo_baixando_do_ctr))
            request.allowScanningByMediaScanner()
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(pasta, nomeFinal)

            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            Toast.makeText(this, getString(R.string.anexo_baixando), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.erro_generico, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun extensaoPorTipo(tipo: String): String {
        return when (tipo) {
            "pdf" -> "pdf"
            "imagem" -> "jpg"
            "video" -> "mp4"
            "documento" -> "docx"
            "planilha" -> "xlsx"
            "apresentacao" -> "pptx"
            "audio" -> "mp3"
            "compactado" -> "zip"
            "texto" -> "txt"
            else -> "bin"
        }
    }

    private fun confirmarRemocao(anexoId: String, nome: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.anexo_remover_titulo))
            .setMessage(getString(R.string.anexo_remover_msg, nome))
            .setPositiveButton(getString(R.string.anexo_acao_remover)) { _, _ ->
                lifecycleScope.launch {
                    val ok = AnexoHelper.removerAnexo(trabalhoId, anexoId)
                    if (ok) {
                        Toast.makeText(this@AnexosTrabalhoActivity, getString(R.string.anexo_removido), Toast.LENGTH_SHORT).show()
                        carregarAnexos()
                    } else {
                        Toast.makeText(this@AnexosTrabalhoActivity, getString(R.string.anexo_erro_remover), Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    // ============================================================
    // FORMATAR DATA
    // ============================================================
    private fun formatarDataRelativa(timestamp: Long): String {
        if (timestamp == 0L) return ""

        val agora = System.currentTimeMillis()
        val diff = agora - timestamp

        return when {
            diff < 60_000 -> getString(R.string.tempo_agora)
            diff < 3_600_000 -> {
                val min = TimeUnit.MILLISECONDS.toMinutes(diff)
                getString(R.string.tempo_ha_min, min)
            }
            diff < 86_400_000 -> {
                val horas = TimeUnit.MILLISECONDS.toHours(diff)
                getString(R.string.tempo_ha_horas, horas)
            }
            diff < 604_800_000 -> {
                val dias = TimeUnit.MILLISECONDS.toDays(diff)
                getString(R.string.tempo_ha_dias, dias)
            }
            else -> {
                val formato = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                formato.format(Date(timestamp))
            }
        }
    }
}