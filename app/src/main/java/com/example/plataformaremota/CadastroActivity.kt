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

class CadastroActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cadastro)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val edtNome = findViewById<EditText>(R.id.edtNome)
        val edtEmail = findViewById<EditText>(R.id.edtEmail)
        val edtSenha = findViewById<EditText>(R.id.edtSenha)
        val edtConfSenha = findViewById<EditText>(R.id.edtConfSenha)
        val edtProfissao = findViewById<EditText>(R.id.edtProfissao)
        val btnSalvar = findViewById<Button>(R.id.btnSalvar)

        btnSalvar.setOnClickListener {
            // ✅ VERIFICA CONEXÃO ANTES DE CADASTRAR
            if (!NetworkUtils.isOnline(this)) {
                Toast.makeText(
                    this,
                    "⚠️ Sem conexão com a internet. Verifique sua rede e tente novamente.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            val nome = edtNome.text.toString().trim()
            val email = edtEmail.text.toString().trim()
            val senha = edtSenha.text.toString().trim()
            val confSenha = edtConfSenha.text.toString().trim()
            val profissao = edtProfissao.text.toString().trim()

            if (nome.isEmpty() || email.isEmpty() || senha.isEmpty() || confSenha.isEmpty() || profissao.isEmpty()) {
                Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (senha.length < 6) {
                Toast.makeText(this, "A senha deve ter pelo menos 6 caracteres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (senha != confSenha) {
                Toast.makeText(this, "As senhas não coincidem", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSalvar.isEnabled = false
            btnSalvar.text = "CADASTRANDO..."

            auth.createUserWithEmailAndPassword(email, senha)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val usuario = hashMapOf(
                            "email" to email,
                            "nome" to nome,
                            "profissao" to profissao,
                            "criadoEm" to System.currentTimeMillis()
                        )

                        db.collection("usuarios").document(email)
                            .set(usuario)
                            .addOnSuccessListener {
                                Log.d("CADASTRO", "✅ Usuário salvo no Firestore")

                                val prefs = getSharedPreferences("CTR_PREFS", MODE_PRIVATE)
                                prefs.edit()
                                    .putString("emailUsuario", email)
                                    .putString("nomeUsuario", nome)
                                    .putString("profissaoUsuario", profissao)
                                    .putBoolean("logado", true)
                                    .apply()

                                Toast.makeText(this, "✅ Cadastro realizado!", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Log.e("CADASTRO", "❌ Erro ao salvar no Firestore: ${e.message}")
                                Toast.makeText(
                                    this,
                                    "Cadastro criado, mas erro ao salvar dados: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                                btnSalvar.isEnabled = true
                                btnSalvar.text = "SALVAR"
                            }
                    } else {
                        Toast.makeText(
                            this,
                            "Erro: ${task.exception?.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        btnSalvar.isEnabled = true
                        btnSalvar.text = "SALVAR"
                    }
                }
        }
    }
}