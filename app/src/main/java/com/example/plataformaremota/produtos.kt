package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class produtos : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var equipeIdAtual: String? = null
    private var filtroAtual: String = "todos"

    private var todosTrabalhos: List<DocumentSnapshot> = emptyList()
    private var listenerTrabalhos: ListenerRegistration? = null

    private lateinit var containerTrabalhos: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        val email = auth.currentUser?.email ?: ""

        val equipeIdIntent = intent.getStringExtra("equipeId")

        lifecycleScope.launch {
            try {
                if (equipeIdIntent != null) {
                    equipeIdAtual = equipeIdIntent
                    setContentView(R.layout.activity_produtos)
                    configurarDashboard(email)
                    configurarBottomNavigation(R.id.nav_groups)
                    return@launch
                }

                val equipeCriador = db.collection("equipes")
                    .whereEqualTo("criadorEmail", email)
                    .limit(1)
                    .get()
                    .await()

                if (!equipeCriador.isEmpty) {
                    equipeIdAtual = equipeCriador.documents[0].id
                    setContentView(R.layout.activity_produtos)
                    configurarDashboard(email)
                    configurarBottomNavigation(R.id.nav_groups)
                    return@launch
                }

                setContentView(R.layout.activity_produtos_vazio)
                configurarTelaVazia()
                configurarBottomNavigation(R.id.nav_groups)

            } catch (e: Exception) {
                Log.e(TAG, "Erro: ${e.message}")
                Toast.makeText(
                    this@produtos,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        listenerTrabalhos?.remove()
        listenerTrabalhos = null
    }

    private fun configurarDashboard(email: String) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val nomeUsuario = prefs.getString(KEY_NOME_USUARIO, "")
            ?: getString(R.string.produtos_usuario_fallback)

        val txtNomeEquipe = findViewById<TextView>(R.id.txtNomeEquipeDashboard)
        val txtCriador = findViewById<TextView>(R.id.txtCriadorEquipeDashboard)
        val txtDescricao = findViewById<TextView>(R.id.txtDescricaoEquipeDashboard)
        val txtLogo = findViewById<TextView>(R.id.txtLogoEquipe)
        val btnConfig = findViewById<ImageView>(R.id.btnConfigEquipe)
        val btnCriarTrabalho = findViewById<Button>(R.id.btnCriarTrabalho)
        val btnProdutividade = findViewById<Button>(R.id.btnProdutividade)
        val btnChatEquipe = findViewById<Button>(R.id.btnChatEquipe)

        containerTrabalhos = findViewById(R.id.containerTrabalhosRecentes)

        btnProdutividade.setOnClickListener {
            val intent = Intent(this, ProdutividadeActivity::class.java)
            intent.putExtra("equipeId", equipeIdAtual)
            startActivity(intent)
        }

        btnChatEquipe.setOnClickListener {
            val intent = Intent(this, ChatEquipeHubActivity::class.java)
            intent.putExtra("equipeId", equipeIdAtual)
            startActivity(intent)
        }

        val btnFiltroTodos = findViewById<TextView>(R.id.btnFiltroTodos)
        val btnFiltroPendente = findViewById<TextView>(R.id.btnFiltroPendente)
        val btnFiltroProgresso = findViewById<TextView>(R.id.btnFiltroProgresso)
        val btnFiltroConcluido = findViewById<TextView>(R.id.btnFiltroConcluido)

        btnFiltroTodos.setOnClickListener {
            filtroAtual = "todos"
            atualizarBotoesFiltro(btnFiltroTodos, btnFiltroPendente, btnFiltroProgresso, btnFiltroConcluido)
            aplicarFiltro()
        }
        btnFiltroPendente.setOnClickListener {
            filtroAtual = "pendente"
            atualizarBotoesFiltro(btnFiltroTodos, btnFiltroPendente, btnFiltroProgresso, btnFiltroConcluido)
            aplicarFiltro()
        }
        btnFiltroProgresso.setOnClickListener {
            filtroAtual = "em_progresso"
            atualizarBotoesFiltro(btnFiltroTodos, btnFiltroPendente, btnFiltroProgresso, btnFiltroConcluido)
            aplicarFiltro()
        }
        btnFiltroConcluido.setOnClickListener {
            filtroAtual = "concluido"
            atualizarBotoesFiltro(btnFiltroTodos, btnFiltroPendente, btnFiltroProgresso, btnFiltroConcluido)
            aplicarFiltro()
        }

        // ✅ Placeholder na string
        txtCriador.text = getString(R.string.produtos_criado_por, nomeUsuario)

        lifecycleScope.launch {
            try {
                val equipeDoc = db.collection("equipes").document(equipeIdAtual!!).get().await()
                val nome = equipeDoc.getString("nome") ?: getString(R.string.produtos_equipe_fallback)
                val descricao = equipeDoc.getString("descricao") ?: ""

                txtNomeEquipe.text = nome
                txtDescricao.text = descricao.ifEmpty { getString(R.string.produtos_sem_descricao) }

                val iniciais = nome.split(" ")
                    .take(2)
                    .map { palavra -> palavra.firstOrNull()?.uppercase() ?: "" }
                    .joinToString("")
                txtLogo.text = iniciais.ifEmpty { getString(R.string.produtos_logo_fallback) }

                iniciarListenerTrabalhos()

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar equipe: ${e.message}")
            }
        }

        btnConfig.setOnClickListener {
            val intent = Intent(this, GerenciarEquipeActivity::class.java)
            intent.putExtra("equipeId", equipeIdAtual)
            startActivity(intent)
        }

        btnCriarTrabalho.setOnClickListener {
            val intent = Intent(this, CriarTrabalhoActivity::class.java)
            intent.putExtra("equipeId", equipeIdAtual)
            startActivity(intent)
        }
    }

    private fun iniciarListenerTrabalhos() {
        listenerTrabalhos?.remove()

        val eqId = equipeIdAtual ?: return

        listenerTrabalhos = db.collection("trabalhos")
            .whereEqualTo("equipeId", eqId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Erro listener: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                todosTrabalhos = snapshot.documents
                aplicarFiltro()
            }
    }

    private fun aplicarFiltro() {
        val filtrados = if (filtroAtual == "todos") {
            todosTrabalhos
        } else {
            todosTrabalhos.filter {
                it.getString("status") == filtroAtual
            }
        }

        carregarTrabalhosNoLayout(filtrados, containerTrabalhos)
    }

    private fun atualizarBotoesFiltro(
        btnTodos: TextView,
        btnPendente: TextView,
        btnProgresso: TextView,
        btnConcluido: TextView
    ) {
        val inativo = android.graphics.Color.parseColor("#3D2B27")
        val ativo = android.graphics.Color.parseColor("#F5E6D0")
        val textoInativo = android.graphics.Color.WHITE
        val textoAtivo = android.graphics.Color.parseColor("#1C1311")

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

    private fun configurarTelaVazia() {
        val btnCriarEquipeVazio = findViewById<Button>(R.id.btnCriarEquipeVazio)
        btnCriarEquipeVazio.setOnClickListener {
            startActivity(Intent(this, CriarEquipeActivity::class.java))
        }
    }

    private fun carregarTrabalhosNoLayout(
        trabalhos: List<DocumentSnapshot>,
        container: LinearLayout
    ) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)

        if (trabalhos.isEmpty()) {
            val txtVazio = TextView(this).apply {
                text = getString(R.string.entregar_nenhum_trabalho)
                setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                textSize = 14f
                setPadding(0, 16, 0, 16)
            }
            container.addView(txtVazio)
            return
        }

        // ✅ Fallbacks traduzidos UMA VEZ
        val fallbackSemTitulo = getString(R.string.meus_trabalhos_sem_titulo)
        val fallbackSemPrazo = getString(R.string.meus_trabalhos_sem_prazo)
        val labelPendente = getString(R.string.status_pendente)
        val labelProgresso = getString(R.string.status_em_progresso)
        val labelConcluido = getString(R.string.status_concluido)

        trabalhos.forEach { doc ->
            val itemView = inflater.inflate(R.layout.item_trabalho_dashboard, container, false)

            val txtTitulo = itemView.findViewById<TextView>(R.id.txtTituloItem)
            val txtStatus = itemView.findViewById<TextView>(R.id.txtStatusItem)
            val txtPrazo = itemView.findViewById<TextView>(R.id.txtPrazoItem)

            txtTitulo.text = doc.getString("titulo") ?: fallbackSemTitulo
            txtPrazo.text = getString(
                R.string.meus_trabalhos_entrega_formatado,
                doc.getString("prazo") ?: fallbackSemPrazo
            )

            val status = doc.getString("status") ?: "pendente"
            when (status) {
                "pendente" -> {
                    txtStatus.text = labelPendente
                    txtStatus.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                }
                "em_progresso" -> {
                    txtStatus.text = labelProgresso
                    txtStatus.setTextColor(android.graphics.Color.parseColor("#F5E6D0"))
                }
                "concluido" -> {
                    txtStatus.text = labelConcluido
                    txtStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                }
            }

            container.addView(itemView)
        }
    }

    companion object {
        private const val TAG = "PRODUTOS"
        private const val PREFS_NAME = "CTR_PREFS"
        private const val KEY_NOME_USUARIO = "nomeUsuario"
    }
}