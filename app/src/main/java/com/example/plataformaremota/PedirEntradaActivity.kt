package com.example.plataformaremota

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PedirEntradaActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pedir_entrada)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        val emailSolicitante = auth.currentUser?.email ?: ""

        val equipeId = intent.getStringExtra("equipeId") ?: ""
        val nomeEquipe = intent.getStringExtra("nomeEquipe") ?: ""

        val txtEquipe = findViewById<TextView>(R.id.txtNomeEquipePedido)
        val edtMotivos = findViewById<EditText>(R.id.edtMotivos)
        val edtEspecialidades = findViewById<EditText>(R.id.edtEspecialidades)
        val btnEnviar = findViewById<Button>(R.id.btnEnviarPedido)
        val btnVoltar = findViewById<Button>(R.id.btnVoltarPedir)

        txtEquipe.text = getString(R.string.pedir_entrada_equipe_prefixo, nomeEquipe)

        btnVoltar.setOnClickListener { finish() }

        // ✅ Verifica se já existe pedido pendente ao abrir a tela
        lifecycleScope.launch {
            try {
                val pedidos = db.collection("pedidos_entrada")
                    .whereEqualTo("equipeId", equipeId)
                    .whereEqualTo("emailSolicitante", emailSolicitante)
                    .whereEqualTo("status", "pendente")
                    .get()
                    .await()

                if (!pedidos.isEmpty) {
                    Toast.makeText(
                        this@PedirEntradaActivity,
                        getString(R.string.pedir_entrada_ja_enviado),
                        Toast.LENGTH_LONG
                    ).show()
                    btnEnviar.isEnabled = false
                    btnEnviar.text = getString(R.string.pedir_entrada_ja_enviado_botao)
                }
            } catch (e: Exception) {
                Log.e("PEDIR_ENTRADA", "Erro ao verificar: ${e.message}")
            }
        }

        btnEnviar.setOnClickListener {
            val motivos = edtMotivos.text.toString().trim()
            val especialidades = edtEspecialidades.text.toString().trim()

            if (motivos.isEmpty() || especialidades.isEmpty()) {
                Toast.makeText(this, getString(R.string.erro_campos_vazios), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnEnviar.isEnabled = false
            btnEnviar.text = getString(R.string.pedir_entrada_enviando)

            lifecycleScope.launch {
                try {
                    // ✅ Re-verifica antes de enviar (evita duplicado por clique duplo)
                    val jaExiste = db.collection("pedidos_entrada")
                        .whereEqualTo("equipeId", equipeId)
                        .whereEqualTo("emailSolicitante", emailSolicitante)
                        .whereEqualTo("status", "pendente")
                        .get()
                        .await()

                    if (!jaExiste.isEmpty) {
                        Toast.makeText(
                            this@PedirEntradaActivity,
                            getString(R.string.pedir_entrada_ja_enviado),
                            Toast.LENGTH_LONG
                        ).show()
                        btnEnviar.isEnabled = true
                        btnEnviar.text = getString(R.string.pedir_entrada_enviar)
                        return@launch
                    }

                    // Busca dados do solicitante
                    val usuarioDoc = db.collection("usuarios").document(emailSolicitante).get().await()
                    val nomeSolicitante = usuarioDoc.getString("nome") ?: getString(R.string.usuario_padrao)
                    val profissaoSolicitante = usuarioDoc.getString("profissao") ?: ""

                    // Cria o pedido
                    val pedido = hashMapOf(
                        "equipeId" to equipeId,
                        "nomeEquipe" to nomeEquipe,
                        "emailSolicitante" to emailSolicitante,
                        "nomeSolicitante" to nomeSolicitante,
                        "profissaoSolicitante" to profissaoSolicitante,
                        "motivos" to motivos,
                        "especialidades" to especialidades,
                        "status" to "pendente",
                        "criadoEm" to System.currentTimeMillis()
                    )

                    db.collection("pedidos_entrada").add(pedido).await()
                    Log.d("PEDIR_ENTRADA", "✅ Pedido enviado!")

                    // ✅ Notifica o dono da equipe
                    notificarDono(
                        equipeId = equipeId,
                        nomeEquipe = nomeEquipe,
                        nomeSolicitante = nomeSolicitante,
                        emailSolicitante = emailSolicitante
                    )

                    Toast.makeText(
                        this@PedirEntradaActivity,
                        getString(R.string.pedir_entrada_sucesso),
                        Toast.LENGTH_SHORT
                    ).show()

                    finish()

                } catch (e: Exception) {
                    Log.e("PEDIR_ENTRADA", "Erro: ${e.message}")
                    Toast.makeText(
                        this@PedirEntradaActivity,
                        getString(R.string.erro_generico, e.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                    btnEnviar.isEnabled = true
                    btnEnviar.text = getString(R.string.pedir_entrada_enviar)
                }
            }
        }

        // ✅ Usa o método do BaseActivity (que já cuida do locale + badges)
        // Como "Pedir Entrada" não é uma aba, deixa null
        configurarBottomNavigation(null)
    }

    // ============================================================
    // ✅ NOTIFICAR O DONO DA EQUIPE
    // ============================================================
    private suspend fun notificarDono(
        equipeId: String,
        nomeEquipe: String,
        nomeSolicitante: String,
        emailSolicitante: String
    ) {
        try {
            val equipeDoc = db.collection("equipes").document(equipeId).get().await()
            val emailDono = equipeDoc.getString("criadorEmail") ?: return

            NotificacaoHelper.notificarPedidoEntrada(
                destinatario = emailDono,
                remetente = emailSolicitante,
                nomeRemetente = nomeSolicitante,
                equipeId = equipeId,
                nomeEquipe = nomeEquipe
            )

            Log.d("PEDIR_ENTRADA", "✅ Dono notificado: $emailDono")
        } catch (e: Exception) {
            Log.e("PEDIR_ENTRADA", "Erro ao notificar dono: ${e.message}")
        }
    }
}