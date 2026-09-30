package com.example.plataformaremota

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
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

class PedidosPendentesActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""
    private var equipeId: String = ""
    private var nomeEquipe: String = "Equipe"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pedidos_pendentes)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""
        equipeId = intent.getStringExtra("equipeId") ?: ""

        if (equipeId.isEmpty()) {
            Toast.makeText(this, "Equipe não encontrada", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val btnVoltar = findViewById<Button>(R.id.btnVoltarPedidos)
        btnVoltar.setOnClickListener { finish() }

        // Carrega nome da equipe
        lifecycleScope.launch {
            try {
                val equipeDoc = db.collection("equipes").document(equipeId).get().await()
                nomeEquipe = equipeDoc.getString("nome") ?: "Equipe"
                findViewById<TextView>(R.id.txtNomeEquipePedidos).text = nomeEquipe
            } catch (e: Exception) {
                Log.e("PEDIDOS_PENDENTES", "Erro nome equipe: ${e.message}")
            }
        }

        carregarPedidos()
    }

    // ============================================================
    // CARREGAR PEDIDOS PENDENTES
    // ============================================================
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

                pedidos.documents.forEach { doc ->
                    val pedidoId = doc.id
                    val nomeSolicitante = doc.getString("nomeSolicitante") ?: "Usuário"
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

                    // Botão ver perfil
                    view.findViewById<Button>(R.id.btnVerPerfilSolicitante).setOnClickListener {
                        val i = android.content.Intent(
                            this@PedidosPendentesActivity,
                            PerfilUsuarioActivity::class.java
                        )
                        i.putExtra("emailOutro", emailSolicitante)
                        startActivity(i)
                    }

                    // Botão ACEITAR
                    view.findViewById<Button>(R.id.btnAceitarPedido).setOnClickListener {
                        confirmarAceitar(pedidoId, nomeSolicitante, emailSolicitante)
                    }

                    // Botão RECUSAR
                    view.findViewById<Button>(R.id.btnRecusarPedido).setOnClickListener {
                        confirmarRecusar(pedidoId, nomeSolicitante, emailSolicitante)
                    }

                    container.addView(view)
                }

            } catch (e: Exception) {
                Log.e("PEDIDOS_PENDENTES", "Erro: ${e.message}")
                Toast.makeText(
                    this@PedidosPendentesActivity,
                    "Erro ao carregar pedidos: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ============================================================
    // ACEITAR PEDIDO
    // ============================================================
    private fun confirmarAceitar(pedidoId: String, nome: String, email: String) {
        AlertDialog.Builder(this)
            .setTitle("Aceitar pedido")
            .setMessage("Aceitar $nome na equipe $nomeEquipe?")
            .setPositiveButton("Aceitar") { _, _ -> aceitarPedido(pedidoId, nome, email) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun aceitarPedido(pedidoId: String, nome: String, email: String) {
        lifecycleScope.launch {
            try {
                // 1. Atualiza status do pedido
                db.collection("pedidos_entrada").document(pedidoId)
                    .update("status", "aceito").await()

                // 2. Cria membro com ID determinístico
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

                // 3. Notifica o solicitante
                NotificacaoHelper.notificarPedidoAceito(
                    destinatario = email,
                    remetente = emailUsuario,
                    nomeRemetente = "Admin",
                    equipeId = equipeId,
                    nomeEquipe = nomeEquipe
                )

                Toast.makeText(
                    this@PedidosPendentesActivity,
                    "✅ $nome entrou na equipe!",
                    Toast.LENGTH_SHORT
                ).show()

                carregarPedidos()

            } catch (e: Exception) {
                Log.e("PEDIDOS_PENDENTES", "Erro aceitar: ${e.message}")
                Toast.makeText(
                    this@PedidosPendentesActivity,
                    "Erro: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ============================================================
    // RECUSAR PEDIDO
    // ============================================================
    private fun confirmarRecusar(pedidoId: String, nome: String, email: String) {
        AlertDialog.Builder(this)
            .setTitle("Recusar pedido")
            .setMessage("Recusar o pedido de $nome?")
            .setPositiveButton("Recusar") { _, _ -> recusarPedido(pedidoId, nome, email) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun recusarPedido(pedidoId: String, nome: String, email: String) {
        lifecycleScope.launch {
            try {
                // 1. Atualiza status
                db.collection("pedidos_entrada").document(pedidoId)
                    .update("status", "recusado").await()

                // 2. Notifica o solicitante (opcional, mas educado)
                NotificacaoHelper.criar(
                    destinatario = email,
                    tipo = "pedido_recusado",
                    titulo = "Pedido recusado",
                    mensagem = "Seu pedido para entrar em $nomeEquipe foi recusado",
                    referenciaId = equipeId,
                    referenciaTipo = "equipe",
                    remetente = emailUsuario,
                    nomeRemetente = "Admin"
                )

                Toast.makeText(
                    this@PedidosPendentesActivity,
                    "Pedido de $nome recusado",
                    Toast.LENGTH_SHORT
                ).show()

                carregarPedidos()

            } catch (e: Exception) {
                Log.e("PEDIDOS_PENDENTES", "Erro recusar: ${e.message}")
                Toast.makeText(
                    this@PedidosPendentesActivity,
                    "Erro: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}