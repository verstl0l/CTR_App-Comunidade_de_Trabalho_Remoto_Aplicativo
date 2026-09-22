package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
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

class GruposActivity : AppCompatActivity() {

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

        btnVoltar.setOnClickListener { finish() }

        verificarPermissao()
        carregarGrupos()

        btnNovoGrupo.setOnClickListener {
            if (ehDonoOuAdm) {
                abrirDialogNovoGrupo()
            } else {
                Toast.makeText(this, "Só o dono ou administrador pode criar grupos", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun verificarPermissao() {
        lifecycleScope.launch {
            try {
                // Verifica se é criador
                val equipe = db.collection("equipes").document(equipeId).get().await()
                val criadorEmail = equipe.getString("criadorEmail") ?: ""

                if (criadorEmail == emailUsuario) {
                    ehDonoOuAdm = true
                    return@launch
                }

                // Verifica se é admin
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
                Log.e("GRUPOS", "Erro: ${e.message}")
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
                        text = "Nenhum grupo criado ainda"
                        setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                        textSize = 14f
                        setPadding(0, 60, 0, 60)
                        gravity = android.view.Gravity.CENTER
                    }
                    container.addView(txtVazio)
                    return@launch
                }

                val inflater = LayoutInflater.from(this@GruposActivity)

                grupos.documents.forEach { doc ->
                    val grupoId = doc.id
                    val nome = doc.getString("nomeGrupo") ?: "Grupo"
                    val membros = doc.get("membros") as? List<*> ?: emptyList<Any>()

                    val view = inflater.inflate(android.R.layout.simple_list_item_2, container, false)
                    val t1 = view.findViewById<TextView>(android.R.id.text1)
                    val t2 = view.findViewById<TextView>(android.R.id.text2)

                    t1.text = nome
                    t1.setTextColor(android.graphics.Color.WHITE)
                    t2.text = "${membros.size} membros"
                    t2.setTextColor(android.graphics.Color.GRAY)
                    view.setPadding(0, 24, 0, 24)

                    view.setOnClickListener {
                        val intent = Intent(this@GruposActivity, ChatGrupoActivity::class.java)
                        intent.putExtra("grupoId", grupoId)
                        intent.putExtra("nomeGrupo", nome)
                        startActivity(intent)
                    }

                    container.addView(view)
                }

            } catch (e: Exception) {
                Log.e("GRUPOS", "Erro: ${e.message}")
            }
        }
    }

    private fun abrirDialogNovoGrupo() {
        val edtNome = EditText(this).apply {
            hint = "Nome do grupo"
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(this)
            .setTitle("Novo Grupo")
            .setView(edtNome)
            .setPositiveButton("Criar") { _, _ ->
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
                        db.collection("grupos").add(grupo).await()
                        Toast.makeText(this@GruposActivity, "✅ Grupo criado!", Toast.LENGTH_SHORT).show()
                        carregarGrupos()
                    } catch (e: Exception) {
                        Toast.makeText(this@GruposActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}