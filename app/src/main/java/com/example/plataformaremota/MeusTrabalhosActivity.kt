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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Tela que mostra TODOS os trabalhos do usuario logado,
 * em todas as equipes que ele pertence.
 *
 * Filtros: Todos | Pendente | Em Progresso | Concluido
 */
class MeusTrabalhosActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""
    private var filtroAtual: String = "todos"

    private var todosTrabalhos: List<TrabalhoItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meus_trabalhos)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val emailCache = prefs.getString(KEY_EMAIL_USUARIO, "") ?: ""
        emailUsuario = auth.currentUser?.email ?: emailCache

        if (emailUsuario.isEmpty()) {
            Toast.makeText(this, getString(R.string.meus_trabalhos_sessao_expirada), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        findViewById<Button>(R.id.btnVoltarMeusTrabalhos).setOnClickListener { finish() }

        val btnTodos = findViewById<TextView>(R.id.btnFiltroTodosMeus)
        val btnPendente = findViewById<TextView>(R.id.btnFiltroPendenteMeus)
        val btnProgresso = findViewById<TextView>(R.id.btnFiltroProgressoMeus)
        val btnConcluido = findViewById<TextView>(R.id.btnFiltroConcluidoMeus)

        btnTodos.setOnClickListener {
            filtroAtual = "todos"
            atualizarBotoesFiltro(btnTodos, btnPendente, btnProgresso, btnConcluido)
            aplicarFiltro()
        }
        btnPendente.setOnClickListener {
            filtroAtual = "pendente"
            atualizarBotoesFiltro(btnTodos, btnPendente, btnProgresso, btnConcluido)
            aplicarFiltro()
        }
        btnProgresso.setOnClickListener {
            filtroAtual = "em_progresso"
            atualizarBotoesFiltro(btnTodos, btnPendente, btnProgresso, btnConcluido)
            aplicarFiltro()
        }
        btnConcluido.setOnClickListener {
            filtroAtual = "concluido"
            atualizarBotoesFiltro(btnTodos, btnPendente, btnProgresso, btnConcluido)
            aplicarFiltro()
        }

        carregarTrabalhos()
    }

    private fun carregarTrabalhos() {
        lifecycleScope.launch {
            try {
                val membros = db.collection("membros_equipe")
                    .whereEqualTo("email", emailUsuario)
                    .get()
                    .await()

                val equipesIds = membros.documents.mapNotNull { it.getString("equipeId") }

                val equipesCriador = db.collection("equipes")
                    .whereEqualTo("criadorEmail", emailUsuario)
                    .get()
                    .await()

                val equipesCriadorIds = equipesCriador.documents.map { it.id }

                val todasEquipesIds = (equipesIds + equipesCriadorIds).distinct()

                if (todasEquipesIds.isEmpty()) {
                    todosTrabalhos = emptyList()
                    aplicarFiltro()
                    return@launch
                }

                val trabalhos = mutableListOf<TrabalhoItem>()

                // ✅ Captura fallbacks traduzidos UMA VEZ
                val fallbackEquipe = getString(R.string.meus_trabalhos_equipe_fallback)
                val fallbackSemTitulo = getString(R.string.meus_trabalhos_sem_titulo)
                val fallbackSemDescricao = getString(R.string.meus_trabalhos_sem_descricao)
                val fallbackSemPrazo = getString(R.string.meus_trabalhos_sem_prazo)

                todasEquipesIds.chunked(10).forEach { chunk ->
                    val snap = db.collection("trabalhos")
                        .whereIn("equipeId", chunk)
                        .get()
                        .await()

                    snap.documents.forEach { doc ->
                        val responsavel = doc.getString("responsavelEmail") ?: ""
                        val criador = doc.getString("criadorEmail") ?: ""

                        if (responsavel == emailUsuario || criador == emailUsuario) {
                            val equipeDoc = db.collection("equipes")
                                .document(doc.getString("equipeId") ?: "")
                                .get().await()
                            val nomeEquipe = equipeDoc.getString("nome") ?: fallbackEquipe

                            trabalhos.add(
                                TrabalhoItem(
                                    id = doc.id,
                                    titulo = doc.getString("titulo") ?: fallbackSemTitulo,
                                    descricao = doc.getString("descricao") ?: fallbackSemDescricao,
                                    status = doc.getString("status") ?: "pendente",
                                    prazo = doc.getString("prazo") ?: fallbackSemPrazo,
                                    equipeNome = nomeEquipe,
                                    equipeId = doc.getString("equipeId") ?: ""
                                )
                            )
                        }
                    }
                }

                todosTrabalhos = trabalhos.sortedBy { it.prazo }
                aplicarFiltro()

            } catch (e: Exception) {
                Log.e(TAG, "Erro: ${e.message}")
                Toast.makeText(
                    this@MeusTrabalhosActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun aplicarFiltro() {
        val container = findViewById<LinearLayout>(R.id.containerMeusTrabalhos)
        val containerVazio = findViewById<LinearLayout>(R.id.containerVazioMeus)
        container.removeAllViews()

        val filtrados = if (filtroAtual == "todos") {
            todosTrabalhos
        } else {
            todosTrabalhos.filter { it.status == filtroAtual }
        }

        if (filtrados.isEmpty()) {
            containerVazio.visibility = View.VISIBLE
            return
        }

        containerVazio.visibility = View.GONE
        val inflater = LayoutInflater.from(this)

        // ✅ Captura labels traduzidos ANTES do loop
        val labelPendente = getString(R.string.status_pendente)
        val labelEmProgresso = getString(R.string.status_em_progresso)
        val labelConcluido = getString(R.string.status_concluido)
        val prefixoEntrega = getString(R.string.meus_trabalhos_entrega_prefixo)

        filtrados.forEach { trabalho ->
            val view = inflater.inflate(R.layout.item_meu_trabalho, container, false)

            view.findViewById<TextView>(R.id.txtTituloMeuTrabalho).text = trabalho.titulo
            view.findViewById<TextView>(R.id.txtDescricaoMeuTrabalho).text = trabalho.descricao
            view.findViewById<TextView>(R.id.txtPrazoMeuTrabalho).text =
                getString(R.string.meus_trabalhos_entrega_formatado, trabalho.prazo)
            view.findViewById<TextView>(R.id.txtEquipeMeuTrabalho).text = trabalho.equipeNome

            val txtStatus = view.findViewById<TextView>(R.id.txtStatusMeuTrabalho)
            when (trabalho.status) {
                "pendente" -> {
                    txtStatus.text = labelPendente
                    txtStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                }
                "em_progresso" -> {
                    txtStatus.text = labelEmProgresso
                    txtStatus.setTextColor(ContextCompat.getColor(this, R.color.accent))
                }
                "concluido" -> {
                    txtStatus.text = labelConcluido
                    txtStatus.setTextColor(ContextCompat.getColor(this, R.color.status_concluido))
                }
            }

            view.setOnClickListener {
                val intent = Intent(this, EntregarTrabalhoActivity::class.java)
                intent.putExtra("equipeId", trabalho.equipeId)
                startActivity(intent)
            }

            container.addView(view)
        }
    }

    private fun atualizarBotoesFiltro(
        btnTodos: TextView,
        btnPendente: TextView,
        btnProgresso: TextView,
        btnConcluido: TextView
    ) {
        val inativo = ContextCompat.getColor(this, R.color.bg_surface_hover)
        val ativo = ContextCompat.getColor(this, R.color.accent)
        val textoInativo = ContextCompat.getColor(this, R.color.text_primary)
        val textoAtivo = ContextCompat.getColor(this, R.color.accent_dark)

        listOf(btnTodos, btnPendente, btnProgresso, btnConcluido).forEach {
            it.setBackgroundColor(inativo)
            it.setTextColor(textoInativo)
        }

        val btnAtivo = when (filtroAtual) {
            "todos" -> btnTodos
            "pendente" -> btnPendente
            "em_progresso" -> btnProgresso
            "concluido" -> btnConcluido
            else -> btnTodos
        }
        btnAtivo.setBackgroundColor(ativo)
        btnAtivo.setTextColor(textoAtivo)
    }

    data class TrabalhoItem(
        val id: String,
        val titulo: String,
        val descricao: String,
        val status: String,
        val prazo: String,
        val equipeNome: String,
        val equipeId: String
    )

    companion object {
        private const val TAG = "MEUS_TRABALHOS"
        private const val PREFS_NAME = "CTR_PREFS"
        private const val KEY_EMAIL_USUARIO = "emailUsuario"
    }
}