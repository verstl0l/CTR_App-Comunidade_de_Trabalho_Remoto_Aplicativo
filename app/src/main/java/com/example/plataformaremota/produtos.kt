package com.example.plataformaremota

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.plataformaremota.data.database.AppDatabase
import com.example.plataformaremota.data.entity.CriarTrabalhoActivity
import com.example.plataformaremota.data.entity.Trabalho
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

class produtos : AppCompatActivity() {

    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = AppDatabase.getDatabase(this)

        val prefs = getSharedPreferences("CTR_PREFS", Context.MODE_PRIVATE)
        val email = prefs.getString("emailUsuario", "") ?: ""

        // ✅ VERIFICA DIRETO NO BANCO DE DADOS
        lifecycleScope.launch {
            val equipe = database.equipeDao().buscarPorCriador(email)

            if (equipe != null) {
                setContentView(R.layout.activity_produtos)
                configurarDashboard(email)
            } else {
                setContentView(R.layout.activity_produtos_vazio)
                configurarTelaVazia()
            }

            configurarBottomNavigation()
        }
    }

    override fun onResume() {
        super.onResume()

        val prefs = getSharedPreferences("CTR_PREFS", Context.MODE_PRIVATE)
        val email = prefs.getString("emailUsuario", "") ?: ""

        // Se já tem dashboard carregado, atualiza
        if (findViewById<TextView>(R.id.txtNomeEquipeDashboard) != null) {
            configurarDashboard(email)
        }
    }

    private fun configurarDashboard(email: String) {
        val prefs = getSharedPreferences("CTR_PREFS", Context.MODE_PRIVATE)
        val nomeUsuario = prefs.getString("nomeUsuario", "Usuário") ?: "Usuário"

        val txtNomeEquipe = findViewById<TextView>(R.id.txtNomeEquipeDashboard)
        val txtCriador = findViewById<TextView>(R.id.txtCriadorEquipeDashboard)
        val txtDescricao = findViewById<TextView>(R.id.txtDescricaoEquipeDashboard)
        val txtLogo = findViewById<TextView>(R.id.txtLogoEquipe)
        val btnConfig = findViewById<ImageView>(R.id.btnConfigEquipe)
        val btnCriarTrabalho = findViewById<Button>(R.id.btnCriarTrabalho)
        val containerTrabalhos = findViewById<LinearLayout>(R.id.containerTrabalhosRecentes)

        txtCriador.text = "Criado por $nomeUsuario"

        lifecycleScope.launch {
            val equipe = database.equipeDao().buscarPorCriador(email)
            equipe?.let {
                txtNomeEquipe.text = it.nome
                txtDescricao.text = it.descricao.ifEmpty { "Nenhuma descrição" }

                val iniciais = it.nome.split(" ")
                    .take(2)
                    .map { palavra -> palavra.firstOrNull()?.uppercase() ?: "" }
                    .joinToString("")
                txtLogo.text = iniciais.ifEmpty { "EQ" }

                val trabalhos = database.trabalhoDao().listarPorEquipe(it.id)
                carregarTrabalhosNoLayout(trabalhos, containerTrabalhos)
            }
        }

        btnConfig.setOnClickListener {
            startActivity(Intent(this, GerenciarEquipeActivity::class.java))
        }

        btnCriarTrabalho.setOnClickListener {
            startActivity(Intent(this, CriarTrabalhoActivity::class.java))
        }
    }

    private fun configurarTelaVazia() {
        val btnCriarEquipeVazio = findViewById<Button>(R.id.btnCriarEquipeVazio)
        btnCriarEquipeVazio.setOnClickListener {
            // ✅ Vai para a tela de CRIAR EQUIPE (formulário)
            startActivity(Intent(this, CriarEquipeActivity::class.java))
        }
    }

    private fun carregarTrabalhosNoLayout(trabalhos: List<Trabalho>, container: LinearLayout) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)

        if (trabalhos.isEmpty()) {
            val txtVazio = TextView(this).apply {
                text = "Nenhum trabalho publicado ainda"
                setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                textSize = 14f
                setPadding(0, 16, 0, 16)
            }
            container.addView(txtVazio)
            return
        }

        trabalhos.forEach { trabalho ->
            val itemView = inflater.inflate(R.layout.item_trabalho_dashboard, container, false)

            val txtTitulo = itemView.findViewById<TextView>(R.id.txtTituloItem)
            val txtStatus = itemView.findViewById<TextView>(R.id.txtStatusItem)
            val txtPrazo = itemView.findViewById<TextView>(R.id.txtPrazoItem)

            txtTitulo.text = trabalho.titulo
            txtStatus.text = "Pendente"
            txtPrazo.text = "Entrega: ${trabalho.prazo}"

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
                R.id.nav_groups -> true
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
    }
}