package com.example.plataformaremota

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
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

class ConfiguracoesActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuracoes)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        emailUsuario = auth.currentUser?.email
            ?: prefs.getString(KEY_EMAIL_USUARIO, "")
                    ?: ""

        // ============================================================
        // BOTÃO VOLTAR (é um Button no XML)
        // ============================================================
        findViewById<Button>(R.id.btnVoltarConfig).setOnClickListener { finish() }

        // ============================================================
        // IDIOMA (é um LinearLayout no XML)
        // ============================================================
        val txtIdiomaAtual = findViewById<TextView>(R.id.txtIdiomaAtualConfig)
        atualizarTextoIdioma(txtIdiomaAtual)

        findViewById<LinearLayout>(R.id.btnIdiomaConfig).setOnClickListener {
            LocaleHelper.mostrarSeletorIdioma(this) { codigoEscolhido ->
                Toast.makeText(
                    this,
                    getString(R.string.config_idioma_alterado),
                    Toast.LENGTH_SHORT
                ).show()
                LocaleHelper.reiniciarApp(this)
            }
        }

        // ============================================================
        // HISTÓRICO DE TRABALHOS
        // ============================================================
        findViewById<LinearLayout>(R.id.btnHistoricoTrabalhosConfig).setOnClickListener {
            startActivity(Intent(this, HistoricoTrabalhoDetalhesActivity::class.java))
        }

        // ============================================================
        // MENSAGENS FAVORITAS
        // ============================================================
        findViewById<LinearLayout>(R.id.btnMensagensFavoritasConfig).setOnClickListener {
            startActivity(Intent(this, MensagensFavoritasActivity::class.java))
        }

        // ============================================================
        // HISTÓRICO DE EQUIPES
        // ============================================================
        findViewById<LinearLayout>(R.id.btnHistoricoEquipesConfig).setOnClickListener {
            startActivity(Intent(this, HistoricoEquipesActivity::class.java))
        }

        // ============================================================
        // USUÁRIOS BLOQUEADOS
        // ============================================================
        findViewById<LinearLayout>(R.id.btnUsuariosBloqueadosConfig).setOnClickListener {
                startActivity(Intent(this, UsuariosBloqueadosActivity::class.java))  // ← comentado
        }

        // ============================================================
        // SAIR DA CONTA
        // ============================================================
        findViewById<LinearLayout>(R.id.btnSairContaConfig).setOnClickListener {
            confirmarSairConta()
        }

        // ============================================================
        // EXCLUIR CONTA
        // ============================================================
        findViewById<LinearLayout>(R.id.btnExcluirContaConfig).setOnClickListener {
            confirmarExcluirConta()
        }
    }

    private fun atualizarTextoIdioma(txt: TextView) {
        val codigo = LocaleHelper.getIdiomaAtual(this)
        val nome = when (codigo) {
            "pt" -> getString(R.string.idioma_portugues)
            "en" -> getString(R.string.idioma_ingles)
            "es" -> getString(R.string.idioma_espanhol)
            "fr" -> getString(R.string.idioma_frances)
            "ru" -> getString(R.string.idioma_russo)
            "zh" -> getString(R.string.idioma_chines)
            else -> getString(R.string.idioma_portugues)
        }
        txt.text = nome
    }

    private fun confirmarSairConta() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.config_sair_titulo))
            .setMessage(getString(R.string.config_sair_msg))
            .setPositiveButton(getString(R.string.config_sair_confirmar)) { _, _ ->
                fazerLogout()
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun fazerLogout() {
        auth.signOut()
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("logado", false)
            .remove(KEY_EMAIL_USUARIO)
            .remove("nomeUsuario")
            .remove("profissaoUsuario")
            .apply()

        Toast.makeText(
            this,
            getString(R.string.perfil_saindo),
            Toast.LENGTH_SHORT
        ).show()
        startActivity(Intent(this, LoginActivity::class.java))
        finishAffinity()
    }

    private fun confirmarExcluirConta() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.config_excluir_conta))
            .setMessage(getString(R.string.config_excluir_conta_msg))
            .setPositiveButton(getString(R.string.config_excluir_confirmar)) { _, _ ->
                executarExclusaoConta()
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun executarExclusaoConta() {
        lifecycleScope.launch {
            try {
                db.collection("usuarios").document(emailUsuario)
                    .update(
                        mapOf(
                            "ativo" to false,
                            "excluidoEm" to System.currentTimeMillis(),
                            "nome" to getString(R.string.config_conta_removida),
                            "fotoUrl" to "",
                            "links" to emptyList<Any>(),
                            "profissao" to ""
                        )
                    ).await()

                auth.currentUser?.delete()?.await()

                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().clear().apply()

                Toast.makeText(
                    this@ConfiguracoesActivity,
                    getString(R.string.config_conta_excluida),
                    Toast.LENGTH_LONG
                ).show()

                startActivity(Intent(this@ConfiguracoesActivity, LoginActivity::class.java))
                finishAffinity()

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao excluir conta: ${e.message}")
                Toast.makeText(
                    this@ConfiguracoesActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        private const val TAG = "CONFIGURACOES"
        private const val PREFS_NAME = "CTR_PREFS"
        private const val KEY_EMAIL_USUARIO = "emailUsuario"
    }
}