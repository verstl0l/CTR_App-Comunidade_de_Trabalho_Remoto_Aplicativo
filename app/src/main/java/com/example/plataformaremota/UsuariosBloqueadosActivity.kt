package com.example.plataformaremota

import android.content.Context
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
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Tela que lista os usuários que o usuário atual bloqueou.
 * Permite desbloquear.
 */
class UsuariosBloqueadosActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""

    private var carregando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usuarios_bloqueados)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        emailUsuario = auth.currentUser?.email
            ?: prefs.getString(KEY_EMAIL_USUARIO, "")
                    ?: ""

        findViewById<Button>(R.id.btnVoltarBloqueados).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        if (emailUsuario.isNotEmpty()) {
            carregando = false
            carregarBloqueados()
        }
    }

    private fun carregarBloqueados() {
        if (carregando) return
        carregando = true

        val container = findViewById<LinearLayout>(R.id.containerBloqueados)
        val containerVazio = findViewById<LinearLayout>(R.id.containerVazioBloqueados)
        container.removeAllViews()

        lifecycleScope.launch {
            try {
                val snap = db.collection("bloqueios")
                    .whereEqualTo("bloqueadorEmail", emailUsuario)
                    .get()
                    .await()

                if (snap.isEmpty) {
                    containerVazio.visibility = View.VISIBLE
                    return@launch
                }

                containerVazio.visibility = View.GONE

                val inflater = LayoutInflater.from(this@UsuariosBloqueadosActivity)
                val fallbackUsuario = getString(R.string.usuarios_bloqueados_fallback_usuario)

                // Ordena por data de bloqueio (mais recente primeiro)
                val bloqueios = snap.documents.sortedByDescending {
                    it.getLong("criadoEm") ?: 0L
                }

                for (doc in bloqueios) {
                    val bloqueioId = doc.id
                    val emailBloqueado = doc.getString("bloqueadoEmail") ?: continue

                    // Busca nome + foto do usuário bloqueado
                    val usuarioDoc = db.collection("usuarios").document(emailBloqueado).get().await()
                    val nome = usuarioDoc.getString("nome") ?: fallbackUsuario
                    val fotoUrl = usuarioDoc.getString("fotoUrl") ?: ""

                    val view = inflater.inflate(R.layout.item_usuario_bloqueado, container, false)

                    val imgAvatar = view.findViewById<ImageView>(R.id.imgAvatarBloqueado)
                    val txtIniciais = view.findViewById<TextView>(R.id.txtIniciaisBloqueado)
                    val txtNome = view.findViewById<TextView>(R.id.txtNomeBloqueado)
                    val txtEmail = view.findViewById<TextView>(R.id.txtEmailBloqueado)
                    val btnDesbloquear = view.findViewById<Button>(R.id.btnDesbloquear)

                    txtNome.text = nome
                    txtEmail.text = emailBloqueado

                    if (fotoUrl.isNotEmpty()) {
                        Glide.with(this@UsuariosBloqueadosActivity)
                            .load(fotoUrl)
                            .circleCrop()
                            .into(imgAvatar)
                        imgAvatar.visibility = View.VISIBLE
                        txtIniciais.visibility = View.GONE
                    } else {
                        val iniciais = nome.split(" ")
                            .take(2)
                            .map { it.firstOrNull()?.uppercase() ?: "" }
                            .joinToString("")
                            .ifEmpty { "?" }
                        txtIniciais.text = iniciais
                        txtIniciais.visibility = View.VISIBLE
                        imgAvatar.visibility = View.GONE
                    }

                    btnDesbloquear.setOnClickListener {
                        confirmarDesbloquear(bloqueioId, nome)
                    }

                    container.addView(view)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar bloqueados: ${e.message}")
                Toast.makeText(
                    this@UsuariosBloqueadosActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                carregando = false
            }
        }
    }

    private fun confirmarDesbloquear(bloqueioId: String, nome: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.usuarios_bloqueados_desbloquear_titulo))
            .setMessage(getString(R.string.usuarios_bloqueados_desbloquear_msg, nome))
            .setPositiveButton(getString(R.string.usuarios_bloqueados_desbloquear_confirmar)) { _, _ ->
                desbloquear(bloqueioId)
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun desbloquear(bloqueioId: String) {
        lifecycleScope.launch {
            try {
                db.collection("bloqueios").document(bloqueioId).delete().await()

                Toast.makeText(
                    this@UsuariosBloqueadosActivity,
                    getString(R.string.usuarios_bloqueados_desbloqueado_sucesso),
                    Toast.LENGTH_SHORT
                ).show()

                carregando = false
                carregarBloqueados()

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao desbloquear: ${e.message}")
                Toast.makeText(
                    this@UsuariosBloqueadosActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        private const val TAG = "USUARIOS_BLOQUEADOS"
        private const val PREFS_NAME = "CTR_PREFS"
        private const val KEY_EMAIL_USUARIO = "emailUsuario"
    }
}