package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot

class CriarEquipeActivity : AppCompatActivity() {

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
                Toast.makeText(this, "O nome da equipe é obrigatório", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSalvar.isEnabled = false
            btnSalvar.text = "CRIANDO..."

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

                    // Adiciona o criador como administrador na coleção de membros
                    val membro = hashMapOf(
                        "equipeId" to documentReference.id,
                        "email" to email,
                        "nome" to (auth.currentUser?.displayName ?: email),
                        "funcao" to "administrador",
                        "entrouEm" to System.currentTimeMillis()
                    )

                    db.collection("membros_equipe")
                        .add(membro)
                        .addOnSuccessListener {
                            Toast.makeText(this, "✅ Equipe criada com sucesso!", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, produtos::class.java))
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Log.e("CRIAR_EQUIPE", "Erro ao adicionar membro: ${e.message}")
                            Toast.makeText(this, "Equipe criada, mas erro ao adicionar membro", Toast.LENGTH_LONG).show()
                            startActivity(Intent(this, produtos::class.java))
                            finish()
                        }
                }
                .addOnFailureListener { e ->
                    Log.e("CRIAR_EQUIPE", "❌ Erro: ${e.message}")
                    Toast.makeText(this, "Erro ao criar equipe: ${e.message}", Toast.LENGTH_LONG).show()
                    btnSalvar.isEnabled = true
                    btnSalvar.text = "SALVAR"
                }
        }
    }
}