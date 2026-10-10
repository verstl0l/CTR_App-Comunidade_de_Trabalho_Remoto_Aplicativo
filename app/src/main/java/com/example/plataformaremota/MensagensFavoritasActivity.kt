package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MensagensFavoritasActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""

    private var carregando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mensagens_favoritas)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""

        val btnVoltar = findViewById<Button>(R.id.btnVoltarFavoritos)
        btnVoltar.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        if (emailUsuario.isNotEmpty()) {
            carregando = false
            carregarFavoritos()
        }
    }

    private fun carregarFavoritos() {
        if (carregando) return
        carregando = true

        val container = findViewById<LinearLayout>(R.id.containerFavoritos)
        container.removeAllViews()

        lifecycleScope.launch {
            try {
                val favoritos = db.collection("favoritos")
                    .whereEqualTo("usuarioEmail", emailUsuario)
                    .get()
                    .await()

                if (favoritos.isEmpty) {
                    val txtVazio = TextView(this@MensagensFavoritasActivity).apply {
                        text = getString(R.string.favoritos_vazio)
                        setTextColor(ContextCompat.getColor(this@MensagensFavoritasActivity, R.color.text_secondary))
                        textSize = 14f
                        setPadding(0, 60, 0, 60)
                        gravity = android.view.Gravity.CENTER
                    }
                    container.addView(txtVazio)
                    return@launch
                }

                val unicos = favoritos.documents
                    .distinctBy { it.getString("mensagemId") ?: it.id }
                    .sortedByDescending { it.getLong("criadoEm") ?: 0L }

                val inflater = LayoutInflater.from(this@MensagensFavoritasActivity)

                // ✅ Captura strings traduzidas UMA VEZ antes do loop
                val fallbackSemTexto = getString(R.string.favoritos_sem_texto)
                val fallbackUsuario = getString(R.string.favoritos_usuario_fallback)
                val labelFoto = getString(R.string.favoritos_tipo_foto)
                val labelVideo = getString(R.string.favoritos_tipo_video)
                val labelArquivo = getString(R.string.favoritos_tipo_arquivo)
                val ctxGrupo = getString(R.string.favoritos_ctx_grupo)
                val ctxEquipe = getString(R.string.favoritos_ctx_equipe)
                val ctxPrivado = getString(R.string.favoritos_ctx_privado)

                unicos.forEach { doc ->
                    val favoritoId = doc.id
                    val texto = doc.getString("texto") ?: fallbackSemTexto
                    val nomeRemetente = doc.getString("nomeRemetente") ?: fallbackUsuario
                    val chatId = doc.getString("chatId") ?: ""
                    val tipoChat = doc.getString("tipoChat") ?: "pv"
                    val tipoMidia = doc.getString("tipoMidia") ?: "texto"

                    val view = inflater.inflate(R.layout.item_favorito, container, false)

                    view.findViewById<TextView>(R.id.txtNomeFavorito).text = nomeRemetente

                    val textoExibido = when (tipoMidia) {
                        "foto" -> labelFoto
                        "video" -> labelVideo
                        "arquivo" -> labelArquivo
                        else -> texto
                    }
                    view.findViewById<TextView>(R.id.txtTextoFavorito).text = textoExibido

                    val contexto = when (tipoChat) {
                        "grupo" -> ctxGrupo
                        "equipe" -> ctxEquipe
                        else -> ctxPrivado
                    }
                    view.findViewById<TextView>(R.id.txtContextoFavorito).text = contexto

                    view.setOnClickListener {
                        abrirChat(chatId, tipoChat)
                    }

                    view.setOnLongClickListener {
                        AlertDialog.Builder(this@MensagensFavoritasActivity)
                            .setTitle(getString(R.string.favoritos_remover))
                            .setMessage(getString(R.string.favoritos_remover_msg))
                            .setPositiveButton(getString(R.string.favoritos_remover_confirmar)) { _, _ ->
                                removerFavorito(favoritoId)
                            }
                            .setNegativeButton(R.string.cancelar, null)
                            .show()
                        true
                    }

                    container.addView(view)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Erro: ${e.message}")
                Toast.makeText(
                    this@MensagensFavoritasActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                carregando = false
            }
        }
    }

    private fun abrirChat(chatId: String, tipoChat: String) {
        if (chatId.isEmpty()) {
            Toast.makeText(this, getString(R.string.favoritos_chat_nao_encontrado), Toast.LENGTH_SHORT).show()
            return
        }

        val intent = when (tipoChat) {
            "grupo" -> Intent(this, ChatGrupoActivity::class.java).apply {
                putExtra("grupoId", chatId)
            }
            "equipe" -> Intent(this, ChatEquipeActivity::class.java).apply {
                putExtra("equipeId", chatId)
            }
            else -> Intent(this, ChatActivity::class.java).apply {
                putExtra("chatId", chatId)
            }
        }

        startActivity(intent)
    }

    private fun removerFavorito(favoritoId: String) {
        lifecycleScope.launch {
            try {
                db.collection("favoritos").document(favoritoId).delete().await()
                Toast.makeText(
                    this@MensagensFavoritasActivity,
                    getString(R.string.favoritos_removido_sucesso),
                    Toast.LENGTH_SHORT
                ).show()
                carregando = false
                carregarFavoritos()
            } catch (e: Exception) {
                Toast.makeText(
                    this@MensagensFavoritasActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        private const val TAG = "FAVORITOS"
    }
}