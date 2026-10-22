package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class participantes : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // Views do layout
    private lateinit var containerTarefas: LinearLayout
    private lateinit var containerVazio: LinearLayout
    private lateinit var txtNomeEquipe: TextView
    private lateinit var txtAvatarSigla: TextView

    private var emailUsuario: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_participantes)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""

        // Valida sessão
        if (emailUsuario.isEmpty()) {
            Toast.makeText(this, getString(R.string.chat_sessao_expirada), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Vincula as views do layout
        containerTarefas = findViewById(R.id.containerTarefasParticipante)
        containerVazio    = findViewById(R.id.containerVazioParticipantes)
        txtNomeEquipe     = findViewById(R.id.txtNomeEquipeParticipante)
        txtAvatarSigla    = findViewById(R.id.txtAvatarSiglaParticipante)

        configurarBottomNavigation()

        // Carrega equipe + tarefas do Firestore
        carregarDados()
    }

    private fun configurarBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.selectedItemId = R.id.nav_profile

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
                    startActivity(Intent(this, NotificacoesActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_profile -> true
                else -> false
            }
        }
    }

    // ============================================================
    // CARREGAMENTO DE DADOS (FIRESTORE REAL)
    // ============================================================

    /**
     * Fluxo:
     * 1. Busca membro_equipe do usuário → pega equipeId
     * 2. Busca nome da equipe
     * 3. Busca trabalhos onde participanteEmail == email do usuário
     * 4. Renderiza lista
     */
    private fun carregarDados() {
        lifecycleScope.launch {
            try {
                // 1. Busca a equipe do usuário
                val membroSnap = db.collection("membros_equipe")
                    .whereEqualTo("email", emailUsuario)
                    .limit(1)
                    .get()
                    .await()

                if (membroSnap.isEmpty) {
                    // Usuário não está em nenhuma equipe
                    txtNomeEquipe.text = getString(R.string.equipe_default_nome)
                    txtAvatarSigla.text = getString(R.string.entregar_avatar_sigla)
                    renderizarTarefas(emptyList())
                    return@launch
                }

                val equipeId = membroSnap.documents[0].getString("equipeId") ?: ""
                if (equipeId.isEmpty()) {
                    txtNomeEquipe.text = getString(R.string.equipe_default_nome)
                    txtAvatarSigla.text = getString(R.string.entregar_avatar_sigla)
                    renderizarTarefas(emptyList())
                    return@launch
                }

                // 2. Busca o nome da equipe
                val equipeDoc = db.collection("equipes").document(equipeId).get().await()
                val nomeEquipe = equipeDoc.getString("nome")
                    ?: getString(R.string.equipe_default_nome)

                txtNomeEquipe.text = nomeEquipe
                txtAvatarSigla.text = gerarSigla(nomeEquipe)

                // 3. Busca trabalhos onde o usuário é o participante
                val trabalhosSnap = db.collection("trabalhos")
                    .whereEqualTo("equipeId", equipeId)
                    .whereEqualTo("participanteEmail", emailUsuario)
                    .get()
                    .await()

                val lista = trabalhosSnap.documents.map { doc ->
                    Tarefa(
                        id = doc.id,
                        titulo = doc.getString("titulo") ?: "",
                        descricao = doc.getString("descricao") ?: "",
                        prazo = doc.getString("prazo") ?: "",
                        status = doc.getString("status") ?: "pendente"
                    )
                }

                renderizarTarefas(lista)

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar dados", e)
                Toast.makeText(
                    this@participantes,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Gera uma sigla de até 2 letras a partir do nome da equipe.
     * Ex: "Marketing Estratégico" -> "ME"
     */
    private fun gerarSigla(nome: String): String {
        val partes = nome.trim().split(" ").filter { it.isNotBlank() }
        return when {
            partes.isEmpty() -> getString(R.string.equipe_default_nome).take(2).uppercase()
            partes.size == 1 -> partes[0].take(2).uppercase()
            else -> (partes[0].first().toString() + partes[1].first().toString()).uppercase()
        }
    }

    /**
     * Renderiza a lista de tarefas no container dinâmico.
     * Se a lista estiver vazia, exibe o estado vazio.
     */
    private fun renderizarTarefas(listaTarefas: List<Tarefa>) {
        containerTarefas.removeAllViews()

        if (listaTarefas.isEmpty()) {
            containerVazio.visibility = View.VISIBLE
            containerTarefas.visibility = View.GONE
            return
        }

        containerVazio.visibility = View.GONE
        containerTarefas.visibility = View.VISIBLE

        val inflater = LayoutInflater.from(this)
        listaTarefas.forEach { tarefa ->
            val card = inflater.inflate(
                R.layout.item_tarefa_participante,
                containerTarefas,
                false
            )

            // Título
            card.findViewById<TextView>(R.id.txtTituloTarefa).text =
                tarefa.titulo.ifBlank { getString(R.string.meus_trabalhos_sem_titulo) }

            // Status
            card.findViewById<TextView>(R.id.txtStatusTarefa).text =
                getString(statusStringRes(tarefa.status))

            // Prazo
            card.findViewById<TextView>(R.id.txtPrazoTarefa).text =
                getString(
                    R.string.meus_trabalhos_entrega_formatado,
                    tarefa.prazo.ifBlank { getString(R.string.meus_trabalhos_sem_prazo) }
                )

            // Descrição
            card.findViewById<TextView>(R.id.txtDescricaoTarefa).text =
                tarefa.descricao.ifBlank { getString(R.string.meus_trabalhos_sem_descricao) }

            // Botão de ação conforme o status
            val btnAcao = card.findViewById<Button>(R.id.btnAcaoTarefa)
            configurarBotaoAcao(btnAcao, tarefa)

            containerTarefas.addView(card)
        }
    }

    /**
     * Retorna o resource string correspondente ao status da tarefa.
     */
    private fun statusStringRes(status: String): Int = when (status) {
        "pendente"     -> R.string.status_pendente
        "em_progresso" -> R.string.status_em_progresso
        "concluido"    -> R.string.status_concluido
        else           -> R.string.status_pendente
    }

    /**
     * Configura o texto e a ação do botão de acordo com o status atual.
     * Atualiza o status no Firestore e recarrega a lista.
     */
    private fun configurarBotaoAcao(btn: Button, tarefa: Tarefa) {
        when (tarefa.status) {
            "pendente" -> {
                btn.setText(R.string.participantes_botao_iniciar)
                btn.setOnClickListener {
                    atualizarStatus(tarefa.id, "em_progresso")
                }
            }
            "em_progresso" -> {
                btn.setText(R.string.participantes_botao_concluir)
                btn.setOnClickListener {
                    atualizarStatus(tarefa.id, "concluido")
                }
            }
            "concluido" -> {
                btn.setText(R.string.participantes_botao_reabrir)
                btn.setOnClickListener {
                    atualizarStatus(tarefa.id, "pendente")
                }
            }
        }
    }

    /**
     * Atualiza o status do trabalho no Firestore e recarrega a lista.
     */
    private fun atualizarStatus(trabalhoId: String, novoStatus: String) {
        lifecycleScope.launch {
            try {
                db.collection("trabalhos").document(trabalhoId)
                    .update("status", novoStatus).await()

                Toast.makeText(
                    this@participantes,
                    getString(R.string.entregar_status_atualizado),
                    Toast.LENGTH_SHORT
                ).show()

                // Recarrega a lista
                carregarDados()

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao atualizar status", e)
                Toast.makeText(
                    this@participantes,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Data class interna para a tarefa.
     */
    data class Tarefa(
        val id: String = "",
        val titulo: String = "",
        val descricao: String = "",
        val prazo: String = "",
        val status: String = "pendente"
    )

    companion object {
        private const val TAG = "PARTICIPANTES"
    }
}