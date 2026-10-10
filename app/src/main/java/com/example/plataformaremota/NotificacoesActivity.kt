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

    private var carregando = false

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
        if (emailUsuario.isNotEmpty()) {
            carregando = false
            carregarNotificacoes()
        }
    }

    private fun carregarNotificacoes() {
        if (carregando) return
        carregando = true

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

                val unicas = notificacoes.documents.distinctBy { doc ->
                    val tipo = doc.getString("tipo") ?: ""
                    val titulo = doc.getString("titulo") ?: ""
                    val msg = doc.getString("mensagem") ?: ""
                    "${tipo}_${titulo}_${msg}_${doc.getString("referenciaId") ?: ""}"
                }

                val inflater = LayoutInflater.from(this@NotificacoesActivity)

                // ✅ Fallbacks traduzidos capturados UMA VEZ
                val fallbackTitulo = getString(R.string.notificacoes_titulo_fallback)
                val fallbackEquipe = getString(R.string.notificacoes_equipe_fallback)
                val fallbackSemDescricao = getString(R.string.notificacoes_sem_descricao)
                val fallbackUsuario = getString(R.string.notificacoes_usuario_fallback)
                val fallbackTrabalho = getString(R.string.notificacoes_trabalho_fallback)

                unicas.forEach { doc ->
                    val notificacaoId = doc.id
                    val tipo = doc.getString("tipo") ?: "generico"
                    val titulo = doc.getString("titulo") ?: fallbackTitulo
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

                    configurarIcone(cardIcone, imgIcone, tipo)

                    indicador.visibility = if (lida) View.GONE else View.VISIBLE
                    view.alpha = if (lida) 0.7f else 1f

                    view.setOnClickListener {
                        if (!lida) {
                            lifecycleScope.launch {
                                NotificacaoHelper.marcarComoLida(notificacaoId)
                            }
                        }
                        navegarParaNotificacao(tipo, referenciaTipo, referenciaId)
                    }

                    view.setOnLongClickListener {
                        AlertDialog.Builder(this@NotificacoesActivity)
                            .setTitle(getString(R.string.notificacoes_apagar_titulo))
                            .setMessage(getString(R.string.notificacoes_apagar_msg))
                            .setPositiveButton(getString(R.string.notificacoes_apagar_confirmar)) { _, _ ->
                                apagarNotificacao(notificacaoId)
                            }
                            .setNegativeButton(R.string.cancelar, null)
                            .show()
                        true
                    }

                    container.addView(view)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Erro: ${e.message}")
            } finally {
                carregando = false
            }
        }
    }

    private fun configurarIcone(
        cardIcone: com.google.android.material.card.MaterialCardView,
        imgIcone: ImageView,
        tipo: String
    ) {
        when (tipo) {
            "convite_equipe", "convite_trabalho", "membro_adicionado", "pedido_recusado", "pedido_entrada" -> {
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

    private fun navegarParaNotificacao(tipo: String, referenciaTipo: String, referenciaId: String) {
        if (tipo == "pedido_entrada") {
            val intent = Intent(this@NotificacoesActivity, PedidosPendentesActivity::class.java)
            intent.putExtra("equipeId", referenciaId)
            startActivity(intent)
            return
        }

        if (referenciaTipo == "equipe") {
            lifecycleScope.launch {
                try {
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
                        val nomeEquipe = doc.getString("nomeEquipe")
                            ?: getString(R.string.notificacoes_equipe_fallback)
                        val descricaoEquipe = doc.getString("descricaoEquipe")
                            ?: getString(R.string.notificacoes_sem_descricao)
                        val nomeRemetente = doc.getString("nomeRemetente")
                            ?: getString(R.string.notificacoes_usuario_fallback)

                        val intent = Intent(this@NotificacoesActivity, AceitarConviteActivity::class.java)
                        intent.putExtra("conviteId", conviteId)
                        intent.putExtra("equipeId", referenciaId)
                        intent.putExtra("nomeEquipe", nomeEquipe)
                        intent.putExtra("descricaoEquipe", descricaoEquipe)
                        intent.putExtra("nomeRemetente", nomeRemetente)
                        startActivity(intent)
                    } else {
                        val intent = Intent(this@NotificacoesActivity, InfoEquipeActivity::class.java)
                        intent.putExtra("equipeId", referenciaId)
                        startActivity(intent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro convite: ${e.message}")
                }
            }
            return
        }

        when (referenciaTipo) {
            "trabalho" -> {
                lifecycleScope.launch {
                    try {
                        val trabalhoDoc = db.collection("trabalhos").document(referenciaId).get().await()
                        val titulo = trabalhoDoc.getString("titulo")
                            ?: getString(R.string.notificacoes_trabalho_fallback)
                        val equipeIdTrab = trabalhoDoc.getString("equipeId") ?: ""

                        val intent = Intent(this@NotificacoesActivity, ComentariosTrabalhoActivity::class.java)
                        intent.putExtra("trabalhoId", referenciaId)
                        intent.putExtra("tituloTrabalho", titulo)
                        intent.putExtra("equipeId", equipeIdTrab)
                        startActivity(intent)
                    } catch (e: Exception) {
                        val intent = Intent(this@NotificacoesActivity, MinhasEquipesActivity::class.java)
                        startActivity(intent)
                    }
                }
            }
            "grupo" -> {
                val intent = Intent(this, ChatGrupoActivity::class.java)
                intent.putExtra("grupoId", referenciaId)
                startActivity(intent)
            }
            else -> { }
        }
    }

    private fun marcarTodasComoLidas() {
        lifecycleScope.launch {
            try {
                NotificacaoHelper.marcarTodasComoLidas(emailUsuario)
                Toast.makeText(
                    this@NotificacoesActivity,
                    getString(R.string.notificacoes_todas_lidas),
                    Toast.LENGTH_SHORT
                ).show()
                carregando = false
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

    private fun apagarNotificacao(notificacaoId: String) {
        lifecycleScope.launch {
            try {
                db.collection("notificacoes").document(notificacaoId).delete().await()
                Toast.makeText(
                    this@NotificacoesActivity,
                    getString(R.string.notificacoes_apagada_sucesso),
                    Toast.LENGTH_SHORT
                ).show()
                carregando = false
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
    // FORMATAR DATA (com plurais e locale do sistema)
    // ============================================================
    private fun formatarDataRelativa(timestamp: Long): String {
        if (timestamp == 0L) return ""

        val agora = System.currentTimeMillis()
        val diff = agora - timestamp

        return when {
            diff < 60_000 -> getString(R.string.notificacoes_agora)
            diff < 3_600_000 -> {
                val min = TimeUnit.MILLISECONDS.toMinutes(diff).toInt()
                resources.getQuantityString(R.plurals.notificacoes_minutos_atras, min, min)
            }
            diff < 86_400_000 -> {
                val horas = TimeUnit.MILLISECONDS.toHours(diff).toInt()
                resources.getQuantityString(R.plurals.notificacoes_horas_atras, horas, horas)
            }
            diff < 604_800_000 -> {
                val dias = TimeUnit.MILLISECONDS.toDays(diff).toInt()
                resources.getQuantityString(R.plurals.notificacoes_dias_atras, dias, dias)
            }
            else -> {
                // ✅ Usa o locale do sistema, não força pt-BR
                val formato = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                formato.format(Date(timestamp))
            }
        }
    }

    companion object {
        private const val TAG = "NOTIFICACOES"
    }
}