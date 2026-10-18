package com.example.plataformaremota

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
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

/**
 * Lista TODOS os trabalhos onde o usuário é criador ou participante:
 *  - Ativos (equipes que ele está agora)
 *  - Históricos (equipes que ele saiu)
 */
class HistoricoTrabalhosActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""

    private var carregando = false

    // Modelo unificado
    private data class TrabalhoLista(
        val trabalhoId: String,
        val equipeId: String,
        val titulo: String,
        val descricao: String,
        val status: String,
        val prazo: String,
        val nomeEquipe: String,
        val equipeAtiva: Boolean,
        val isHistorico: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historico_trabalhos)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        emailUsuario = auth.currentUser?.email
            ?: prefs.getString(KEY_EMAIL_USUARIO, "")
                    ?: ""

        findViewById<Button>(R.id.btnVoltarHistoricoTrabalhos).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        if (emailUsuario.isNotEmpty()) {
            carregando = false
            carregarTrabalhos()
        }
    }

    private fun carregarTrabalhos() {
        if (carregando) return
        carregando = true

        val container = findViewById<LinearLayout>(R.id.containerHistoricoTrabalhos)
        val containerVazio = findViewById<LinearLayout>(R.id.containerVazioHistoricoTrabalhos)
        container.removeAllViews()

        lifecycleScope.launch {
            try {
                val lista = mutableListOf<TrabalhoLista>()
                val equipesProcessadas = mutableSetOf<String>()

                // ============================================================
                // 1. TRABALHOS HISTÓRICOS (equipes que saiu)
                // ============================================================
                val equipesHistoricas = db.collection("historico_equipes")
                    .document(emailUsuario)
                    .collection("equipes")
                    .get()
                    .await()

                for (equipeDoc in equipesHistoricas.documents) {
                    val eqId = equipeDoc.getString("equipeId") ?: equipeDoc.id
                    val nomeEq = equipeDoc.getString("nomeEquipe") ?: getString(R.string.geral_equipe)
                    val statusEquipe = equipeDoc.getString("status") ?: "inativo"
                    equipesProcessadas.add(eqId)

                    // Busca trabalhos do histórico dessa equipe
                    val trabalhosHist = db.collection("historico_equipes")
                        .document(emailUsuario)
                        .collection("equipes")
                        .document(eqId)
                        .collection("trabalhos")
                        .get()
                        .await()

                    for (t in trabalhosHist.documents) {
                        lista.add(
                            TrabalhoLista(
                                trabalhoId = t.getString("trabalhoId") ?: t.id,
                                equipeId = eqId,
                                titulo = t.getString("titulo") ?: getString(R.string.meus_trabalhos_sem_titulo),
                                descricao = t.getString("descricao") ?: getString(R.string.meus_trabalhos_sem_descricao),
                                status = t.getString("status") ?: "pendente",
                                prazo = t.getString("prazo") ?: getString(R.string.meus_trabalhos_sem_prazo),
                                nomeEquipe = nomeEq,
                                equipeAtiva = (statusEquipe == "ativo"),
                                isHistorico = true
                            )
                        )
                    }
                }

                // ============================================================
                // 2. TRABALHOS ATIVOS (equipes que está agora)
                // ============================================================
                // 2a. Onde é criador
                val comoCriador = db.collection("trabalhos")
                    .whereEqualTo("criadorEmail", emailUsuario)
                    .get()
                    .await()

                // 2b. Onde é participante
                val comoParticipante = db.collection("trabalhos")
                    .whereEqualTo("participanteEmail", emailUsuario)
                    .get()
                    .await()

                // 2c. Onde é responsável (compatibilidade antiga)
                val comoResponsavel = db.collection("trabalhos")
                    .whereEqualTo("responsavelEmail", emailUsuario)
                    .get()
                    .await()

                // Junta (sem duplicar)
                val todosAtivos = mutableMapOf<String, com.google.firebase.firestore.DocumentSnapshot>()
                (comoCriador.documents + comoParticipante.documents + comoResponsavel.documents).forEach {
                    todosAtivos[it.id] = it
                }

                for ((_, t) in todosAtivos) {
                    val eqId = t.getString("equipeId") ?: ""
                    if (eqId in equipesProcessadas) continue // já foi processado no histórico

                    // Carrega nome da equipe
                    val nomeEq = try {
                        val eqDoc = db.collection("equipes").document(eqId).get().await()
                        eqDoc.getString("nome") ?: getString(R.string.geral_equipe)
                    } catch (e: Exception) {
                        getString(R.string.geral_equipe)
                    }

                    lista.add(
                        TrabalhoLista(
                            trabalhoId = t.id,
                            equipeId = eqId,
                            titulo = t.getString("titulo") ?: getString(R.string.meus_trabalhos_sem_titulo),
                            descricao = t.getString("descricao") ?: getString(R.string.meus_trabalhos_sem_descricao),
                            status = t.getString("status") ?: "pendente",
                            prazo = t.getString("prazo") ?: getString(R.string.meus_trabalhos_sem_prazo),
                            nomeEquipe = nomeEq,
                            equipeAtiva = true,
                            isHistorico = false
                        )
                    )
                }

                // ============================================================
                // 3. RENDERIZA
                // ============================================================
                if (lista.isEmpty()) {
                    containerVazio.visibility = View.VISIBLE
                    return@launch
                }

                containerVazio.visibility = View.GONE
                val inflater = LayoutInflater.from(this@HistoricoTrabalhosActivity)

                // Ordena: ativos primeiro, depois por título
                val ordenada = lista.sortedWith(
                    compareByDescending<TrabalhoLista> { it.equipeAtiva }
                        .thenBy { it.titulo.lowercase() }
                )

                for (t in ordenada) {
                    val view = inflater.inflate(R.layout.item_historico_trabalho_lista, container, false)

                    val txtTitulo = view.findViewById<TextView>(R.id.txtTituloTrabalhoLista)
                    val txtEquipe = view.findViewById<TextView>(R.id.txtEquipeTrabalhoLista)
                    val txtPrazo = view.findViewById<TextView>(R.id.txtPrazoTrabalhoLista)
                    val txtStatus = view.findViewById<TextView>(R.id.txtStatusTrabalhoLista)
                    val txtOrigem = view.findViewById<TextView>(R.id.txtOrigemTrabalhoLista)

                    txtTitulo.text = t.titulo
                    txtEquipe.text = t.nomeEquipe
                    txtPrazo.text = getString(R.string.meus_trabalhos_entrega_formatado, t.prazo)

                    // Status do trabalho
                    when (t.status) {
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

                    // Origem (ativo vs histórico)
                    if (t.isHistorico) {
                        if (t.equipeAtiva) {
                            txtOrigem.text = getString(R.string.historico_equipes_status_ativo)
                            txtOrigem.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                        } else {
                            txtOrigem.text = getString(R.string.historico_trabalhos_inativa)
                            txtOrigem.setTextColor(android.graphics.Color.parseColor("#FF6B6B"))
                        }
                        txtOrigem.visibility = View.VISIBLE
                    } else {
                        txtOrigem.visibility = View.GONE
                    }

                    // Clique: abre detalhes
                    view.setOnClickListener {
                        if (t.isHistorico) {
                            // Histórico: abre HistoricoTrabalhoDetalhesActivity
                            val i = Intent(this@HistoricoTrabalhosActivity, HistoricoTrabalhoDetalhesActivity::class.java)
                            i.putExtra("equipeId", t.equipeId)
                            i.putExtra("trabalhoId", t.trabalhoId)
                            startActivity(i)
                        } else {
                            // Ativo: abre ComentariosTrabalhoActivity (tela normal)
                            val i = Intent(this@HistoricoTrabalhosActivity, ComentariosTrabalhoActivity::class.java)
                            i.putExtra("trabalhoId", t.trabalhoId)
                            i.putExtra("tituloTrabalho", t.titulo)
                            i.putExtra("equipeId", t.equipeId)
                            startActivity(i)
                        }
                    }

                    container.addView(view)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar trabalhos: ${e.message}")
                Toast.makeText(
                    this@HistoricoTrabalhosActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                carregando = false
            }
        }
    }

    companion object {
        private const val TAG = "HISTORICO_TRABALHOS"
        private const val PREFS_NAME = "CTR_PREFS"
        private const val KEY_EMAIL_USUARIO = "emailUsuario"
    }
}