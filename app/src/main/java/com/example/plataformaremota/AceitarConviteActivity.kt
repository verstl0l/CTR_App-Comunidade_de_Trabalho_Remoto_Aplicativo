package com.example.plataformaremota

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Tela dedicada para aceitar ou recusar um convite de equipe.
 *
 * Recebe via Intent:
 *   - conviteId: ID do documento em convites_equipe
 *   - equipeId: ID da equipe
 *   - nomeEquipe: nome da equipe (opcional, para exibir rapido)
 *   - descricaoEquipe: descricao (opcional)
 *   - nomeRemetente: quem convidou (opcional)
 */
class AceitarConviteActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""

    private var conviteId: String = ""
    private var equipeId: String = ""
    private var nomeEquipe: String = ""
    private var descricaoEquipe: String = ""
    private var nomeRemetente: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aceitar_convite)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""

        conviteId = intent.getStringExtra("conviteId") ?: ""
        equipeId = intent.getStringExtra("equipeId") ?: ""
        nomeEquipe = intent.getStringExtra("nomeEquipe") ?: "Equipe"
        descricaoEquipe = intent.getStringExtra("descricaoEquipe") ?: "Sem descricao"
        nomeRemetente = intent.getStringExtra("nomeRemetente") ?: "Usuario"

        if (conviteId.isEmpty() || equipeId.isEmpty()) {
            Toast.makeText(this, "Convite invalido", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Bind das views
        val btnVoltar = findViewById<Button>(R.id.btnVoltarAceitarConvite)
        val txtQuemConvidou = findViewById<TextView>(R.id.txtQuemConvidou)
        val txtNomeEquipe = findViewById<TextView>(R.id.txtNomeEquipeConvite)
        val txtDescricao = findViewById<TextView>(R.id.txtDescricaoEquipeConvite)
        val btnAceitar = findViewById<Button>(R.id.btnAceitarConvite)
        val btnRecusar = findViewById<Button>(R.id.btnRecusarConvite)
        val btnDepois = findViewById<Button>(R.id.btnDepoisConvite)

        // Preenche dados iniciais
        txtQuemConvidou.text = "Convidado por $nomeRemetente"
        txtNomeEquipe.text = nomeEquipe
        txtDescricao.text = descricaoEquipe

        // Recarrega os dados direto do Firestore (mais confiavel)
        lifecycleScope.launch {
            try {
                val doc = db.collection("convites_equipe").document(conviteId).get().await()
                if (!doc.exists()) {
                    Toast.makeText(
                        this@AceitarConviteActivity,
                        "Convite nao encontrado",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                    return@launch
                }

                // Atualiza com os dados reais
                nomeEquipe = doc.getString("nomeEquipe") ?: nomeEquipe
                descricaoEquipe = doc.getString("descricaoEquipe") ?: descricaoEquipe
                nomeRemetente = doc.getString("nomeRemetente") ?: nomeRemetente
                equipeId = doc.getString("equipeId") ?: equipeId

                txtQuemConvidou.text = "Convidado por $nomeRemetente"
                txtNomeEquipe.text = nomeEquipe
                txtDescricao.text = descricaoEquipe
            } catch (e: Exception) {
                Log.e("ACEITAR_CONVITE", "Erro ao carregar convite: ${e.message}")
            }
        }

        // Listeners
        btnVoltar.setOnClickListener { finish() }
        btnDepois.setOnClickListener { finish() }
        btnAceitar.setOnClickListener { aceitarConvite() }
        btnRecusar.setOnClickListener { recusarConvite() }
    }

    // ============================================================
    // ACEITAR
    // ============================================================
    private fun aceitarConvite() {
        lifecycleScope.launch {
            try {
                // 1. Atualiza status do convite
                db.collection("convites_equipe").document(conviteId)
                    .update("status", "aceito").await()

                // 2. Busca nome do usuario para o registro de membro
                val userDoc = db.collection("usuarios").document(emailUsuario).get().await()
                val nomeUsuario = userDoc.getString("nome") ?: "Usuario"

                // 3. Verifica se ja e membro (evita duplicata)
                val jaMembro = db.collection("membros_equipe")
                    .whereEqualTo("equipeId", equipeId)
                    .whereEqualTo("email", emailUsuario)
                    .limit(1)
                    .get()
                    .await()

                if (jaMembro.isEmpty) {
                    val membro = hashMapOf(
                        "equipeId" to equipeId,
                        "email" to emailUsuario,
                        "nome" to nomeUsuario,
                        "funcao" to "membro",
                        "entrouEm" to System.currentTimeMillis()
                    )
                    db.collection("membros_equipe").add(membro).await()
                }

                Toast.makeText(
                    this@AceitarConviteActivity,
                    "Bem-vindo a $nomeEquipe!",
                    Toast.LENGTH_SHORT
                ).show()

                // 4. Notifica o dono que o convite foi aceito
                notificarDonoConviteAceito()

                finish()

            } catch (e: Exception) {
                Toast.makeText(
                    this@AceitarConviteActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ============================================================
    // RECUSAR
    // ============================================================
    private fun recusarConvite() {
        lifecycleScope.launch {
            try {
                db.collection("convites_equipe").document(conviteId)
                    .update("status", "recusado").await()

                Toast.makeText(
                    this@AceitarConviteActivity,
                    "Convite recusado",
                    Toast.LENGTH_SHORT
                ).show()

                finish()

            } catch (e: Exception) {
                Toast.makeText(
                    this@AceitarConviteActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ============================================================
    // NOTIFICAR O DONO
    // ============================================================
    private fun notificarDonoConviteAceito() {
        lifecycleScope.launch {
            try {
                val conviteDoc = db.collection("convites_equipe").document(conviteId).get().await()
                val emailRemetente = conviteDoc.getString("emailRemetente") ?: ""

                if (emailRemetente.isEmpty()) return@launch

                val userDoc = db.collection("usuarios").document(emailUsuario).get().await()
                val nomeUsuario = userDoc.getString("nome") ?: "Usuario"

                NotificacaoHelper.criar(
                    destinatario = emailRemetente,
                    tipo = "convite_aceito",
                    titulo = "Convite aceito",
                    mensagem = "$nomeUsuario aceitou entrar em $nomeEquipe",
                    referenciaId = equipeId,
                    referenciaTipo = "equipe",
                    remetente = emailUsuario,
                    nomeRemetente = nomeUsuario
                )
            } catch (e: Exception) {
                Log.e("ACEITAR_CONVITE", "Erro ao notificar: ${e.message}")
            }
        }
    }
}