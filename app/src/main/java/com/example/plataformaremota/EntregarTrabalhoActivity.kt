package com.example.plataformaremota

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class EntregarTrabalhoActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var equipeId: String = ""
    private var filtroAtual: String = "todos"
    private var ehDono: Boolean = false

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
                        Toast.makeText(
                            this@EntregarTrabalhoActivity,
                            getString(R.string.entregar_nao_membro),
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                        return@launch
                    }

                    equipeId = membro.documents[0].getString("equipeId") ?: ""
                }

                val equipeDoc = db.collection("equipes").document(equipeId).get().await()
                txtNomeEquipe.text = equipeDoc.getString("nome")
                    ?: getString(R.string.entregar_equipe_fallback)

                val criadorEmail = equipeDoc.getString("criadorEmail") ?: ""
                ehDono = criadorEmail.equals(email, ignoreCase = true)

                btnChatEquipe.setOnClickListener {
                    val intent = Intent(this@EntregarTrabalhoActivity, ChatEquipeActivity::class.java)
                    intent.putExtra("equipeId", equipeId)
                    startActivity(intent)
                }

                carregarTrabalhosFiltrados(containerTrabalhos)

            } catch (e: Exception) {
                Log.e(TAG, "Erro: ${e.message}")
            }
        }

        configurarBottomNavigation(R.id.nav_groups)
    }

    private fun atualizarBotoesFiltro(
        btnTodos: TextView,
        btnPendente: TextView,
        btnProgresso: TextView,
        btnConcluido: TextView
    ) {
        val inativo = Color.parseColor("#3D2B27")
        val ativo = Color.parseColor("#F5E6D0")
        val textoInativo = Color.WHITE
        val textoAtivo = Color.parseColor("#1C1311")

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
                Log.e(TAG, "Erro filtro: ${e.message}")
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
                text = getString(R.string.entregar_nenhum_trabalho)
                setTextColor(Color.parseColor("#9E9E9E"))
                textSize = 14f
                setPadding(0, 60, 0, 60)
                gravity = Gravity.CENTER
            }
            container.addView(txtVazio)
            return
        }

        // ✅ Fallbacks traduzidos capturados UMA VEZ
        val fallbackSemTitulo = getString(R.string.meus_trabalhos_sem_titulo)
        val fallbackSemDescricao = getString(R.string.meus_trabalhos_sem_descricao)
        val fallbackSemPrazo = getString(R.string.meus_trabalhos_sem_prazo)
        val textoZero = getString(R.string.entregar_contador_zero)

        trabalhos.forEach { doc ->
            val trabalhoId = doc.id
            val titulo = doc.getString("titulo") ?: fallbackSemTitulo
            val descricao = doc.getString("descricao") ?: fallbackSemDescricao
            val prazo = doc.getString("prazo") ?: fallbackSemPrazo
            val status = doc.getString("status") ?: "pendente"

            val view = inflater.inflate(R.layout.item_trabalho_membro, container, false)

            view.findViewById<TextView>(R.id.txtTituloTrabalhoMembro).text = titulo
            view.findViewById<TextView>(R.id.txtDescricaoTrabalhoMembro).text = descricao
            view.findViewById<TextView>(R.id.txtPrazoTrabalhoMembro).text =
                getString(R.string.meus_trabalhos_entrega_formatado, prazo)

            val txtStatus = view.findViewById<TextView>(R.id.txtStatusTrabalhoMembro)
            atualizarStatusUI(txtStatus, status)

            val btnEntregar = view.findViewById<Button>(R.id.btnEntregarTrabalho)

            if (ehDono) {
                btnEntregar.visibility = View.GONE
            } else {
                btnEntregar.visibility = View.VISIBLE
                atualizarBotao(btnEntregar, status)
                btnEntregar.setOnClickListener {
                    mostrarOpcoesStatus(trabalhoId, status, container)
                }
            }

            val btnComentarios = view.findViewById<Button>(R.id.btnComentariosMembro)
            lifecycleScope.launch {
                try {
                    val total = ComentarioHelper.contar(trabalhoId)
                    btnComentarios.text = total.toString()
                } catch (e: Exception) {
                    btnComentarios.text = textoZero
                }
            }

            btnComentarios.setOnClickListener {
                val intent = Intent(this@EntregarTrabalhoActivity, ComentariosTrabalhoActivity::class.java)
                intent.putExtra("trabalhoId", trabalhoId)
                intent.putExtra("tituloTrabalho", titulo)
                startActivity(intent)
            }

            val btnAnexos = view.findViewById<Button>(R.id.btnAnexosMembro)
            lifecycleScope.launch {
                try {
                    val anexos = doc.reference.collection("anexos").get().await()
                    btnAnexos.text = anexos.size().toString()
                } catch (e: Exception) {
                    btnAnexos.text = textoZero
                }
            }

            btnAnexos.setOnClickListener {
                val intent = Intent(this@EntregarTrabalhoActivity, AnexosTrabalhoActivity::class.java)
                intent.putExtra("trabalhoId", trabalhoId)
                intent.putExtra("tituloTrabalho", titulo)
                startActivity(intent)
            }

            container.addView(view)
        }
    }

    private fun atualizarStatusUI(txtStatus: TextView, status: String) {
        when (status) {
            "pendente" -> {
                txtStatus.text = getString(R.string.status_pendente)
                txtStatus.setTextColor(Color.parseColor("#9E9E9E"))
            }
            "em_progresso" -> {
                txtStatus.text = getString(R.string.status_em_progresso)
                txtStatus.setTextColor(Color.parseColor("#F5E6D0"))
            }
            "concluido" -> {
                txtStatus.text = getString(R.string.status_concluido)
                txtStatus.setTextColor(Color.parseColor("#4CAF50"))
            }
        }
    }

    private fun atualizarBotao(btn: Button, status: String) {
        when (status) {
            "pendente" -> {
                btn.text = getString(R.string.entregar_btn_iniciar)
                btn.setBackgroundColor(Color.parseColor("#F5E6D0"))
                btn.setTextColor(Color.parseColor("#1C1311"))
            }
            "em_progresso" -> {
                btn.text = getString(R.string.entregar_btn_concluir)
                btn.setBackgroundColor(Color.parseColor("#4CAF50"))
                btn.setTextColor(Color.WHITE)
            }
            "concluido" -> {
                btn.text = getString(R.string.entregar_btn_reabrir)
                btn.setBackgroundColor(Color.parseColor("#3D2B27"))
                btn.setTextColor(Color.WHITE)
            }
        }
    }

    private fun mostrarOpcoesStatus(trabalhoId: String, statusAtual: String, container: LinearLayout) {
        // ✅ Opções traduzidas capturadas ANTES do dialog
        val labelPendente = getString(R.string.status_pendente)
        val labelProgresso = getString(R.string.status_em_progresso)
        val labelConcluido = getString(R.string.status_concluido)

        val opcoes = arrayOf(labelPendente, labelProgresso, labelConcluido)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.entregar_alterar_status_titulo))
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

                Toast.makeText(
                    this@EntregarTrabalhoActivity,
                    getString(R.string.entregar_status_atualizado),
                    Toast.LENGTH_SHORT
                ).show()
                carregarTrabalhosFiltrados(container)

            } catch (e: Exception) {
                Toast.makeText(
                    this@EntregarTrabalhoActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        private const val TAG = "ENTREGAR_TRABALHO"
    }
}