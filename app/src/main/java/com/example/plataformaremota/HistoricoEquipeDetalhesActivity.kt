package com.example.plataformaremota

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Mostra os detalhes de uma equipe do histórico do usuário.
 * Inclui dados da equipe + lista de trabalhos que ele participou.
 */
class HistoricoEquipeDetalhesActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""
    private var equipeId: String = ""

    private var carregando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historico_equipe_detalhes)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        emailUsuario = auth.currentUser?.email
            ?: prefs.getString(KEY_EMAIL_USUARIO, "")
                    ?: ""

        equipeId = intent.getStringExtra("equipeId") ?: ""

        if (equipeId.isEmpty()) {
            Toast.makeText(this, getString(R.string.historico_equipes_equipe_fallback), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<Button>(R.id.btnVoltarDetalhesEquipe).setOnClickListener { finish() }

        carregarDados()
    }

    private fun carregarDados() {
        if (carregando) return
        carregando = true

        val txtNome = findViewById<TextView>(R.id.txtNomeEquipeDetalhes)
        val txtDescricao = findViewById<TextView>(R.id.txtDescricaoEquipeDetalhes)
        val txtPapel = findViewById<TextView>(R.id.txtPapelEquipeDetalhes)
        val txtBadgeMotivo = findViewById<TextView>(R.id.txtBadgeMotivoDetalhes)
        val txtPeriodo = findViewById<TextView>(R.id.txtPeriodoDetalhes)
        val containerTrabalhos = findViewById<LinearLayout>(R.id.containerTrabalhosHistorico)
        val containerVazioTrabalhos = findViewById<LinearLayout>(R.id.containerVazioTrabalhosHistorico)
        val txtTituloTrabalhos = findViewById<TextView>(R.id.txtTituloSecaoTrabalhos)

        lifecycleScope.launch {
            try {
                // Lê o doc do histórico
                val doc = db.collection("historico_equipes")
                    .document(emailUsuario)
                    .collection("equipes")
                    .document(equipeId)
                    .get()
                    .await()

                if (!doc.exists()) {
                    Toast.makeText(
                        this@HistoricoEquipeDetalhesActivity,
                        getString(R.string.historico_equipes_equipe_fallback),
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                    return@launch
                }

                val nomeEquipe = doc.getString("nomeEquipe")
                    ?: getString(R.string.historico_equipes_equipe_fallback)
                val descricaoEquipe = doc.getString("descricaoEquipe") ?: ""
                val papel = doc.getString("papel") ?: "membro"
                val motivoSaida = doc.getString("motivoSaida") ?: "saiu"
                val entrouEm = doc.getTimestamp("entrouEm")?.toDate()
                val saiuEm = doc.getTimestamp("saiuEm")?.toDate()

                val formatoData = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

                txtNome.text = nomeEquipe
                txtDescricao.text = descricaoEquipe.ifEmpty {
                    getString(R.string.historico_equipes_sem_descricao)
                }

                // Papel
                txtPapel.text = when (papel) {
                    "dono" -> getString(R.string.minhas_equipes_papel_criador)
                    "admin" -> getString(R.string.minhas_equipes_papel_admin)
                    else -> getString(R.string.minhas_equipes_papel_membro)
                }

                // Badge do motivo
                when (motivoSaida) {
                    "saiu" -> {
                        txtBadgeMotivo.text = getString(R.string.historico_equipes_motivo_saiu)
                        txtBadgeMotivo.setBackgroundColor(android.graphics.Color.parseColor("#9E9E9E"))
                        txtBadgeMotivo.setTextColor(android.graphics.Color.parseColor("#1C1311"))
                    }
                    "removido" -> {
                        txtBadgeMotivo.text = getString(R.string.historico_equipes_motivo_removido)
                        txtBadgeMotivo.setBackgroundColor(android.graphics.Color.parseColor("#FF6B6B"))
                        txtBadgeMotivo.setTextColor(android.graphics.Color.WHITE)
                    }
                    "equipe_excluida" -> {
                        txtBadgeMotivo.text = getString(R.string.historico_equipes_motivo_excluida)
                        txtBadgeMotivo.setBackgroundColor(android.graphics.Color.parseColor("#FF6B6B"))
                        txtBadgeMotivo.setTextColor(android.graphics.Color.WHITE)
                    }
                }

                // Período
                txtPeriodo.text = buildString {
                    if (entrouEm != null) {
                        append(getString(R.string.historico_detalhes_entrou_em, formatoData.format(entrouEm)))
                    }
                    if (entrouEm != null && saiuEm != null) append("\n")
                    if (saiuEm != null) {
                        append(getString(R.string.historico_detalhes_saiu_em, formatoData.format(saiuEm)))
                    }
                }

                // Carrega os trabalhos
                carregarTrabalhos(containerTrabalhos, containerVazioTrabalhos, txtTituloTrabalhos)

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar detalhes: ${e.message}")
                Toast.makeText(
                    this@HistoricoEquipeDetalhesActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                carregando = false
            }
        }
    }

    private fun carregarTrabalhos(
        container: LinearLayout,
        containerVazio: LinearLayout,
        txtTituloSecao: TextView
    ) {
        container.removeAllViews()

        lifecycleScope.launch {
            try {
                val snap = db.collection("historico_equipes")
                    .document(emailUsuario)
                    .collection("equipes")
                    .document(equipeId)
                    .collection("trabalhos")
                    .get()
                    .await()

                if (snap.isEmpty) {
                    txtTituloSecao.visibility = View.GONE
                    containerVazio.visibility = View.VISIBLE
                    return@launch
                }

                txtTituloSecao.visibility = View.VISIBLE
                containerVazio.visibility = View.GONE

                val inflater = LayoutInflater.from(this@HistoricoEquipeDetalhesActivity)
                val fallbackTitulo = getString(R.string.meus_trabalhos_sem_titulo)
                val fallbackPrazo = getString(R.string.meus_trabalhos_sem_prazo)

                // Ordena por criadoEm se existir, senão por título
                val trabalhos = snap.documents.sortedByDescending {
                    it.getTimestamp("criadoEm")?.seconds ?: 0L
                }

                for (doc in trabalhos) {
                    val trabalhoId = doc.getString("trabalhoId") ?: doc.id
                    val titulo = doc.getString("titulo") ?: fallbackTitulo
                    val status = doc.getString("status") ?: "pendente"
                    val prazo = doc.getString("prazo") ?: fallbackPrazo
                    val categoria = doc.getString("categoria") ?: ""

                    val view = inflater.inflate(R.layout.item_historico_trabalho, container, false)

                    val txtTitulo = view.findViewById<TextView>(R.id.txtTituloTrabalhoHistorico)
                    val txtInfo = view.findViewById<TextView>(R.id.txtInfoTrabalhoHistorico)
                    val txtStatus = view.findViewById<TextView>(R.id.txtStatusTrabalhoHistorico)
                    val txtQtdComentarios = view.findViewById<TextView>(R.id.txtQtdComentariosHistorico)
                    val txtQtdAnexos = view.findViewById<TextView>(R.id.txtQtdAnexosHistorico)

                    txtTitulo.text = titulo

                    // Info: "Categoria - Entrega: X"
                    txtInfo.text = buildString {
                        if (categoria.isNotEmpty()) append(categoria)
                        if (categoria.isNotEmpty() && prazo.isNotEmpty()) append(" • ")
                        append(getString(R.string.meus_trabalhos_entrega_formatado, prazo))
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

                    // Contadores (comentários/anexos) — carrega de forma assíncrona
                    lifecycleScope.launch {
                        try {
                            val comentarios = db.collection("historico_equipes")
                                .document(emailUsuario)
                                .collection("equipes")
                                .document(equipeId)
                                .collection("trabalhos")
                                .document(trabalhoId)
                                .collection("comentarios")
                                .get()
                                .await()
                            txtQtdComentarios.text = getString(
                                R.string.historico_trabalho_qtd_comentarios,
                                comentarios.size()
                            )
                        } catch (e: Exception) {
                            txtQtdComentarios.text = getString(
                                R.string.historico_trabalho_qtd_comentarios,
                                0
                            )
                        }
                    }

                    lifecycleScope.launch {
                        try {
                            val anexos = db.collection("historico_equipes")
                                .document(emailUsuario)
                                .collection("equipes")
                                .document(equipeId)
                                .collection("trabalhos")
                                .document(trabalhoId)
                                .collection("anexos")
                                .get()
                                .await()
                            txtQtdAnexos.text = getString(
                                R.string.historico_trabalho_qtd_anexos,
                                anexos.size()
                            )
                        } catch (e: Exception) {
                            txtQtdAnexos.text = getString(
                                R.string.historico_trabalho_qtd_anexos,
                                0
                            )
                        }
                    }

                    // Clicar abre a tela de detalhes do trabalho
                    view.setOnClickListener {
                        val i = Intent(
                            this@HistoricoEquipeDetalhesActivity,
                            HistoricoTrabalhoDetalhesActivity::class.java
                        )
                        i.putExtra("equipeId", equipeId)
                        i.putExtra("trabalhoId", trabalhoId)
                        startActivity(i)
                    }

                    container.addView(view)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar trabalhos: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "HISTORICO_DETALHES"
        private const val PREFS_NAME = "CTR_PREFS"
        private const val KEY_EMAIL_USUARIO = "emailUsuario"
    }
}