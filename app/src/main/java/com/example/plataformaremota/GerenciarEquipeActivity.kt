package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.plataformaremota.data.database.AppDatabase
import com.example.plataformaremota.data.entity.Equipe
import com.example.plataformaremota.data.entity.PedidoEntrada
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

class GerenciarEquipeActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase
    private var equipeAtual: Equipe? = null
    private lateinit var prefs: android.content.SharedPreferences
    private var email: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gerenciar_equipe)

        database = AppDatabase.getDatabase(this)
        prefs = getSharedPreferences("CTR_PREFS", MODE_PRIVATE)
        email = prefs.getString("emailUsuario", "") ?: ""

        val edtNomeEquipe = findViewById<EditText>(R.id.edtGerenciarNomeEquipe)
        val edtDescEquipe = findViewById<EditText>(R.id.edtGerenciarDescEquipe)
        val switchPrivada = findViewById<Switch>(R.id.switchEquipePrivada)
        val btnSalvarEquipe = findViewById<Button>(R.id.btnSalvarAlteracoesEquipe)
        val btnExcluirEquipe = findViewById<Button>(R.id.btnExcluirEquipe)
        val btnVoltar = findViewById<Button>(R.id.btnVoltarGerenciar)

        btnVoltar.setOnClickListener { finish() }

        // Carrega dados da equipe
        lifecycleScope.launch {
            equipeAtual = database.equipeDao().buscarPorCriador(email)
            equipeAtual?.let {
                edtNomeEquipe.setText(it.nome)
                edtDescEquipe.setText(it.descricao)
                switchPrivada.isChecked = it.privada
                carregarTrabalhos(it.id)
                carregarPedidos(it.id)
            }
        }

        // Salvar alterações na equipe
        btnSalvarEquipe.setOnClickListener {
            val novoNome = edtNomeEquipe.text.toString().trim()
            val novaDesc = edtDescEquipe.text.toString().trim()
            val privada = switchPrivada.isChecked

            if (novoNome.isEmpty()) {
                Toast.makeText(this, "O nome da equipe é obrigatório", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            equipeAtual?.let { equipe ->
                lifecycleScope.launch {
                    val equipeAtualizada = equipe.copy(
                        nome = novoNome,
                        descricao = novaDesc,
                        privada = privada
                    )
                    database.equipeDao().atualizar(equipeAtualizada)
                    equipeAtual = equipeAtualizada

                    prefs.edit().putString("nomeEquipe_$email", novoNome).apply()
                    Toast.makeText(
                        this@GerenciarEquipeActivity,
                        "Dados da equipe atualizados!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        // Botão Excluir Equipe
        btnExcluirEquipe.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Excluir Equipe")
                .setMessage("Tem certeza? Todos os trabalhos serão removidos permanentemente.")
                .setPositiveButton("Sim, excluir") { _, _ ->
                    equipeAtual?.let { equipe ->
                        lifecycleScope.launch {
                            try {
                                val trabalhos = database.trabalhoDao().listarPorEquipe(equipe.id)
                                trabalhos.forEach { database.trabalhoDao().deletar(it) }

                                database.equipeDao().deletar(equipe)

                                prefs.edit()
                                    .remove("temEquipe_$email")
                                    .remove("nomeEquipe_$email")
                                    .apply()

                                Toast.makeText(
                                    this@GerenciarEquipeActivity,
                                    "🗑️ Equipe excluída!",
                                    Toast.LENGTH_SHORT
                                ).show()

                                startActivity(Intent(this@GerenciarEquipeActivity, produtos::class.java))
                                finish()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    this@GerenciarEquipeActivity,
                                    "Erro: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        // Bottom Navigation
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_groups -> {
                    startActivity(Intent(this, produtos::class.java))
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
    }

    private fun carregarPedidos(equipeId: Int) {
        val container = findViewById<LinearLayout>(R.id.containerPedidos)
        container.removeAllViews()

        lifecycleScope.launch {
            val pedidos = database.pedidoEntradaDao().listarPendentesPorEquipe(equipeId)
            val inflater = LayoutInflater.from(this@GerenciarEquipeActivity)

            if (pedidos.isEmpty()) {
                val txtVazio = TextView(this@GerenciarEquipeActivity).apply {
                    text = "Nenhum pedido pendente"
                    setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                    textSize = 14f
                    setPadding(0, 16, 0, 16)
                }
                container.addView(txtVazio)
                return@launch
            }

            pedidos.forEach { pedido ->
                val view = inflater.inflate(android.R.layout.simple_list_item_2, container, false)
                val text1 = view.findViewById<TextView>(android.R.id.text1)
                val text2 = view.findViewById<TextView>(android.R.id.text2)

                text1.text = "${pedido.nomeSolicitante} (${pedido.emailSolicitante})"
                text1.setTextColor(android.graphics.Color.WHITE)
                text2.text = "Motivos: ${pedido.motivos}\nEspecialidades: ${pedido.especialidades}"
                text2.setTextColor(android.graphics.Color.GRAY)

                view.setPadding(0, 16, 0, 16)

                view.setOnClickListener {
                    AlertDialog.Builder(this@GerenciarEquipeActivity)
                        .setTitle("Pedido de ${pedido.nomeSolicitante}")
                        .setMessage("Motivos: ${pedido.motivos}\n\nEspecialidades: ${pedido.especialidades}")
                        .setPositiveButton("Aceitar") { _, _ ->
                            lifecycleScope.launch {
                                database.pedidoEntradaDao().atualizar(pedido.copy(status = "aceito"))
                                carregarPedidos(equipeId)
                                Toast.makeText(
                                    this@GerenciarEquipeActivity,
                                    "✅ Pedido aceito!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .setNegativeButton("Recusar") { _, _ ->
                            lifecycleScope.launch {
                                database.pedidoEntradaDao().atualizar(pedido.copy(status = "recusado"))
                                carregarPedidos(equipeId)
                                Toast.makeText(
                                    this@GerenciarEquipeActivity,
                                    "❌ Pedido recusado",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .show()
                }

                container.addView(view)
            }
        }
    }

    private fun carregarTrabalhos(equipeId: Int) {
        val container = findViewById<LinearLayout>(R.id.containerTrabalhos)
        container.removeAllViews()

        lifecycleScope.launch {
            val trabalhos = database.trabalhoDao().listarPorEquipe(equipeId)
            val inflater = LayoutInflater.from(this@GerenciarEquipeActivity)

            if (trabalhos.isEmpty()) {
                val txtVazio = TextView(this@GerenciarEquipeActivity).apply {
                    text = "Nenhum trabalho publicado ainda"
                    setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                    textSize = 14f
                    setPadding(0, 16, 0, 16)
                }
                container.addView(txtVazio)
                return@launch
            }

            trabalhos.forEach { trabalho ->
                val view = inflater.inflate(android.R.layout.simple_list_item_2, container, false)
                val text1 = view.findViewById<TextView>(android.R.id.text1)
                val text2 = view.findViewById<TextView>(android.R.id.text2)

                text1.text = trabalho.titulo
                text1.setTextColor(android.graphics.Color.WHITE)
                text2.text = "${trabalho.categoria} - ${trabalho.prazo}"
                text2.setTextColor(android.graphics.Color.GRAY)

                view.setPadding(0, 16, 0, 16)

                // Clique curto → Convidar para trabalho
                view.setOnClickListener {
                    val intent = Intent(this@GerenciarEquipeActivity, ConvidarTrabalhoActivity::class.java)
                    intent.putExtra("trabalhoId", trabalho.id)
                    intent.putExtra("tituloTrabalho", trabalho.titulo)
                    startActivity(intent)
                }

                // Clique longo → Excluir trabalho
                view.setOnLongClickListener {
                    lifecycleScope.launch {
                        database.trabalhoDao().deletar(trabalho)
                        carregarTrabalhos(equipeId)
                        Toast.makeText(
                            this@GerenciarEquipeActivity,
                            "Trabalho removido!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    true
                }

                container.addView(view)
            }
        }
    }
}