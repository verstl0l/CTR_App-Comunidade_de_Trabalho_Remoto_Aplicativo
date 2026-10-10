package com.example.plataformaremota

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class GerenciarMembrosGrupoActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""
    private var grupoId: String = ""
    private var equipeId: String = ""
    private var criadorGrupo: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gerenciar_membros_grupo)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""
        grupoId = intent.getStringExtra("grupoId") ?: ""
        equipeId = intent.getStringExtra("equipeId") ?: ""

        if (grupoId.isEmpty() || equipeId.isEmpty()) {
            Toast.makeText(
                this,
                getString(R.string.gerenciar_membros_grupo_nao_encontrado),
                Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }

        val btnVoltar = findViewById<Button>(R.id.btnVoltarGerenciarMembros)
        btnVoltar.text = getString(R.string.voltar)
        btnVoltar.setOnClickListener { finish() }

        val btnAdicionarMembro = findViewById<Button>(R.id.btnAdicionarMembro)
        btnAdicionarMembro.setOnClickListener { mostrarMembrosDisponiveis() }

        carregarInfo()
    }

    private fun carregarInfo() {
        lifecycleScope.launch {
            try {
                val grupoDoc = db.collection("grupos").document(grupoId).get().await()
                criadorGrupo = grupoDoc.getString("criadorEmail") ?: ""

                val membroEquipe = db.collection("membros_equipe")
                    .whereEqualTo("equipeId", equipeId)
                    .whereEqualTo("email", emailUsuario)
                    .limit(1)
                    .get()
                    .await()

                val ehAdmin = !membroEquipe.isEmpty &&
                        membroEquipe.documents[0].getString("funcao") == "administrador"

                val ehCriadorGrupo = criadorGrupo == emailUsuario

                if (!ehCriadorGrupo && !ehAdmin) {
                    Toast.makeText(
                        this@GerenciarMembrosGrupoActivity,
                        getString(R.string.gerenciar_membros_grupo_sem_permissao),
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                    return@launch
                }

                carregarMembros()

            } catch (e: Exception) {
                Log.e(TAG, getString(R.string.erro_generico, e.message ?: ""))
            }
        }
    }

    private fun carregarMembros() {
        val container = findViewById<LinearLayout>(R.id.containerMembrosGrupo)
        container.removeAllViews()

        lifecycleScope.launch {
            try {
                val grupoDoc = db.collection("grupos").document(grupoId).get().await()
                val membros = grupoDoc.get("membros") as? List<*> ?: emptyList<Any>()

                val inflater = LayoutInflater.from(this@GerenciarMembrosGrupoActivity)

                if (membros.isEmpty()) {
                    val txtVazio = TextView(this@GerenciarMembrosGrupoActivity).apply {
                        text = getString(R.string.gerenciar_membros_grupo_nenhum_membro)
                        setTextColor(ContextCompat.getColor(this@GerenciarMembrosGrupoActivity, R.color.text_secondary))
                        textSize = 14f
                        setPadding(0, 16, 0, 16)
                    }
                    container.addView(txtVazio)
                    return@launch
                }

                // ✅ Fallbacks traduzidos capturados UMA VEZ
                val nomeFallback = getString(R.string.gerenciar_membros_grupo_usuario_fallback)

                for (emailMembro in membros) {
                    val email = emailMembro as? String ?: continue

                    val usuarioDoc = db.collection("usuarios").document(email).get().await()
                    val nome = usuarioDoc.getString("nome") ?: nomeFallback

                    val view = inflater.inflate(android.R.layout.simple_list_item_2, container, false)
                    val t1 = view.findViewById<TextView>(android.R.id.text1)
                    val t2 = view.findViewById<TextView>(android.R.id.text2)

                    t1.text = nome
                    t1.setTextColor(ContextCompat.getColor(this@GerenciarMembrosGrupoActivity, R.color.text_primary))
                    t2.text = email
                    t2.setTextColor(ContextCompat.getColor(this@GerenciarMembrosGrupoActivity, R.color.text_secondary))
                    view.setPadding(0, 24, 0, 24)

                    if (email != criadorGrupo) {
                        view.setOnLongClickListener {
                            AlertDialog.Builder(this@GerenciarMembrosGrupoActivity)
                                .setTitle(getString(R.string.gerenciar_membros_grupo_remover_titulo))
                                .setMessage(getString(R.string.gerenciar_membros_grupo_remover_msg, nome))
                                .setPositiveButton(getString(R.string.gerenciar_membros_grupo_remover_confirmar)) { _, _ ->
                                    removerMembro(email, membros)
                                }
                                .setNegativeButton(R.string.cancelar, null)
                                .show()
                            true
                        }
                    }

                    container.addView(view)
                }

            } catch (e: Exception) {
                Log.e(TAG, getString(R.string.erro_generico, e.message ?: ""))
            }
        }
    }

    private fun removerMembro(email: String, membrosAtuais: List<*>) {
        lifecycleScope.launch {
            try {
                val novosMembros = membrosAtuais.filter { it != email }

                db.collection("grupos").document(grupoId)
                    .update("membros", novosMembros).await()

                removerGrupoIdDoUsuario(email, grupoId)

                Toast.makeText(
                    this@GerenciarMembrosGrupoActivity,
                    getString(R.string.gerenciar_membros_grupo_removido_sucesso),
                    Toast.LENGTH_SHORT
                ).show()
                carregarMembros()

            } catch (e: Exception) {
                Toast.makeText(
                    this@GerenciarMembrosGrupoActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun mostrarMembrosDisponiveis() {
        lifecycleScope.launch {
            try {
                val membrosEquipe = db.collection("membros_equipe")
                    .whereEqualTo("equipeId", equipeId)
                    .get()
                    .await()

                val grupoDoc = db.collection("grupos").document(grupoId).get().await()
                val membrosGrupo = grupoDoc.get("membros") as? List<*> ?: emptyList<Any>()

                val disponiveis = membrosEquipe.documents.filter { doc ->
                    val email = doc.getString("email") ?: ""
                    !membrosGrupo.contains(email)
                }

                if (disponiveis.isEmpty()) {
                    Toast.makeText(
                        this@GerenciarMembrosGrupoActivity,
                        getString(R.string.gerenciar_membros_grupo_todos_no_grupo),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val nomes = mutableListOf<String>()
                val emails = mutableListOf<String>()
                val nomeFallback = getString(R.string.gerenciar_membros_grupo_usuario_fallback)

                for (doc in disponiveis) {
                    val email = doc.getString("email") ?: ""
                    val nome = doc.getString("nome") ?: nomeFallback
                    nomes.add(nome)
                    emails.add(email)
                }

                AlertDialog.Builder(this@GerenciarMembrosGrupoActivity)
                    .setTitle(getString(R.string.gerenciar_membros_grupo_adicionar_titulo))
                    .setItems(nomes.toTypedArray()) { _, which ->
                        adicionarMembro(emails[which], membrosGrupo)
                    }
                    .setNegativeButton(R.string.cancelar, null)
                    .show()

            } catch (e: Exception) {
                Toast.makeText(
                    this@GerenciarMembrosGrupoActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun adicionarMembro(email: String, membrosAtuais: List<*>) {
        lifecycleScope.launch {
            try {
                val novosMembros = membrosAtuais.toMutableList()
                novosMembros.add(email)

                db.collection("grupos").document(grupoId)
                    .update("membros", novosMembros).await()

                adicionarGrupoIdAoUsuario(email, grupoId)

                val grupoDoc = db.collection("grupos").document(grupoId).get().await()
                val nomeGrupo = grupoDoc.getString("nomeGrupo")
                    ?: getString(R.string.gerenciar_membros_grupo_grupo_fallback)
                val nomeRemetente = db.collection("usuarios").document(emailUsuario)
                    .get().await().getString("nome") ?: emailUsuario

                NotificacaoHelper.notificarMembroAdicionado(
                    destinatario = email,
                    remetente = emailUsuario,
                    nomeRemetente = nomeRemetente,
                    grupoId = grupoId,
                    nomeGrupo = nomeGrupo
                )

                Toast.makeText(
                    this@GerenciarMembrosGrupoActivity,
                    getString(R.string.gerenciar_membros_grupo_adicionado_sucesso),
                    Toast.LENGTH_SHORT
                ).show()
                carregarMembros()

            } catch (e: Exception) {
                Toast.makeText(
                    this@GerenciarMembrosGrupoActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private suspend fun adicionarGrupoIdAoUsuario(email: String, grupoId: String) {
        try {
            val userRef = db.collection("usuarios").document(email)
            val userDoc = userRef.get().await()
            val ids = (userDoc.get("gruposIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

            if (grupoId !in ids) {
                userRef.update("gruposIds", ids + grupoId).await()
                Log.d(TAG_DENORM, "gruposIds + $grupoId para $email")
            }
        } catch (e: Exception) {
            Log.e(TAG_DENORM, "Erro ao adicionar grupoId: ${e.message}")
        }
    }

    private suspend fun removerGrupoIdDoUsuario(email: String, grupoId: String) {
        try {
            val userRef = db.collection("usuarios").document(email)
            val userDoc = userRef.get().await()
            val ids = (userDoc.get("gruposIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

            if (grupoId in ids) {
                userRef.update("gruposIds", ids - grupoId).await()
                Log.d(TAG_DENORM, "gruposIds - $grupoId para $email")
            }
        } catch (e: Exception) {
            Log.e(TAG_DENORM, "Erro ao remover grupoId: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "GERENCIAR_MEMBROS"
        private const val TAG_DENORM = "GRUPO_MEMBROS"
    }
}