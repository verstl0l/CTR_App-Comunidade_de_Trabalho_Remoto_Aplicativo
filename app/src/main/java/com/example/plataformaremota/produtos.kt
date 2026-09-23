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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class produtos : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var equipeIdAtual: String? = null
    private var filtroAtual: String = "todos"

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
                    configurarBottomNavigation()
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
                    configurarBottomNavigation()
                    return@launch
                }

                setContentView(R.layout.activity_produtos_vazio)
                configurarTelaVazia()
                configurarBottomNavigation()

            } catch (e: Exception) {
                Log.e("PRODUTOS", "Erro: ${e.message}")
                Toast.makeText(this@produtos, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun configurarDashboard(email: String) {
        val prefs = getSharedPreferences("CTR_PREFS", MODE_PRIVATE)
        val nomeUsuario = prefs.getString("nomeUsuario", "Usuário") ?: "Usuário"

        val txtNomeEquipe = findViewById<TextView>(R.id.txtNomeEquipeDashboard)
        val txtCriador = findViewById<TextView>(R.id.txtCriadorEquipeDashboard)
        val txtDescricao = findViewById<TextView>(R.id.txtDescricaoEquipeDashboard)
        val txtLogo = findViewById<TextView>(R.id.txtLogoEquipe)
        val btnConfig = findViewById<ImageView>(R.id.btnConfigEquipe)
        val btnCriarTrabalho = findViewById<Button>(R.id.btnCriarTrabalho)
        val containerTrabalhos = findViewById<LinearLayout>(R.id.containerTrabalhosRecentes)

        val btnChatEquipe = findViewById<Button>(R.id.btnChatEquipe)
        btnChatEquipe.setOnClickListener {
            val intent = Intent(this, ChatEquipeActivity::class.java)
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
            carregarTrabalhosFiltrados(containerTrabalhos)
        }
        btnFiltroPendente.setOnClickListener {
            filtroAtual = "pendente"
            atualizarBotoesFiltro(btnFiltroTodos, btnFiltroPendente, btnFiltroProgresso, btnFiltroConcluido)
            carregarTrabalhosFiltrados(containerTrabalhos)
        }
        btnFiltroProgresso.setOnClickListener {
            filtroAtual = "em_progresso"
            atualizarBotoesFiltro(btnFiltroTodos, btnFiltroPendente, btnFiltroProgresso, btnFiltroConcluido)
            carregarTrabalhosFiltrados(containerTrabalhos)
        }
        btnFiltroConcluido.setOnClickListener {
            filtroAtual = "concluido"
            atualizarBotoesFiltro(btnFiltroTodos, btnFiltroPendente, btnFiltroProgresso, btnFiltroConcluido)
            carregarTrabalhosFiltrados(containerTrabalhos)
        }

        txtCriador.text = "Criado por $nomeUsuario"

        lifecycleScope.launch {
            try {
                val equipeDoc = db.collection("equipes").document(equipeIdAtual!!).get().await()
                val nome = equipeDoc.getString("nome") ?: "Equipe"
                val descricao = equipeDoc.getString("descricao") ?: ""

                txtNomeEquipe.text = nome
                txtDescricao.text = descricao.ifEmpty { "Nenhuma descrição" }

                val iniciais = nome.split(" ")
                    .take(2)
                    .map { palavra -> palavra.firstOrNull()?.uppercase() ?: "" }
                    .joinToString("")
                txtLogo.text = iniciais.ifEmpty { "EQ" }

                carregarTrabalhosFiltrados(containerTrabalhos)

            } catch (e: Exception) {
                Log.e("PRODUTOS", "Erro ao carregar equipe: ${e.message}")
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

    private fun atualizarBotoesFiltro(
        btnTodos: TextView,
        btnPendente: TextView,
        btnProgresso: TextView,
        btnConcluido: TextView
    ) {
        btnTodos.setBackgroundColor(android.graphics.Color.parseColor("#3D2B27"))
        btnTodos.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
        btnPendente.setBackgroundColor(android.graphics.Color.parseColor("#3D2B27"))
        btnPendente.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
        btnProgresso.setBackgroundColor(android.graphics.Color.parseColor("#3D2B27"))
        btnProgresso.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
        btnConcluido.setBackgroundColor(android.graphics.Color.parseColor("#3D2B27"))
        btnConcluido.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))

        when (filtroAtual) {
            "todos" -> {
                btnTodos.setBackgroundColor(android.graphics.Color.parseColor("#F5E6D0"))
                btnTodos.setTextColor(android.graphics.Color.parseColor("#1C1311"))
            }
            "pendente" -> {
                btnPendente.setBackgroundColor(android.graphics.Color.parseColor("#F5E6D0"))
                btnPendente.setTextColor(android.graphics.Color.parseColor("#1C1311"))
            }
            "em_progresso" -> {
                btnProgresso.setBackgroundColor(android.graphics.Color.parseColor("#F5E6D0"))
                btnProgresso.setTextColor(android.graphics.Color.parseColor("#1C1311"))
            }
            "concluido" -> {
                btnConcluido.setBackgroundColor(android.graphics.Color.parseColor("#F5E6D0"))
                btnConcluido.setTextColor(android.graphics.Color.parseColor("#1C1311"))
            }
        }
    }

    private fun carregarTrabalhosFiltrados(container: LinearLayout) {
        lifecycleScope.launch {
            try {
                val trabalhos = db.collection("trabalhos")
                    .whereEqualTo("equipeId", equipeIdAtual)
                    .get()
                    .await()

                val filtrados = if (filtroAtual == "todos") {
                    trabalhos.documents
                } else {
                    trabalhos.documents.filter {
                        it.getString("status") == filtroAtual
                    }
                }

                carregarTrabalhosNoLayout(filtrados, container)

            } catch (e: Exception) {
                Log.e("PRODUTOS", "Erro filtro: ${e.message}")
            }
        }
    }

    private fun configurarTelaVazia() {
        val btnCriarEquipeVazio = findViewById<Button>(R.id.btnCriarEquipeVazio)
        btnCriarEquipeVazio.setOnClickListener {
            startActivity(Intent(this, CriarEquipeActivity::class.java))
        }
    }

    private fun carregarTrabalhosNoLayout(
        trabalhos: List<com.google.firebase.firestore.DocumentSnapshot>,
        container: LinearLayout
    ) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)

        if (trabalhos.isEmpty()) {
            val txtVazio = TextView(this).apply {
                text = "Nenhum trabalho encontrado"
                setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                textSize = 14f
                setPadding(0, 16, 0, 16)
            }
            container.addView(txtVazio)
            return
        }

        trabalhos.forEach { doc ->
            val itemView = inflater.inflate(R.layout.item_trabalho_dashboard, container, false)

            val txtTitulo = itemView.findViewById<TextView>(R.id.txtTituloItem)
            val txtStatus = itemView.findViewById<TextView>(R.id.txtStatusItem)
            val txtPrazo = itemView.findViewById<TextView>(R.id.txtPrazoItem)

            txtTitulo.text = doc.getString("titulo") ?: "Sem título"
            txtPrazo.text = "Entrega: ${doc.getString("prazo") ?: "Sem prazo"}"

            val status = doc.getString("status") ?: "pendente"
            when (status) {
                "pendente" -> {
                    txtStatus.text = "Pendente"
                    txtStatus.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                }
                "em_progresso" -> {
                    txtStatus.text = "Em Progresso"
                    txtStatus.setTextColor(android.graphics.Color.parseColor("#F5E6D0"))
                }
                "concluido" -> {
                    txtStatus.text = "Concluído"
                    txtStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                }
            }

            container.addView(itemView)
        }
    }

    private fun configurarBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_chat -> {
                    startActivity(Intent(this, ListaConversasActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_groups -> {
                    startActivity(Intent(this, MinhasEquipesActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_notifications -> {
                    startActivity(Intent(this, notificacao::class.java))
                    finish()
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, perfil::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }

        lifecycleScope.launch {
            BadgeHelper.atualizarBadgeChat(this@produtos, bottomNav)
        }
    }
}