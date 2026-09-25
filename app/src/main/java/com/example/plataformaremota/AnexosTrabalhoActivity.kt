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
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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

class AnexosTrabalhoActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""
    private var nomeUsuario: String = "Usuario"
    private var trabalhoId: String = ""
    private var tituloTrabalho: String = "Trabalho"
    private var equipeId: String = ""

    // Launcher para selecionar arquivo
    private val selecionarArquivo = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) fazerUpload(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_anexos_trabalho)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""
        trabalhoId = intent.getStringExtra("trabalhoId") ?: ""
        tituloTrabalho = intent.getStringExtra("tituloTrabalho") ?: "Trabalho"

        if (trabalhoId.isEmpty()) {
            Toast.makeText(this, "Trabalho nao encontrado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val btnVoltar = findViewById<Button>(R.id.btnVoltarAnexos)
        btnVoltar.setOnClickListener { finish() }

        val txtTitulo = findViewById<TextView>(R.id.txtTituloTrabalhoAnexos)
        txtTitulo.text = tituloTrabalho

        val btnAdicionar = findViewById<Button>(R.id.btnAdicionarAnexo)
        btnAdicionar.setOnClickListener { abrirSeletorArquivo() }

        // Carrega o nome do usuario e a equipeId
        lifecycleScope.launch {
            try {
                val userDoc = db.collection("usuarios").document(emailUsuario).get().await()
                nomeUsuario = userDoc.getString("nome") ?: "Usuario"

                val trabalhoDoc = db.collection("trabalhos").document(trabalhoId).get().await()
                equipeId = trabalhoDoc.getString("equipeId") ?: ""
            } catch (e: Exception) {
                Log.e("ANEXOS", "Erro ao carregar dados: ${e.message}")
            }
        }

        carregarAnexos()
    }

    // ============================================================
    // CARREGAR LISTA DE ANEXOS
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
                    val nome = doc.getString("nome") ?: "arquivo"
                    val url = doc.getString("url") ?: ""
                    val tipo = doc.getString("tipo") ?: "outro"
                    val mimeType = doc.getString("mimeType") ?: "application/octet-stream"
                    val tamanho = doc.getLong("tamanho") ?: 0L
                    val enviadoPor = doc.getString("enviadoPor") ?: ""
                    val nomeEnviadoPor = doc.getString("nomeEnviadoPor") ?: "Usuario"
                    val enviadoEm = doc.getLong("enviadoEm") ?: 0L

                    val view = inflater.inflate(R.layout.item_anexo, container, false)

                    val cardIcone = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardIconeAnexo)
                    val txtIcone = view.findViewById<TextView>(R.id.txtIconeAnexo)
                    val txtNome = view.findViewById<TextView>(R.id.txtNomeAnexo)
                    val txtInfo = view.findViewById<TextView>(R.id.txtInfoAnexo)
                    val txtData = view.findViewById<TextView>(R.id.txtDataAnexo)
                    val btnMenu = view.findViewById<Button>(R.id.btnMenuAnexo)

                    txtNome.text = nome
                    txtInfo.text = "${AnexoHelper.formatarTamanho(tamanho)} - por $nomeEnviadoPor"
                    txtData.text = formatarDataRelativa(enviadoEm)

                    configurarIcone(cardIcone, txtIcone, tipo)

                    // Clique no card = abrir/baixar
                    view.setOnClickListener {
                        abrirOuBaixar(url, mimeType, nome, tipo)
                    }

                    // Menu de acoes
                    btnMenu.setOnClickListener {
                        val podeRemover = enviadoPor == emailUsuario
                        mostrarMenuAnexo(anexoId, url, mimeType, nome, tipo, podeRemover)
                    }

                    container.addView(view)
                }

            } catch (e: Exception) {
                Log.e("ANEXOS", getString(R.string.erro_generico, e.message ?: ""))
            }
        }
    }

    // ============================================================
    // CONFIGURAR ICONE POR TIPO
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
    // SELECIONAR ARQUIVO
    // ============================================================
    private fun abrirSeletorArquivo() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        selecionarArquivo.launch(intent)
    }

    // ============================================================
    // FAZER UPLOAD
    // ============================================================
    private fun fazerUpload(uri: Uri) {
        val containerProgresso = findViewById<LinearLayout>(R.id.containerProgresso)
        val txtStatus = findViewById<TextView>(R.id.txtProgressoStatus)
        val progressBar = findViewById<ProgressBar>(R.id.progressUpload)

        containerProgresso.visibility = View.VISIBLE
        txtStatus.text = "Enviando..."
        progressBar.progress = 0

        AnexoHelper.uploadAnexo(
            context = this,
            trabalhoId = trabalhoId,
            uri = uri,
            emailUsuario = emailUsuario,
            nomeUsuario = nomeUsuario,
            onProgress = { progresso ->
                runOnUiThread {
                    progressBar.progress = (progresso * 100).toInt()
                }
            },
            onSucesso = { anexoId ->
                runOnUiThread {
                    containerProgresso.visibility = View.GONE
                    Toast.makeText(this, "Anexo adicionado", Toast.LENGTH_SHORT).show()
                    carregarAnexos()
                    notificarMembros()
                }
            },
            onErro = { erro ->
                runOnUiThread {
                    containerProgresso.visibility = View.GONE
                    Toast.makeText(
                        this,
                        getString(R.string.erro_generico, erro),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    // ============================================================
    // NOTIFICAR MEMBROS DA EQUIPE
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
                        titulo = "Novo anexo em $tituloTrabalho",
                        mensagem = "$nomeUsuario adicionou um anexo",
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
    // MENU DE ACOES DO ANEXO
    // ============================================================
    private fun mostrarMenuAnexo(
        anexoId: String,
        url: String,
        mimeType: String,
        nome: String,
        tipo: String,
        podeRemover: Boolean
    ) {
        val opcoes = mutableListOf<String>()
        opcoes.add("Abrir")
        opcoes.add("Baixar")
        if (podeRemover) opcoes.add("Remover")

        AlertDialog.Builder(this)
            .setTitle(nome)
            .setItems(opcoes.toTypedArray()) { _, which ->
                when (opcoes[which]) {
                    "Abrir" -> abrirArquivo(url, mimeType, nome)
                    "Baixar" -> baixarAnexo(url, nome, tipo)
                    "Remover" -> confirmarRemocao(anexoId, nome)
                }
            }
            .show()
    }

    // ============================================================
    // ABRIR / BAIXAR
    // ============================================================
    private fun abrirOuBaixar(url: String, mimeType: String, nome: String, tipo: String) {
        // Tipos visualizaveis: abre direto. Outros: baixa.
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
            Toast.makeText(this, "Nenhum app para abrir '$nome'", Toast.LENGTH_LONG).show()
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
            request.setDescription("Baixando do CTR...")
            request.allowScanningByMediaScanner()
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(pasta, nomeFinal)

            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            Toast.makeText(this, "Baixando...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.erro_generico, e.message ?: ""),
                Toast.LENGTH_LONG
            ).show()
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

    // ============================================================
    // REMOVER
    // ============================================================
    private fun confirmarRemocao(anexoId: String, nome: String) {
        AlertDialog.Builder(this)
            .setTitle("Remover anexo")
            .setMessage("Deseja remover '$nome'?")
            .setPositiveButton("Remover") { _, _ ->
                lifecycleScope.launch {
                    val ok = AnexoHelper.removerAnexo(trabalhoId, anexoId)
                    if (ok) {
                        Toast.makeText(
                            this@AnexosTrabalhoActivity,
                            "Anexo removido",
                            Toast.LENGTH_SHORT
                        ).show()
                        carregarAnexos()
                    } else {
                        Toast.makeText(
                            this@AnexosTrabalhoActivity,
                            "Erro ao remover",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    // ============================================================
    // FORMATAR DATA RELATIVA
    // ============================================================
    private fun formatarDataRelativa(timestamp: Long): String {
        if (timestamp == 0L) return ""

        val agora = System.currentTimeMillis()
        val diff = agora - timestamp

        return when {
            diff < 60_000 -> "Agora"
            diff < 3_600_000 -> {
                val min = TimeUnit.MILLISECONDS.toMinutes(diff)
                "Ha ${min}min"
            }
            diff < 86_400_000 -> {
                val horas = TimeUnit.MILLISECONDS.toHours(diff)
                "Ha ${horas}h"
            }
            diff < 604_800_000 -> {
                val dias = TimeUnit.MILLISECONDS.toDays(diff)
                "Ha ${dias}d"
            }
            else -> {
                val formato = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
                formato.format(Date(timestamp))
            }
        }
    }
}