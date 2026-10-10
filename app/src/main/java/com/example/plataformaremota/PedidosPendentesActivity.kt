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
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PedidosPendentesActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""
    private var equipeId: String = ""
    private var nomeEquipe: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pedidos_pendentes)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""
        equipeId = intent.getStringExtra("equipeId") ?: ""

        if (equipeId.isEmpty()) {
            Toast.makeText(this, getString(R.string.pedidos_equipe_nao_encontrada), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val btnVoltar = findViewById<Button>(R.id.btnVoltarPedidos)
        btnVoltar.setOnClickListener { finish() }

        // Nome provisório da equipe enquanto carrega
        nomeEquipe = getString(R.string.pedidos_equipe_fallback)

        lifecycleScope.launch {
            try {
                val equipeDoc = db.collection("equipes").document(equipeId).get().await()
                nomeEquipe = equipeDoc.getString("nome") ?: getString(R.string.pedidos_equipe_fallback)
                findViewById<TextView>(R.id.txtNomeEquipePedidos).text = nomeEquipe
            } catch (e: Exception) {
                Log.e(TAG, "Erro nome equipe: ${e.message}")
            }
        }

        carregarPedidos()
    }

    private fun carregarPedidos() {
        val container = findViewById<LinearLayout>(R.id.containerPedidos)
        val containerVazio = findViewById<LinearLayout>(R.id.containerVazioPedidos)
        container.removeAllViews()

        lifecycleScope.launch {
            try {
                val pedidos = db.collection("pedidos_entrada")
                    .whereEqualTo("equipeId", equipeId)
                    .whereEqualTo("status", "pendente")
                    .get()
                    .await()

                if (pedidos.isEmpty) {
                    containerVazio.visibility = View.VISIBLE
                    return@launch
                }

                containerVazio.visibility = View.GONE

                val inflater = LayoutInflater.from(this@PedidosPendentesActivity)
                val fallbackUsuario = getString(R.string.pedidos_usuario_fallback)

                pedidos.documents.forEach { doc ->
                    val pedidoId = doc.id
                    val nomeSolicitante = doc.getString("nomeSolicitante") ?: fallbackUsuario
                    val emailSolicitante = doc.getString("emailSolicitante") ?: ""
                    val profissao = doc.getString("profissaoSolicitante") ?: ""
                    val motivos = doc.getString("motivos") ?: ""
                    val especialidades = doc.getString("especialidades") ?: ""

                    val view = inflater.inflate(R.layout.item_pedido_entrada, container, false)

                    view.findViewById<TextView>(R.id.txtNomeSolicitante).text = nomeSolicitante
                    view.findViewById<TextView>(R.id.txtEmailSolicitante).text = emailSolicitante

                    val txtProfissao = view.findViewById<TextView>(R.id.txtProfissaoSolicitante)
                    if (profissao.isNotEmpty()) {
                        txtProfissao.text = profissao
                        txtProfissao.visibility = View.VISIBLE
                    } else {
                        txtProfissao.visibility = View.GONE
                    }

                    view.findViewById<TextView>(R.id.txtMotivos).text = motivos
                    view.findViewById<TextView>(R.id.txtEspecialidades).text = especialidades

                    view.findViewById<Button>(R.id.btnVerPerfilSolicitante).setOnClickListener {
                        val i = Intent(
                            this@PedidosPendentesActivity,
                            PerfilUsuarioActivity::class.java
                        )
                        i.putExtra("emailOutro", emailSolicitante)
                        startActivity(i)
                    }

                    view.findViewById<Button>(R.id.btnAceitarPedido).setOnClickListener {
                        confirmarAceitar(pedidoId, nomeSolicitante, emailSolicitante)
                    }

                    view.findViewById<Button>(R.id.btnRecusarPedido).setOnClickListener {
                        confirmarRecusar(pedidoId, nomeSolicitante, emailSolicitante)
                    }

                    container.addView(view)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Erro: ${e.message}")
                Toast.makeText(
                    this@PedidosPendentesActivity,
                    getString(R.string.pedidos_erro_carregar, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun confirmarAceitar(pedidoId: String, nome: String, email: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.pedidos_aceitar_titulo))
            .setMessage(getString(R.string.pedidos_aceitar_msg, nome, nomeEquipe))
            .setPositiveButton(getString(R.string.pedidos_aceitar_confirmar)) { _, _ ->
                aceitarPedido(pedidoId, nome, email)
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun aceitarPedido(pedidoId: String, nome: String, email: String) {
        lifecycleScope.launch {
            try {
                db.collection("pedidos_entrada").document(pedidoId)
                    .update("status", "aceito").await()

                val membroId = "${email}_${equipeId}"
                val jaExiste = db.collection("membros_equipe").document(membroId).get().await()

                if (!jaExiste.exists()) {
                    val membro = hashMapOf(
                        "equipeId" to equipeId,
                        "email" to email,
                        "nome" to nome,
                        "funcao" to "membro",
                        "entrouEm" to System.currentTimeMillis()
                    )
                    db.collection("membros_equipe").document(membroId).set(membro).await()
                }

                // ⚠️ nomeRemetente continua fixo: valor salvo no BD
                NotificacaoHelper.notificarPedidoAceito(
                    destinatario = email,
                    remetente = emailUsuario,
                    nomeRemetente = getString(R.string.pedidos_nome_remetente_admin),
                    equipeId = equipeId,
                    nomeEquipe = nomeEquipe
                )

                Toast.makeText(
                    this@PedidosPendentesActivity,
                    getString(R.string.pedidos_aceito_sucesso, nome),
                    Toast.LENGTH_SHORT
                ).show()

                carregarPedidos()

            } catch (e: Exception) {
                Log.e(TAG, "Erro aceitar: ${e.message}")
                Toast.makeText(
                    this@PedidosPendentesActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun confirmarRecusar(pedidoId: String, nome: String, email: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.pedidos_recusar_titulo))
            .setMessage(getString(R.string.pedidos_recusar_msg, nome))
            .setPositiveButton(getString(R.string.pedidos_recusar_confirmar)) { _, _ ->
                recusarPedido(pedidoId, nome, email)
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun recusarPedido(pedidoId: String, nome: String, email: String) {
        lifecycleScope.launch {
            try {
                db.collection("pedidos_entrada").document(pedidoId)
                    .update("status", "recusado").await()

                // ⚠️ Este título/mensagem vai pro BD em português
                // (refatoração futura pode mover para i18n runtime)
                NotificacaoHelper.criar(
                    destinatario = email,
                    tipo = "pedido_recusado",
                    titulo = getString(R.string.pedidos_notif_recusado_titulo),
                    mensagem = getString(R.string.pedidos_notif_recusado_msg, nomeEquipe),
                    referenciaId = equipeId,
                    referenciaTipo = "equipe",
                    remetente = emailUsuario,
                    nomeRemetente = getString(R.string.pedidos_nome_remetente_admin)
                )

                Toast.makeText(
                    this@PedidosPendentesActivity,
                    getString(R.string.pedidos_recusado_sucesso, nome),
                    Toast.LENGTH_SHORT
                ).show()

                carregarPedidos()

            } catch (e: Exception) {
                Log.e(TAG, "Erro recusar: ${e.message}")
                Toast.makeText(
                    this@PedidosPendentesActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        private const val TAG = "PEDIDOS_PENDENTES"
    }
}