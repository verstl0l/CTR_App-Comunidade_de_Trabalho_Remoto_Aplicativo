package com.example.plataformaremota

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
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

class GruposActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""
    private var equipeId: String = ""
    private var ehDonoOuAdm: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_grupos)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""
        equipeId = intent.getStringExtra("equipeId") ?: ""

        val btnVoltar = findViewById<Button>(R.id.btnVoltarGrupos)
        val btnNovoGrupo = findViewById<Button>(R.id.btnNovoGrupo)

        btnVoltar.text = getString(R.string.voltar)
        btnVoltar.setOnClickListener { finish() }

        verificarPermissao()
        carregarGrupos()

        btnNovoGrupo.setOnClickListener {
            if (ehDonoOuAdm) {
                abrirDialogNovoGrupo()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.grupos_sem_permissao_criar),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        configurarBottomNavigation(R.id.nav_groups)
    }

    private fun verificarPermissao() {
        lifecycleScope.launch {
            try {
                val equipe = db.collection("equipes").document(equipeId).get().await()
                val criadorEmail = equipe.getString("criadorEmail") ?: ""

                if (criadorEmail == emailUsuario) {
                    ehDonoOuAdm = true
                    return@launch
                }

                val membro = db.collection("membros_equipe")
                    .whereEqualTo("equipeId", equipeId)
                    .whereEqualTo("email", emailUsuario)
                    .limit(1)
                    .get().await()

                if (!membro.isEmpty) {
                    val funcao = membro.documents[0].getString("funcao") ?: ""
                    ehDonoOuAdm = funcao == "administrador"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro verificarPermissao: ${e.message}")
            }
        }
    }

    private fun carregarGrupos() {
        val container = findViewById<LinearLayout>(R.id.containerGrupos)
        container.removeAllViews()

        lifecycleScope.launch {
            try {
                val grupos = db.collection("grupos")
                    .whereEqualTo("equipeId", equipeId)
                    .get()
                    .await()

                if (grupos.isEmpty) {
                    val txtVazio = TextView(this@GruposActivity).apply {
                        text = getString(R.string.grupos_nenhum_criado)
                        setTextColor(ContextCompat.getColor(this@GruposActivity, R.color.text_secondary))
                        textSize = 14f
                        setPadding(0, 60, 0, 60)
                        gravity = android.view.Gravity.CENTER
                    }
                    container.addView(txtVazio)
                    return@launch
                }

                val inflater = LayoutInflater.from(this@GruposActivity)
                val nomeFallback = getString(R.string.grupos_nome_fallback)

                grupos.documents.forEach { doc ->
                    val grupoId = doc.id
                    val nome = doc.getString("nomeGrupo") ?: nomeFallback
                    val membros = doc.get("membros") as? List<*> ?: emptyList<Any>()

                    val view = inflater.inflate(android.R.layout.simple_list_item_2, container, false)
                    val t1 = view.findViewById<TextView>(android.R.id.text1)
                    val t2 = view.findViewById<TextView>(android.R.id.text2)

                    t1.text = nome
                    t1.setTextColor(ContextCompat.getColor(this@GruposActivity, R.color.text_primary))

                    // ✅ Plural correto por idioma
                    t2.text = resources.getQuantityString(
                        R.plurals.grupos_qtd_membros,
                        membros.size,
                        membros.size
                    )
                    t2.setTextColor(ContextCompat.getColor(this@GruposActivity, R.color.text_secondary))
                    view.setPadding(0, 24, 0, 24)

                    view.setOnClickListener {
                        val intent = android.content.Intent(this@GruposActivity, ChatGrupoActivity::class.java)
                        intent.putExtra("grupoId", grupoId)
                        intent.putExtra("nomeGrupo", nome)
                        startActivity(intent)
                    }

                    container.addView(view)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Erro carregarGrupos: ${e.message}")
            }
        }
    }

    private fun abrirDialogNovoGrupo() {
        val edtNome = EditText(this).apply {
            hint = getString(R.string.grupos_hint_nome)
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.grupos_dialog_novo_titulo))
            .setView(edtNome)
            .setPositiveButton(getString(R.string.grupos_dialog_criar)) { _, _ ->
                val nome = edtNome.text.toString().trim()
                if (nome.isEmpty()) return@setPositiveButton

                lifecycleScope.launch {
                    try {
                        val grupo = hashMapOf(
                            "equipeId" to equipeId,
                            "nomeGrupo" to nome,
                            "criadorEmail" to emailUsuario,
                            "membros" to listOf(emailUsuario),
                            "criadoEm" to System.currentTimeMillis()
                        )

                        val grupoId = db.collection("grupos").add(grupo).await().id

                        adicionarGrupoIdAoUsuario(emailUsuario, grupoId)

                        Toast.makeText(
                            this@GruposActivity,
                            getString(R.string.grupos_criado_sucesso),
                            Toast.LENGTH_SHORT
                        ).show()
                        carregarGrupos()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@GruposActivity,
                            getString(R.string.erro_generico, e.message ?: ""),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private suspend fun adicionarGrupoIdAoUsuario(email: String, grupoId: String) {
        try {
            val userRef = db.collection("usuarios").document(email)
            val userDoc = userRef.get().await()
            val ids = (userDoc.get("gruposIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

            if (grupoId !in ids) {
                userRef.update("gruposIds", ids + grupoId).await()
                Log.d(TAG, "gruposIds atualizado para $email")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao denormalizar gruposIds: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "GRUPOS"
    }
}