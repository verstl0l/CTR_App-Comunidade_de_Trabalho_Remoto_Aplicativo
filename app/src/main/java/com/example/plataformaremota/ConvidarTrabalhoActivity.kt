package com.example.plataformaremota

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ConvidarTrabalhoActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var trabalhoId: String = ""
    private var tituloTrabalho: String = ""
    private var emailRemetente: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_convidar_trabalho)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailRemetente = auth.currentUser?.email ?: ""

        trabalhoId = intent.getStringExtra("trabalhoId") ?: ""
        tituloTrabalho = intent.getStringExtra("tituloTrabalho") ?: ""

        val txtTitulo = findViewById<TextView>(R.id.txtTituloTrabalhoConvite)
        val edtEmail = findViewById<EditText>(R.id.edtEmailConvidado)
        val btnEnviar = findViewById<Button>(R.id.btnEnviarConviteTrabalho)
        val btnVoltar = findViewById<Button>(R.id.btnVoltarConvidar)

        txtTitulo.text = getString(R.string.convidar_trabalho_prefixo, tituloTrabalho)

        btnVoltar.setOnClickListener { finish() }

        //  Clicar no campo abre lista de usuários disponíveis
        edtEmail.isFocusable = false
        edtEmail.isClickable = true
        edtEmail.setOnClickListener {
            SeletorUsuarioHelper.abrir(this, emailRemetente) { email, nome ->
                edtEmail.setText(email)
                Toast.makeText(this, "Selecionado: $nome", Toast.LENGTH_SHORT).show()
            }
        }

        btnEnviar.setOnClickListener {
            val emailConvidado = edtEmail.text.toString().trim().lowercase()

            if (emailConvidado.isEmpty()) {
                Toast.makeText(this, getString(R.string.gerenciar_selecione_usuario), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnEnviar.isEnabled = false
            btnEnviar.text = getString(R.string.pedir_entrada_enviando)

            lifecycleScope.launch {
                try {
                    val equipe = db.collection("equipes")
                        .whereEqualTo("criadorEmail", emailRemetente)
                        .limit(1)
                        .get()
                        .await()

                    val nomeEquipe = if (!equipe.isEmpty) {
                        equipe.documents[0].getString("nome") ?: getString(R.string.equipe_default_nome)
                    } else getString(R.string.equipe_default_nome)

                    val convite = hashMapOf(
                        "trabalhoId" to trabalhoId,
                        "tituloTrabalho" to tituloTrabalho,
                        "emailConvidado" to emailConvidado,
                        "emailRemetente" to emailRemetente,
                        "nomeEquipe" to nomeEquipe,
                        "status" to "pendente",
                        "criadoEm" to System.currentTimeMillis()
                    )

                    db.collection("convites_trabalho").add(convite).await()

                    Toast.makeText(
                        this@ConvidarTrabalhoActivity,
                        getString(R.string.convidar_trabalho_enviado, emailConvidado),
                        Toast.LENGTH_SHORT
                    ).show()

                    finish()

                } catch (e: Exception) {
                    Log.e("CONVITE_TRABALHO", "Erro: ${e.message}")
                    Toast.makeText(this@ConvidarTrabalhoActivity, getString(R.string.erro_generico, e.message ?: ""), Toast.LENGTH_LONG).show()
                    btnEnviar.isEnabled = true
                    btnEnviar.text = getString(R.string.convidar_trabalho_enviar)
                }
            }
        }

        configurarBottomNavigation(null)
    }

    // ============================================================
    // ✅ MOSTRA LISTA DE USUÁRIOS DISPONÍVEIS
    // ============================================================
    private fun mostrarUsuariosDisponiveis() {
        lifecycleScope.launch {
            try {
                val usuarios = db.collection("usuarios").get().await()

                val convites = db.collection("convites_trabalho")
                    .whereEqualTo("trabalhoId", trabalhoId)
                    .get()
                    .await()

                val emailsComConvite = convites.documents
                    .filter { it.getString("status") == "pendente" }
                    .mapNotNull { it.getString("emailConvidado") }
                    .toSet()

                val disponiveis = usuarios.documents
                    .mapNotNull { doc ->
                        val email = doc.id
                        val nome = doc.getString("nome") ?: email
                        val profissao = doc.getString("profissao") ?: ""
                        if (email != emailRemetente && email !in emailsComConvite) {
                            Triple(email, nome, profissao)
                        } else null
                    }
                    .sortedBy { it.second.lowercase() }

                if (disponiveis.isEmpty()) {
                    Toast.makeText(
                        this@ConvidarTrabalhoActivity,
                        getString(R.string.convidar_trabalho_sem_disponiveis),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val nomes = disponiveis.map { "${it.second} (${it.first})" }.toTypedArray()

                AlertDialog.Builder(this@ConvidarTrabalhoActivity)
                    .setTitle(getString(R.string.gerenciar_selecionar_usuario, disponiveis.size))
                    .setItems(nomes) { _, which ->
                        val (emailEscolhido, nomeEscolhido, _) = disponiveis[which]
                        val edtEmail = findViewById<EditText>(R.id.edtEmailConvidado)
                        edtEmail.setText(emailEscolhido)
                        Toast.makeText(
                            this@ConvidarTrabalhoActivity,
                            getString(R.string.gerenciar_selecionado, nomeEscolhido),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .setNegativeButton(R.string.cancelar, null)
                    .show()

            } catch (e: Exception) {
                Log.e("CONVITE_TRABALHO", "Erro lista: ${e.message}")

                val mensagem = if (e.message?.contains("PERMISSION_DENIED") == true) {
                    getString(R.string.convidar_trabalho_erro_permissao)
                } else {
                    getString(R.string.gerenciar_erro_usuarios, e.message ?: "")
                }

                Toast.makeText(
                    this@ConvidarTrabalhoActivity,
                    mensagem,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}