package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class EntregarTrabalhoActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var equipeId: String = ""
    private var filtroAtual: String = "todos"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_entregar_trabalho)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        val email = auth.currentUser?.email ?: ""

        val txtNomeEquipe = findViewById<TextView>(R.id.txtNomeEquipeMembro)
        val containerTrabalhos = findViewById<LinearLayout>(R.id.containerTrabalhosMembro)

        val btnChatEquipe = findViewById<Button>(R.id.btnChatEquipeMembro)

        val btnFiltroTodos = findViewById<TextView>(R.id.btnFiltroTodosMembro)
        val btnFiltroPendente = findViewById<TextView>(R.id.btnFiltroPendenteMembro)
        val btnFiltroProgresso = findViewById<TextView>(R.id.btnFiltroProgressoMembro)
        val btnFiltroConcluido = findViewById<TextView>(R.id.btnFiltroConcluidoMembro)

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

        val equipeIdIntent = intent.getStringExtra("equipeId")

        lifecycleScope.launch {
            try {
                if (equipeIdIntent != null) {
                    equipeId = equipeIdIntent
                } else {
                    val membro = db.collection("membros_equipe")
                        .whereEqualTo("email", email)
                        .limit(1)
                        .get()
                        .await()

                    if (membro.isEmpty) {
                        Toast.makeText(this@EntregarTrabalhoActivity, "Você não é membro de nenhuma equipe", Toast.LENGTH_SHORT).show()
                        finish()
                        return@launch
                    }

                    equipeId = membro.documents[0].getString("equipeId") ?: ""
                }

                val equipeDoc = db.collection("equipes").document(equipeId).get().await()
                txtNomeEquipe.text = equipeDoc.getString("nome") ?: "Equipe"

                btnChatEquipe.setOnClickListener {
                    val intent = Intent(this@EntregarTrabalhoActivity, ChatEquipeActivity::class.java)
                    intent.putExtra("equipeId", equipeId)
                    startActivity(intent)
                }

                carregarTrabalhosFiltrados(containerTrabalhos)

            } catch (e: Exception) {
                Log.e("ENTREGAR_TRABALHO", "Erro: ${e.message}")
            }
        }

        configurarBottomNavigation()
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
                    .whereEqualTo("equipeId", equipeId)
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
                Log.e("ENTREGAR_TRABALHO", "Erro filtro: ${e.message}")
            }
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
                setPadding(0, 60, 0, 60)
                gravity = android.view.Gravity.CENTER
            }
            container.addView(txtVazio)
            return
        }

        trabalhos.forEach { doc ->
            val trabalhoId = doc.id
            val titulo = doc.getString("titulo") ?: ""
            val descricao = doc.getString("descricao") ?: ""
            val prazo = doc.getString("prazo") ?: ""
            val status = doc.getString("status") ?: "pendente"

            val view = inflater.inflate(R.layout.item_trabalho_membro, container, false)

            view.findViewById<TextView>(R.id.txtTituloTrabalhoMembro).text = titulo
            view.findViewById<TextView>(R.id.txtDescricaoTrabalhoMembro).text = descricao
            view.findViewById<TextView>(R.id.txtPrazoTrabalhoMembro).text = "Entrega: $prazo"

            val txtStatus = view.findViewById<TextView>(R.id.txtStatusTrabalhoMembro)
            atualizarStatusUI(txtStatus, status)

            val btnEntregar = view.findViewById<Button>(R.id.btnEntregarTrabalho)
            atualizarBotao(btnEntregar, status)

            btnEntregar.setOnClickListener {
                mostrarOpcoesStatus(trabalhoId, status, container)
            }

            container.addView(view)
        }
    }

    private fun atualizarStatusUI(txtStatus: TextView, status: String) {
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
    }

    private fun atualizarBotao(btn: Button, status: String) {
        when (status) {
            "pendente" -> {
                btn.text = "INICIAR"
                btn.setBackgroundColor(android.graphics.Color.parseColor("#F5E6D0"))
                btn.setTextColor(android.graphics.Color.parseColor("#1C1311"))
            }
            "em_progresso" -> {
                btn.text = "CONCLUIR"
                btn.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
                btn.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
            }
            "concluido" -> {
                btn.text = "REABRIR"
                btn.setBackgroundColor(android.graphics.Color.parseColor("#3D2B27"))
                btn.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
            }
        }
    }

    private fun mostrarOpcoesStatus(trabalhoId: String, statusAtual: String, container: LinearLayout) {
        val opcoes = arrayOf("Pendente", "Em Progresso", "Concluído")

        AlertDialog.Builder(this)
            .setTitle("Alterar status")
            .setItems(opcoes) { _, which ->
                val novoStatus = when (which) {
                    0 -> "pendente"
                    1 -> "em_progresso"
                    2 -> "concluido"
                    else -> "pendente"
                }
                atualizarStatus(trabalhoId, novoStatus, container)
            }
            .show()
    }

    private fun atualizarStatus(trabalhoId: String, novoStatus: String, container: LinearLayout) {
        lifecycleScope.launch {
            try {
                db.collection("trabalhos").document(trabalhoId)
                    .update("status", novoStatus).await()

                Toast.makeText(this@EntregarTrabalhoActivity, "Status atualizado!", Toast.LENGTH_SHORT).show()
                carregarTrabalhosFiltrados(container)

            } catch (e: Exception) {
                Toast.makeText(this@EntregarTrabalhoActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
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
            BadgeHelper.atualizarBadgeChat(this@EntregarTrabalhoActivity, bottomNav)
        }
    }
}