package com.example.plataformaremota

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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class GerenciarMembrosGrupoActivity : AppCompatActivity() {

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
            Toast.makeText(this, "Grupo não encontrado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val btnVoltar = findViewById<Button>(R.id.btnVoltarGerenciarMembros)
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

                // Verifica permissão (dono do grupo ou admin da equipe)
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
                    Toast.makeText(this@GerenciarMembrosGrupoActivity, "Você não tem permissão", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }

                carregarMembros()

            } catch (e: Exception) {
                Log.e("GERENCIAR_MEMBROS", "Erro: ${e.message}")
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
                        text = "Nenhum membro no grupo"
                        setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                        textSize = 14f
                        setPadding(0, 16, 0, 16)
                    }
                    container.addView(txtVazio)
                    return@launch
                }

                for (emailMembro in membros) {
                    val email = emailMembro as? String ?: continue

                    val usuarioDoc = db.collection("usuarios").document(email).get().await()
                    val nome = usuarioDoc.getString("nome") ?: email

                    val view = inflater.inflate(android.R.layout.simple_list_item_2, container, false)
                    val t1 = view.findViewById<TextView>(android.R.id.text1)
                    val t2 = view.findViewById<TextView>(android.R.id.text2)

                    t1.text = nome
                    t1.setTextColor(android.graphics.Color.WHITE)
                    t2.text = email
                    t2.setTextColor(android.graphics.Color.GRAY)
                    view.setPadding(0, 24, 0, 24)

                    // Só pode remover se não for o criador do grupo
                    if (email != criadorGrupo) {
                        view.setOnLongClickListener {
                            AlertDialog.Builder(this@GerenciarMembrosGrupoActivity)
                                .setTitle("Remover membro")
                                .setMessage("Deseja remover $nome do grupo?")
                                .setPositiveButton("Remover") { _, _ ->
                                    removerMembro(email, membros)
                                }
                                .setNegativeButton("Cancelar", null)
                                .show()
                            true
                        }
                    }

                    container.addView(view)
                }

            } catch (e: Exception) {
                Log.e("GERENCIAR_MEMBROS", "Erro: ${e.message}")
            }
        }
    }

    private fun removerMembro(email: String, membrosAtuais: List<*>) {
        lifecycleScope.launch {
            try {
                val novosMembros = membrosAtuais.filter { it != email }

                db.collection("grupos").document(grupoId)
                    .update("membros", novosMembros).await()

                Toast.makeText(this@GerenciarMembrosGrupoActivity, "Membro removido", Toast.LENGTH_SHORT).show()
                carregarMembros()

            } catch (e: Exception) {
                Toast.makeText(this@GerenciarMembrosGrupoActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun mostrarMembrosDisponiveis() {
        lifecycleScope.launch {
            try {
                // Busca membros da equipe
                val membrosEquipe = db.collection("membros_equipe")
                    .whereEqualTo("equipeId", equipeId)
                    .get()
                    .await()

                // Busca membros do grupo
                val grupoDoc = db.collection("grupos").document(grupoId).get().await()
                val membrosGrupo = grupoDoc.get("membros") as? List<*> ?: emptyList<Any>()

                // Filtra quem NÃO está no grupo
                val disponiveis = membrosEquipe.documents.filter { doc ->
                    val email = doc.getString("email") ?: ""
                    !membrosGrupo.contains(email)
                }

                if (disponiveis.isEmpty()) {
                    Toast.makeText(this@GerenciarMembrosGrupoActivity, "Todos os membros já estão no grupo", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Monta lista de nomes
                val nomes = mutableListOf<String>()
                val emails = mutableListOf<String>()

                for (doc in disponiveis) {
                    val email = doc.getString("email") ?: ""
                    val nome = doc.getString("nome") ?: email
                    nomes.add(nome)
                    emails.add(email)
                }

                AlertDialog.Builder(this@GerenciarMembrosGrupoActivity)
                    .setTitle("Adicionar membro")
                    .setItems(nomes.toTypedArray()) { _, which ->
                        adicionarMembro(emails[which], membrosGrupo)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()

            } catch (e: Exception) {
                Toast.makeText(this@GerenciarMembrosGrupoActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
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

                Toast.makeText(this@GerenciarMembrosGrupoActivity, "Membro adicionado", Toast.LENGTH_SHORT).show()
                carregarMembros()

            } catch (e: Exception) {
                Toast.makeText(this@GerenciarMembrosGrupoActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}