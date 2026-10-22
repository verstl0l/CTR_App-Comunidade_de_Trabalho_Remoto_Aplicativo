package com.example.plataformaremota

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AceitarConviteActivity : BaseActivity() {

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

        // ✅ CORREÇÃO 1: valida sessão antes de qualquer coisa
        if (emailUsuario.isEmpty()) {
            Toast.makeText(this, getString(R.string.chat_sessao_expirada), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        conviteId = intent.getStringExtra("conviteId") ?: ""
        equipeId = intent.getStringExtra("equipeId") ?: ""
        nomeEquipe = intent.getStringExtra("nomeEquipe") ?: getString(R.string.geral_equipe)
        descricaoEquipe = intent.getStringExtra("descricaoEquipe") ?: getString(R.string.geral_sem_descricao)
        nomeRemetente = intent.getStringExtra("nomeRemetente") ?: getString(R.string.geral_usuario)

        if (conviteId.isEmpty() || equipeId.isEmpty()) {
            Toast.makeText(this, getString(R.string.aceitar_convite_invalido), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val btnVoltar = findViewById<Button>(R.id.btnVoltarAceitarConvite)
        val txtQuemConvidou = findViewById<TextView>(R.id.txtQuemConvidou)
        val txtNomeEquipe = findViewById<TextView>(R.id.txtNomeEquipeConvite)
        val txtDescricao = findViewById<TextView>(R.id.txtDescricaoEquipeConvite)
        val btnAceitar = findViewById<Button>(R.id.btnAceitarConvite)
        val btnRecusar = findViewById<Button>(R.id.btnRecusarConvite)
        val btnDepois = findViewById<Button>(R.id.btnDepoisConvite)

        txtQuemConvidou.text = getString(R.string.aceitar_convite_convidado_por, nomeRemetente)
        txtNomeEquipe.text = nomeEquipe
        txtDescricao.text = descricaoEquipe

        lifecycleScope.launch {
            try {
                val doc = db.collection("convites_equipe").document(conviteId).get().await()
                if (!doc.exists()) {
                    Toast.makeText(
                        this@AceitarConviteActivity,
                        getString(R.string.aceitar_convite_nao_encontrado),
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                    return@launch
                }

                nomeEquipe = doc.getString("nomeEquipe") ?: nomeEquipe
                descricaoEquipe = doc.getString("descricaoEquipe") ?: descricaoEquipe
                nomeRemetente = doc.getString("nomeRemetente") ?: nomeRemetente
                equipeId = doc.getString("equipeId") ?: equipeId

                // Só sobrescreve o nome se vier preenchido (evita "Convidado por " vazio)
                if (nomeRemetente.isNotBlank()) {
                    txtQuemConvidou.text =
                        getString(R.string.aceitar_convite_convidado_por, nomeRemetente)
                }
                txtNomeEquipe.text = nomeEquipe
                txtDescricao.text = descricaoEquipe
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar convite", e)
            }
        }

        btnVoltar.setOnClickListener { finish() }
        btnDepois.setOnClickListener { finish() }
        btnAceitar.setOnClickListener { aceitarConvite() }
        btnRecusar.setOnClickListener { recusarConvite() }
    }

    private fun aceitarConvite() {
        // ✅ CORREÇÃO 3: valida equipeId antes de aceitar (evita equipe fantasma)
        if (equipeId.isBlank()) {
            Toast.makeText(this, getString(R.string.aceitar_convite_invalido), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                db.collection("convites_equipe").document(conviteId)
                    .update("status", "aceito").await()

                val userDoc = db.collection("usuarios").document(emailUsuario).get().await()
                val nomeUsuario = userDoc.getString("nome") ?: getString(R.string.geral_usuario)

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
                    val membroId = "${emailUsuario}_${equipeId}"
                    db.collection("membros_equipe").document(membroId).set(membro).await()

                    HistoricoEquipeHelper.registrarEntradaAsync(
                        emailUsuario = emailUsuario,
                        equipeId = equipeId
                    )
                }

                Toast.makeText(
                    this@AceitarConviteActivity,
                    getString(R.string.aceitar_convite_bem_vindo, nomeEquipe),
                    Toast.LENGTH_SHORT
                ).show()

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

    private fun recusarConvite() {
        lifecycleScope.launch {
            try {
                db.collection("convites_equipe").document(conviteId)
                    .update("status", "recusado").await()
                Toast.makeText(
                    this@AceitarConviteActivity,
                    getString(R.string.aceitar_convite_recusado),
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

    private fun notificarDonoConviteAceito() {
        lifecycleScope.launch {
            try {
                val conviteDoc = db.collection("convites_equipe").document(conviteId).get().await()
                val emailRemetente = conviteDoc.getString("emailRemetente") ?: ""
                if (emailRemetente.isEmpty()) return@launch

                val userDoc = db.collection("usuarios").document(emailUsuario).get().await()
                val nomeUsuario = userDoc.getString("nome") ?: getString(R.string.geral_usuario)

                NotificacaoHelper.criar(
                    destinatario = emailRemetente,
                    tipo = "convite_aceito",
                    titulo = getString(R.string.aceitar_convite_notif_titulo),
                    mensagem = getString(R.string.aceitar_convite_notif_msg, nomeUsuario, nomeEquipe),
                    referenciaId = equipeId,
                    referenciaTipo = "equipe",
                    remetente = emailUsuario,
                    nomeRemetente = nomeUsuario
                )
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao notificar", e)
            }
        }
    }

    companion object {
        private const val TAG = "ACEITAR_CONVITE"
    }
}