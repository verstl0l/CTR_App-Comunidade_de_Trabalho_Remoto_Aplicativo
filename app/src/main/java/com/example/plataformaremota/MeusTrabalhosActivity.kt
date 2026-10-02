package com.example.plataformaremota

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.content.Context

/**
 * Tela que mostra TODOS os trabalhos do usuario logado,
 * em todas as equipes que ele pertence.
 *
 * Filtros: Todos | Pendente | Em Progresso | Concluido
 */
class MeusTrabalhosActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""
    private var filtroAtual: String = "todos"

    // ✅ Cache local
    private var todosTrabalhos: List<TrabalhoItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meus_trabalhos)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        //  Fallback pro cache (mesmo problema do login)
        val prefs = getSharedPreferences("CTR_PREFS", Context.MODE_PRIVATE)
        val emailCache = prefs.getString("emailUsuario", "") ?: ""
        emailUsuario = auth.currentUser?.email ?: emailCache

        if (emailUsuario.isEmpty()) {
            Toast.makeText(this, "Sessão expirada. Faça login novamente.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        findViewById<Button>(R.id.btnVoltarMeusTrabalhos).setOnClickListener { finish() }

        // Filtros
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

    // ============================================================
    // CARREGAR TRABALHOS
    // ============================================================
    private fun carregarTrabalhos() {
        lifecycleScope.launch {
            try {
                // 1. Busca todas as equipes que o usuario pertence
                val membros = db.collection("membros_equipe")
                    .whereEqualTo("email", emailUsuario)
                    .get()
                    .await()

                val equipesIds = membros.documents.mapNotNull { it.getString("equipeId") }

                // 2. Busca equipes que o usuario criou
                val equipesCriador = db.collection("equipes")
                    .whereEqualTo("criadorEmail", emailUsuario)
                    .get()
                    .await()

                val equipesCriadorIds = equipesCriador.documents.map { it.id }

                // 3. Junta (sem duplicatas)
                val todasEquipesIds = (equipesIds + equipesCriadorIds).distinct()

                if (todasEquipesIds.isEmpty()) {
                    todosTrabalhos = emptyList()
                    aplicarFiltro()
                    return@launch
                }

                // 4. Busca trabalhos dessas equipes onde o usuario e responsavel ou criador
                val trabalhos = mutableListOf<TrabalhoItem>()

                // Firestore limita `whereIn` a 10 itens
                todasEquipesIds.chunked(10).forEach { chunk ->
                    val snap = db.collection("trabalhos")
                        .whereIn("equipeId", chunk)
                        .get()
                        .await()

                    snap.documents.forEach { doc ->
                        val responsavel = doc.getString("responsavelEmail") ?: ""
                        val criador = doc.getString("criadorEmail") ?: ""

                        // ✅ So mostra trabalhos onde SOU responsavel OU criador
                        if (responsavel == emailUsuario || criador == emailUsuario) {
                            // Busca nome da equipe
                            val equipeDoc = db.collection("equipes").document(doc.getString("equipeId") ?: "").get().await()
                            val nomeEquipe = equipeDoc.getString("nome") ?: "Equipe"

                            trabalhos.add(
                                TrabalhoItem(
                                    id = doc.id,
                                    titulo = doc.getString("titulo") ?: "Sem título",
                                    descricao = doc.getString("descricao") ?: "Sem descrição",
                                    status = doc.getString("status") ?: "pendente",
                                    prazo = doc.getString("prazo") ?: "Sem prazo",
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
                Log.e("MEUS_TRABALHOS", "Erro: ${e.message}")
                Toast.makeText(this@MeusTrabalhosActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ============================================================
    // APLICAR FILTRO
    // ============================================================
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

        filtrados.forEach { trabalho ->
            val view = inflater.inflate(R.layout.item_meu_trabalho, container, false)

            view.findViewById<TextView>(R.id.txtTituloMeuTrabalho).text = trabalho.titulo
            view.findViewById<TextView>(R.id.txtDescricaoMeuTrabalho).text = trabalho.descricao
            view.findViewById<TextView>(R.id.txtPrazoMeuTrabalho).text = "Entrega: ${trabalho.prazo}"
            view.findViewById<TextView>(R.id.txtEquipeMeuTrabalho).text = trabalho.equipeNome

            val txtStatus = view.findViewById<TextView>(R.id.txtStatusMeuTrabalho)
            when (trabalho.status) {
                "pendente" -> {
                    txtStatus.text = "Pendente"
                    txtStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                }
                "em_progresso" -> {
                    txtStatus.text = "Em Progresso"
                    txtStatus.setTextColor(ContextCompat.getColor(this, R.color.accent))
                }
                "concluido" -> {
                    txtStatus.text = "Concluído"
                    txtStatus.setTextColor(ContextCompat.getColor(this, R.color.status_concluido))
                }
            }

            // Clique abre a tela de trabalho (EntregarTrabalho na equipe)
            view.setOnClickListener {
                val intent = android.content.Intent(this, EntregarTrabalhoActivity::class.java)
                intent.putExtra("equipeId", trabalho.equipeId)
                startActivity(intent)
            }

            container.addView(view)
        }
    }

    // ============================================================
    // FILTROS UI
    // ============================================================
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

    // ============================================================
    // DATA CLASS
    // ============================================================
    data class TrabalhoItem(
        val id: String,
        val titulo: String,
        val descricao: String,
        val status: String,
        val prazo: String,
        val equipeNome: String,
        val equipeId: String
    )
}