package com.example.plataformaremota

import  android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CriarEquipeActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_criar_equipe)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val email = auth.currentUser?.email ?: ""

        val edtNome = findViewById<EditText>(R.id.edtNomeEquipe)
        val edtDescricao = findViewById<EditText>(R.id.edtDescricaoEquipe)
        val btnSalvar = findViewById<Button>(R.id.btnSalvarEquipe)
        val btnVoltar = findViewById<Button>(R.id.btnVoltarCriar)

        btnVoltar.setOnClickListener { finish() }

        btnSalvar.setOnClickListener {
            val nome = edtNome.text.toString().trim()
            val descricao = edtDescricao.text.toString().trim()

            if (nome.isEmpty()) {
                Toast.makeText(this, getString(R.string.erro_nome_equipe_obrigatorio), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSalvar.isEnabled = false
            btnSalvar.text = getString(R.string.criando_equipe)

            val equipe = hashMapOf(
                "nome" to nome,
                "descricao" to descricao,
                "criadorEmail" to email,
                "privada" to false,
                "criadaEm" to System.currentTimeMillis()
            )

            db.collection("equipes")
                .add(equipe)
                .addOnSuccessListener { documentReference ->
                    Log.d("CRIAR_EQUIPE", "✅ Equipe criada! ID: ${documentReference.id}")

                    // ✅ Adiciona o criador como administrador na coleção de membros
                    val membro = hashMapOf(
                        "equipeId" to documentReference.id,
                        "email" to email,
                        "nome" to (auth.currentUser?.displayName ?: email),
                        "funcao" to "administrador",
                        "entrouEm" to System.currentTimeMillis()
                    )

                    // ✅ ONDA 0 — ID determinístico: {email}_{equipeId}
                    val membroId = "${email}_${documentReference.id}"

                    db.collection("membros_equipe")
                        .document(membroId)
                        .set(membro)
                        .addOnSuccessListener {
                            Toast.makeText(this, getString(R.string.equipe_criada_sucesso), Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, produtos::class.java))
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Log.e("CRIAR_EQUIPE", "Erro ao adicionar membro: ${e.message}")
                            Toast.makeText(
                                this,
                                getString(R.string.erro_adicionar_membro),
                                Toast.LENGTH_LONG
                            ).show()
                            startActivity(Intent(this, produtos::class.java))
                            finish()
                        }
                }
                .addOnFailureListener { e ->
                    Log.e("CRIAR_EQUIPE", "❌ Erro: ${e.message}")
                    Toast.makeText(this, getString(R.string.erro_criar_equipe, e.message ?: ""), Toast.LENGTH_LONG).show()
                    btnSalvar.isEnabled = true
                    btnSalvar.text = getString(R.string.salvar_equipe)
                }
        }
    }
}