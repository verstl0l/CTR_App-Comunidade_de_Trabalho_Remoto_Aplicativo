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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MensagensFavoritasActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mensagens_favoritas)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""

        val btnVoltar = findViewById<Button>(R.id.btnVoltarFavoritos)
        btnVoltar.setOnClickListener { finish() }

        carregarFavoritos()
    }

    override fun onResume() {
        super.onResume()
        carregarFavoritos()
    }

    // ============================================================
    // CARREGAR LISTA DE FAVORITOS
    // ============================================================
    private fun carregarFavoritos() {
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

                // Ordena por data (mais recentes primeiro)
                val ordenados = favoritos.documents.sortedByDescending {
                    it.getLong("criadoEm") ?: 0L
                }

                val inflater = LayoutInflater.from(this@MensagensFavoritasActivity)

                ordenados.forEach { doc ->
                    val favoritoId = doc.id
                    val texto = doc.getString("texto") ?: "(sem texto)"
                    val nomeRemetente = doc.getString("nomeRemetente") ?: "Usuário"
                    val chatId = doc.getString("chatId") ?: ""
                    val tipoChat = doc.getString("tipoChat") ?: "pv"
                    val tipoMidia = doc.getString("tipoMidia") ?: "texto"

                    val view = inflater.inflate(R.layout.item_favorito, container, false)

                    view.findViewById<TextView>(R.id.txtNomeFavorito).text = nomeRemetente

                    val textoExibido = when (tipoMidia) {
                        "foto" -> "Foto"
                        "video" -> "Vídeo"
                        "arquivo" -> "Arquivo"
                        else -> texto
                    }
                    view.findViewById<TextView>(R.id.txtTextoFavorito).text = textoExibido

                    val contexto = when (tipoChat) {
                        "grupo" -> "Grupo"
                        "equipe" -> "Chat da equipe"
                        else -> "Chat privado"
                    }
                    view.findViewById<TextView>(R.id.txtContextoFavorito).text = contexto

                    // Clique curto: abre o chat de origem
                    view.setOnClickListener {
                        abrirChat(chatId, tipoChat)
                    }

                    // Long press: remove dos favoritos
                    view.setOnLongClickListener {
                        AlertDialog.Builder(this@MensagensFavoritasActivity)
                            .setTitle(getString(R.string.favoritos_remover))
                            .setMessage(getString(R.string.favoritos_remover_msg))
                            .setPositiveButton("Remover") { _, _ ->
                                removerFavorito(favoritoId)
                            }
                            .setNegativeButton(R.string.cancelar, null)
                            .show()
                        true
                    }

                    container.addView(view)
                }

            } catch (e: Exception) {
                Log.e("FAVORITOS", getString(R.string.erro_generico, e.message ?: ""))
                Toast.makeText(
                    this@MensagensFavoritasActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ============================================================
    // ABRIR CHAT DE ORIGEM
    // ============================================================
    private fun abrirChat(chatId: String, tipoChat: String) {
        if (chatId.isEmpty()) {
            Toast.makeText(this, "Chat não encontrado", Toast.LENGTH_SHORT).show()
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

    // ============================================================
    // REMOVER FAVORITO
    // ============================================================
    private fun removerFavorito(favoritoId: String) {
        lifecycleScope.launch {
            try {
                db.collection("favoritos").document(favoritoId).delete().await()
                Toast.makeText(this@MensagensFavoritasActivity, "Removido", Toast.LENGTH_SHORT).show()
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
}