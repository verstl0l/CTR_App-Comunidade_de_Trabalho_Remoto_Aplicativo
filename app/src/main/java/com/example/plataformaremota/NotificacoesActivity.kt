package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit


class NotificacoesActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notificacoes)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""

        val btnMarcarTodas = findViewById<Button>(R.id.btnMarcarTodasLidas)
        btnMarcarTodas.setOnClickListener {
            marcarTodasComoLidas()
        }

        configurarBottomNavigation(R.id.nav_notifications)
    }

    override fun onResume() {
        super.onResume()
        carregarNotificacoes()
    }

    // ============================================================
    // CARREGAR NOTIFICACOES
    // ============================================================
    private fun carregarNotificacoes() {
        val container = findViewById<LinearLayout>(R.id.containerNotificacoes)
        val containerVazio = findViewById<LinearLayout>(R.id.containerVazio)
        container.removeAllViews()

        lifecycleScope.launch {
            try {
                val notificacoes = db.collection("notificacoes")
                    .whereEqualTo("destinatario", emailUsuario)
                    .orderBy("criadoEm", Query.Direction.DESCENDING)
                    .limit(100)
                    .get()
                    .await()

                if (notificacoes.isEmpty) {
                    containerVazio.visibility = View.VISIBLE
                    return@launch
                }

                containerVazio.visibility = View.GONE

                val inflater = LayoutInflater.from(this@NotificacoesActivity)

                notificacoes.documents.forEach { doc ->
                    val notificacaoId = doc.id
                    val tipo = doc.getString("tipo") ?: "generico"
                    val titulo = doc.getString("titulo") ?: "Notificacao"
                    val mensagem = doc.getString("mensagem") ?: ""
                    val lida = doc.getBoolean("lida") ?: false
                    val criadoEm = doc.getLong("criadoEm") ?: 0L
                    val referenciaId = doc.getString("referenciaId") ?: ""
                    val referenciaTipo = doc.getString("referenciaTipo") ?: ""

                    val view = inflater.inflate(R.layout.item_notificacao, container, false)

                    val cardIcone = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardIconeNotificacao)
                    val imgIcone = view.findViewById<ImageView>(R.id.imgIconeNotificacao)
                    val txtTitulo = view.findViewById<TextView>(R.id.txtTituloNotificacao)
                    val txtMensagem = view.findViewById<TextView>(R.id.txtMensagemNotificacao)
                    val txtData = view.findViewById<TextView>(R.id.txtDataNotificacao)
                    val indicador = view.findViewById<View>(R.id.indicadorNaoLida)

                    txtTitulo.text = titulo
                    txtMensagem.text = mensagem
                    txtData.text = formatarDataRelativa(criadoEm)

                    // Configurar icone conforme tipo
                    configurarIcone(cardIcone, imgIcone, tipo)

                    // Indicador de nao lida
                    indicador.visibility = if (lida) View.GONE else View.VISIBLE

                    // Se lida, aplica um tom mais apagado
                    if (lida) {
                        view.alpha = 0.7f
                    } else {
                        view.alpha = 1f
                    }

                    // Clique curto: marca como lida + navega
                    view.setOnClickListener {
                        if (!lida) {
                            lifecycleScope.launch {
                                NotificacaoHelper.marcarComoLida(notificacaoId)
                            }
                        }
                        navegarParaNotificacao(referenciaTipo, referenciaId)
                    }

                    // Long press: opcao de apagar
                    view.setOnLongClickListener {
                        AlertDialog.Builder(this@NotificacoesActivity)
                            .setTitle("Apagar notificacao")
                            .setMessage("Deseja apagar esta notificacao?")
                            .setPositiveButton("Apagar") { _, _ ->
                                apagarNotificacao(notificacaoId)
                            }
                            .setNegativeButton(R.string.cancelar, null)
                            .show()
                        true
                    }

                    container.addView(view)
                }

            } catch (e: Exception) {
                Log.e("NOTIFICACOES", getString(R.string.erro_generico, e.message ?: ""))
            }
        }
    }

    // ============================================================
    // CONFIGURAR ICONE POR TIPO
    // ============================================================
    private fun configurarIcone(
        cardIcone: com.google.android.material.card.MaterialCardView,
        imgIcone: ImageView,
        tipo: String
    ) {
        when (tipo) {
            "convite_equipe", "convite_trabalho", "membro_adicionado", "pedido_recusado" -> {
                imgIcone.setImageResource(R.drawable.ic_notification_convite)
                cardIcone.setCardBackgroundColor(
                    ContextCompat.getColor(this@NotificacoesActivity, R.color.bg_surface_hover)
                )
                imgIcone.setColorFilter(
                    ContextCompat.getColor(this@NotificacoesActivity, R.color.accent)
                )
            }
            "novo_trabalho", "novo_comentario", "pedido_aceito" -> {
                imgIcone.setImageResource(R.drawable.ic_notification_trabalho)
                cardIcone.setCardBackgroundColor(
                    ContextCompat.getColor(this@NotificacoesActivity, R.color.bg_surface_hover)
                )
                imgIcone.setColorFilter(
                    ContextCompat.getColor(this@NotificacoesActivity, R.color.accent)
                )
            }
            else -> {
                imgIcone.setImageResource(R.drawable.ic_notifications)
                cardIcone.setCardBackgroundColor(
                    ContextCompat.getColor(this@NotificacoesActivity, R.color.bg_surface_hover)
                )
                imgIcone.setColorFilter(
                    ContextCompat.getColor(this@NotificacoesActivity, R.color.accent)
                )
            }
        }
    }

    // ============================================================
    // NAVEGAR PARA O DESTINO DA NOTIFICACAO
    // ============================================================
    private fun navegarParaNotificacao(referenciaTipo: String, referenciaId: String) {
        // Convites de equipe vao para a tela dedicada
        if (referenciaTipo == "equipe") {
            lifecycleScope.launch {
                try {
                    // Verifica se ha um convite pendente com esse equipeId
                    val convites = db.collection("convites_equipe")
                        .whereEqualTo("emailConvidado", emailUsuario)
                        .whereEqualTo("equipeId", referenciaId)
                        .whereEqualTo("status", "pendente")
                        .limit(1)
                        .get()
                        .await()

                    if (!convites.isEmpty) {
                        val doc = convites.documents[0]
                        val conviteId = doc.id
                        val nomeEquipe = doc.getString("nomeEquipe") ?: "Equipe"
                        val descricaoEquipe = doc.getString("descricaoEquipe") ?: "Sem descricao"
                        val nomeRemetente = doc.getString("nomeRemetente") ?: "Usuario"

                        val intent = Intent(this@NotificacoesActivity, AceitarConviteActivity::class.java)
                        intent.putExtra("conviteId", conviteId)
                        intent.putExtra("equipeId", referenciaId)
                        intent.putExtra("nomeEquipe", nomeEquipe)
                        intent.putExtra("descricaoEquipe", descricaoEquipe)
                        intent.putExtra("nomeRemetente", nomeRemetente)
                        startActivity(intent)
                    } else {
                        // Nao e convite pendente: abre InfoEquipe normal
                        val intent = Intent(this@NotificacoesActivity, InfoEquipeActivity::class.java)
                        intent.putExtra("equipeId", referenciaId)
                        startActivity(intent)
                    }
                } catch (e: Exception) {
                    Log.e("NOTIFICACOES", "Erro ao checar convite: ${e.message}")
                }
            }
            return
        }

        // Outros tipos
        when (referenciaTipo) {
            "trabalho" -> {
                val intent = Intent(this, MinhasEquipesActivity::class.java)
                startActivity(intent)
            }
            "grupo" -> {
                val intent = Intent(this, ChatGrupoActivity::class.java)
                intent.putExtra("grupoId", referenciaId)
                startActivity(intent)
            }
            else -> {
                // Sem destino especifico
            }
        }
    }

    // ============================================================
    // MARCAR TODAS COMO LIDAS
    // ============================================================
    private fun marcarTodasComoLidas() {
        lifecycleScope.launch {
            try {
                NotificacaoHelper.marcarTodasComoLidas(emailUsuario)
                Toast.makeText(
                    this@NotificacoesActivity,
                    "Todas marcadas como lidas",
                    Toast.LENGTH_SHORT
                ).show()
                carregarNotificacoes()
            } catch (e: Exception) {
                Toast.makeText(
                    this@NotificacoesActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ============================================================
    // APAGAR NOTIFICACAO
    // ============================================================
    private fun apagarNotificacao(notificacaoId: String) {
        lifecycleScope.launch {
            try {
                db.collection("notificacoes").document(notificacaoId).delete().await()
                Toast.makeText(
                    this@NotificacoesActivity,
                    "Notificacao apagada",
                    Toast.LENGTH_SHORT
                ).show()
                carregarNotificacoes()
            } catch (e: Exception) {
                Toast.makeText(
                    this@NotificacoesActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ============================================================
    // FORMATAR DATA RELATIVA
    // ============================================================
    private fun formatarDataRelativa(timestamp: Long): String {
        if (timestamp == 0L) return ""

        val agora = System.currentTimeMillis()
        val diff = agora - timestamp

        return when {
            diff < 60_000 -> "Agora"
            diff < 3_600_000 -> {
                val min = TimeUnit.MILLISECONDS.toMinutes(diff)
                "Ha ${min}min"
            }
            diff < 86_400_000 -> {
                val horas = TimeUnit.MILLISECONDS.toHours(diff)
                "Ha ${horas}h"
            }
            diff < 604_800_000 -> {
                val dias = TimeUnit.MILLISECONDS.toDays(diff)
                "Ha ${dias}d"
            }
            else -> {
                val formato = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
                formato.format(Date(timestamp))
            }
        }
    }
}