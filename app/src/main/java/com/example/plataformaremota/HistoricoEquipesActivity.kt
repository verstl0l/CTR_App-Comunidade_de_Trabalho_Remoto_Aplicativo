package com.example.plataformaremota

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lista as equipes passadas do usuário (as que ele saiu, foi removido ou que foram excluídas).
 * Ao clicar, abre a tela de detalhes com os trabalhos.
 */
class HistoricoEquipesActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""

    private var carregando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historico_equipes)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        emailUsuario = auth.currentUser?.email
            ?: prefs.getString(KEY_EMAIL_USUARIO, "")
                    ?: ""

        findViewById<Button>(R.id.btnVoltarHistoricoEquipes).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        if (emailUsuario.isNotEmpty()) {
            carregando = false
            carregarHistorico()
        }
    }

    private fun carregarHistorico() {
        if (carregando) return
        carregando = true

        val container = findViewById<LinearLayout>(R.id.containerHistoricoEquipes)
        val containerVazio = findViewById<LinearLayout>(R.id.containerVazioHistoricoEquipes)
        container.removeAllViews()

        lifecycleScope.launch {
            try {
                val snap = db.collection("historico_equipes")
                    .document(emailUsuario)
                    .collection("equipes")
                    .orderBy("saiuEm", Query.Direction.DESCENDING)
                    .get()
                    .await()

                if (snap.isEmpty) {
                    containerVazio.visibility = View.VISIBLE
                    return@launch
                }

                containerVazio.visibility = View.GONE

                val inflater = LayoutInflater.from(this@HistoricoEquipesActivity)
                val formatoData = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                val fallbackEquipe = getString(R.string.historico_equipes_equipe_fallback)

                for (doc in snap.documents) {
                    val equipeId = doc.getString("equipeId") ?: doc.id
                    val nomeEquipe = doc.getString("nomeEquipe") ?: fallbackEquipe
                    val papel = doc.getString("papel") ?: "membro"
                    val motivoSaida = doc.getString("motivoSaida") ?: "saiu"
                    val saiuEm = doc.getTimestamp("saiuEm")?.toDate()

                    val view = inflater.inflate(R.layout.item_historico_equipe, container, false)

                    val txtNome = view.findViewById<TextView>(R.id.txtNomeEquipeHistorico)
                    val txtPapel = view.findViewById<TextView>(R.id.txtPapelHistorico)
                    val txtPeriodo = view.findViewById<TextView>(R.id.txtPeriodoHistorico)
                    val txtBadge = view.findViewById<TextView>(R.id.txtBadgeMotivoHistorico)

                    txtNome.text = nomeEquipe

                    // Papel traduzido
                    txtPapel.text = when (papel) {
                        "dono" -> getString(R.string.minhas_equipes_papel_criador)
                        "admin" -> getString(R.string.minhas_equipes_papel_admin)
                        else -> getString(R.string.minhas_equipes_papel_membro)
                    }

                    // Período: "Saiu em 14/10/2026 10:29"
                    txtPeriodo.text = if (saiuEm != null) {
                        getString(
                            R.string.historico_equipes_saiu_em,
                            formatoData.format(saiuEm)
                        )
                    } else {
                        ""
                    }

                    // Badge do motivo
                    when (motivoSaida) {
                        "saiu" -> {
                            txtBadge.text = getString(R.string.historico_equipes_motivo_saiu)
                            txtBadge.setBackgroundColor(android.graphics.Color.parseColor("#9E9E9E"))
                            txtBadge.setTextColor(android.graphics.Color.parseColor("#1C1311"))
                        }
                        "removido" -> {
                            txtBadge.text = getString(R.string.historico_equipes_motivo_removido)
                            txtBadge.setBackgroundColor(android.graphics.Color.parseColor("#FF6B6B"))
                            txtBadge.setTextColor(android.graphics.Color.WHITE)
                        }
                        "equipe_excluida" -> {
                            txtBadge.text = getString(R.string.historico_equipes_motivo_excluida)
                            txtBadge.setBackgroundColor(android.graphics.Color.parseColor("#FF6B6B"))
                            txtBadge.setTextColor(android.graphics.Color.WHITE)
                        }
                    }

                    view.setOnClickListener {
                        val i = Intent(this@HistoricoEquipesActivity, HistoricoEquipeDetalhesActivity::class.java)
                        i.putExtra("equipeId", equipeId)
                        startActivity(i)
                    }

                    container.addView(view)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar histórico: ${e.message}")
                Toast.makeText(
                    this@HistoricoEquipesActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                carregando = false
            }
        }
    }

    companion object {
        private const val TAG = "HISTORICO_EQUIPES"
        private const val PREFS_NAME = "CTR_PREFS"
        private const val KEY_EMAIL_USUARIO = "emailUsuario"
    }
}