package com.example.plataformaremota

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Mostra os detalhes de um trabalho do histórico, incluindo
 * todos os comentários e anexos (read-only).
 */
class HistoricoTrabalhoDetalhesActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""
    private var equipeId: String = ""
    private var trabalhoId: String = ""

    private var carregando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historico_trabalho_detalhes)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        emailUsuario = auth.currentUser?.email
            ?: prefs.getString(KEY_EMAIL_USUARIO, "")
                    ?: ""

        equipeId = intent.getStringExtra("equipeId") ?: ""
        trabalhoId = intent.getStringExtra("trabalhoId") ?: ""

        if (equipeId.isEmpty() || trabalhoId.isEmpty()) {
            Toast.makeText(this, getString(R.string.anexos_nao_encontrado), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<Button>(R.id.btnVoltarDetalhesTrabalho).setOnClickListener { finish() }

        carregarDados()
    }

    private fun carregarDados() {
        if (carregando) return
        carregando = true

        val txtTitulo = findViewById<TextView>(R.id.txtTituloTrabalhoDetalhes)
        val txtCategoria = findViewById<TextView>(R.id.txtCategoriaTrabalhoDetalhes)
        val txtDescricao = findViewById<TextView>(R.id.txtDescricaoTrabalhoDetalhes)
        val txtStatus = findViewById<TextView>(R.id.txtStatusTrabalhoDetalhes)
        val txtPrazo = findViewById<TextView>(R.id.txtPrazoTrabalhoDetalhes)
        val txtQtdComentarios = findViewById<TextView>(R.id.txtQtdComentariosDetalhes)
        val txtQtdAnexos = findViewById<TextView>(R.id.txtQtdAnexosDetalhes)
        val containerComentarios = findViewById<LinearLayout>(R.id.containerComentariosHistorico)
        val containerAnexos = findViewById<LinearLayout>(R.id.containerAnexosHistorico)
        val vazioComentarios = findViewById<LinearLayout>(R.id.containerVazioComentariosHistorico)
        val vazioAnexos = findViewById<LinearLayout>(R.id.containerVazioAnexosHistorico)

        lifecycleScope.launch {
            try {
                val doc = db.collection("historico_equipes")
                    .document(emailUsuario)
                    .collection("equipes")
                    .document(equipeId)
                    .collection("trabalhos")
                    .document(trabalhoId)
                    .get()
                    .await()

                if (!doc.exists()) {
                    Toast.makeText(
                        this@HistoricoTrabalhoDetalhesActivity,
                        getString(R.string.anexos_nao_encontrado),
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                    return@launch
                }

                val titulo = doc.getString("titulo")
                    ?: getString(R.string.meus_trabalhos_sem_titulo)
                val descricao = doc.getString("descricao")
                    ?: getString(R.string.meus_trabalhos_sem_descricao)
                val status = doc.getString("status") ?: "pendente"
                val prazo = doc.getString("prazo")
                    ?: getString(R.string.meus_trabalhos_sem_prazo)
                val categoria = doc.getString("categoria") ?: ""

                txtTitulo.text = titulo
                txtDescricao.text = descricao
                txtPrazo.text = getString(R.string.meus_trabalhos_entrega_formatado, prazo)

                if (categoria.isNotEmpty()) {
                    txtCategoria.text = categoria
                    txtCategoria.visibility = View.VISIBLE
                } else {
                    txtCategoria.visibility = View.GONE
                }

                // Status com cor
                when (status) {
                    "pendente" -> {
                        txtStatus.text = getString(R.string.status_pendente)
                        txtStatus.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                    }
                    "em_progresso" -> {
                        txtStatus.text = getString(R.string.status_em_progresso)
                        txtStatus.setTextColor(android.graphics.Color.parseColor("#F5E6D0"))
                    }
                    "concluido" -> {
                        txtStatus.text = getString(R.string.status_concluido)
                        txtStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                    }
                }

                // Carrega comentários e anexos em paralelo
                carregarComentarios(containerComentarios, vazioComentarios, txtQtdComentarios)
                carregarAnexos(containerAnexos, vazioAnexos, txtQtdAnexos)

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar trabalho: ${e.message}")
                Toast.makeText(
                    this@HistoricoTrabalhoDetalhesActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                carregando = false
            }
        }
    }

    // ============================================================
    // COMENTÁRIOS
    // ============================================================
    private fun carregarComentarios(
        container: LinearLayout,
        containerVazio: LinearLayout,
        txtQtd: TextView
    ) {
        container.removeAllViews()

        lifecycleScope.launch {
            try {
                val snap = db.collection("historico_equipes")
                    .document(emailUsuario)
                    .collection("equipes")
                    .document(equipeId)
                    .collection("trabalhos")
                    .document(trabalhoId)
                    .collection("comentarios")
                    .get()
                    .await()

                txtQtd.text = snap.size().toString()

                if (snap.isEmpty) {
                    containerVazio.visibility = View.VISIBLE
                    return@launch
                }

                containerVazio.visibility = View.GONE

                val inflater = LayoutInflater.from(this@HistoricoTrabalhoDetalhesActivity)
                val formatoData = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                val fallbackAutor = getString(R.string.comentarios_nome_placeholder)

                // Ordena: mais recente primeiro
                val comentarios = snap.documents.sortedByDescending {
                    it.getTimestamp("criadoEm")?.seconds ?: 0L
                }

                for (doc in comentarios) {
                    val autorEmail = doc.getString("autorEmail") ?: ""
                    val autorNome = doc.getString("autorNome") ?: fallbackAutor
                    val texto = doc.getString("texto") ?: ""
                    val editado = doc.getBoolean("editado") ?: false
                    val criadoEm = doc.getTimestamp("criadoEm")?.toDate()

                    val view = inflater.inflate(R.layout.item_historico_comentario, container, false)

                    val imgAvatar = view.findViewById<ImageView>(R.id.imgAvatarComentarioHistorico)
                    val txtIniciais = view.findViewById<TextView>(R.id.txtIniciaisComentarioHistorico)
                    val txtNome = view.findViewById<TextView>(R.id.txtNomeComentarioHistorico)
                    val txtData = view.findViewById<TextView>(R.id.txtDataComentarioHistorico)
                    val txtTexto = view.findViewById<TextView>(R.id.txtTextoComentarioHistorico)
                    val txtEditado = view.findViewById<TextView>(R.id.txtEditadoComentarioHistorico)

                    txtNome.text = autorNome
                    txtTexto.text = texto

                    if (criadoEm != null) {
                        txtData.text = formatoData.format(criadoEm)
                    }

                    txtEditado.visibility = if (editado) View.VISIBLE else View.GONE

                    // Carrega avatar do autor (fotoUrl no Firestore, que aponta pro Cloudinary)
                    carregarAvatarAutor(autorEmail, autorNome, imgAvatar, txtIniciais)

                    // Clicar abre o perfil do autor
                    view.setOnClickListener {
                        if (autorEmail.isNotEmpty()) {
                            val i = Intent(
                                this@HistoricoTrabalhoDetalhesActivity,
                                PerfilUsuarioActivity::class.java
                            )
                            i.putExtra("emailOutro", autorEmail)
                            startActivity(i)
                        }
                    }

                    container.addView(view)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar comentários: ${e.message}")
            }
        }
    }

    // ============================================================
    // ANEXOS
    // ============================================================
    private fun carregarAnexos(
        container: LinearLayout,
        containerVazio: LinearLayout,
        txtQtd: TextView
    ) {
        container.removeAllViews()

        lifecycleScope.launch {
            try {
                val snap = db.collection("historico_equipes")
                    .document(emailUsuario)
                    .collection("equipes")
                    .document(equipeId)
                    .collection("trabalhos")
                    .document(trabalhoId)
                    .collection("anexos")
                    .get()
                    .await()

                txtQtd.text = snap.size().toString()

                if (snap.isEmpty) {
                    containerVazio.visibility = View.VISIBLE
                    return@launch
                }

                containerVazio.visibility = View.GONE

                val inflater = LayoutInflater.from(this@HistoricoTrabalhoDetalhesActivity)
                val formatoData = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                val fallbackNome = getString(R.string.anexo_nome_arquivo_padrao)

                // Ordena: mais recente primeiro
                val anexos = snap.documents.sortedByDescending {
                    it.getTimestamp("criadoEm")?.seconds ?: 0L
                }

                for (doc in anexos) {
                    val nomeArquivo = doc.getString("nomeArquivo") ?: fallbackNome
                    val url = doc.getString("url") ?: ""
                    val tamanho = doc.getLong("tamanhoArquivo") ?: 0L
                    val mime = doc.getString("mimeType") ?: "application/octet-stream"
                    val enviadoPor = doc.getString("enviadoPor") ?: ""
                    val criadoEm = doc.getTimestamp("criadoEm")?.toDate()

                    val view = inflater.inflate(R.layout.item_historico_anexo, container, false)

                    val txtIcone = view.findViewById<TextView>(R.id.txtIconeAnexoHistorico)
                    val txtNome = view.findViewById<TextView>(R.id.txtNomeAnexoHistorico)
                    val txtInfo = view.findViewById<TextView>(R.id.txtInfoAnexoHistorico)

                    txtNome.text = nomeArquivo

                    // Info: "1.2 MB - por Ana - 14/10/2026 10:29"
                    txtInfo.text = buildString {
                        append(formatarTamanho(tamanho))
                        if (enviadoPor.isNotEmpty()) {
                            append(" • ")
                            append(enviadoPor)
                        }
                        if (criadoEm != null) {
                            append(" • ")
                            append(formatoData.format(criadoEm))
                        }
                    }

                    // Ícone por tipo
                    txtIcone.text = when {
                        mime == "application/pdf" -> "PDF"
                        mime.contains("word") || mime.contains("document") -> "DOC"
                        mime.contains("sheet") || mime.contains("excel") -> "XLS"
                        mime.contains("presentation") || mime.contains("powerpoint") -> "PPT"
                        mime.startsWith("image/") -> "IMG"
                        mime.startsWith("video/") -> "VID"
                        mime.startsWith("audio/") -> "AUD"
                        mime.contains("zip") || mime.contains("rar") -> "ZIP"
                        else -> "ARQ"
                    }

                    // Clique abre no app externo
                    view.setOnClickListener {
                        if (url.isNotEmpty()) {
                            abrirArquivoExterno(url, mime, nomeArquivo)
                        }
                    }

                    container.addView(view)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar anexos: ${e.message}")
            }
        }
    }

    // ============================================================
    // AVATAR DO AUTOR
    // ============================================================
    private fun carregarAvatarAutor(
        autorEmail: String,
        autorNome: String,
        imgAvatar: ImageView,
        txtIniciais: TextView
    ) {
        lifecycleScope.launch {
            try {
                if (autorEmail.isEmpty()) {
                    mostrarIniciais(autorNome, imgAvatar, txtIniciais)
                    return@launch
                }

                val userDoc = db.collection("usuarios").document(autorEmail).get().await()
                val fotoUrl = userDoc.getString("fotoUrl") ?: ""

                if (fotoUrl.isNotEmpty()) {
                    Glide.with(this@HistoricoTrabalhoDetalhesActivity)
                        .load(fotoUrl)
                        .circleCrop()
                        .into(imgAvatar)
                    imgAvatar.visibility = View.VISIBLE
                    txtIniciais.visibility = View.GONE
                } else {
                    mostrarIniciais(autorNome, imgAvatar, txtIniciais)
                }
            } catch (e: Exception) {
                mostrarIniciais(autorNome, imgAvatar, txtIniciais)
            }
        }
    }

    private fun mostrarIniciais(nome: String, imgAvatar: ImageView, txtIniciais: TextView) {
        val iniciais = nome.split(" ")
            .take(2)
            .map { it.firstOrNull()?.uppercase() ?: "" }
            .joinToString("")
            .ifEmpty { "?" }
        txtIniciais.text = iniciais
        txtIniciais.visibility = View.VISIBLE
        imgAvatar.visibility = View.GONE
    }

    // ============================================================
    // HELPERS
    // ============================================================
    private fun abrirArquivoExterno(url: String, mime: String, nome: String) {
        try {
            val i = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(url), mime.ifEmpty { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.anexo_sem_app, nome),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun formatarTamanho(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }

    companion object {
        private const val TAG = "HISTORICO_TRABALHO"
        private const val PREFS_NAME = "CTR_PREFS"
        private const val KEY_EMAIL_USUARIO = "emailUsuario"
    }
}