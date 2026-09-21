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

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val edtEmail = findViewById<EditText>(R.id.edtEmail)
        val edtSenha = findViewById<EditText>(R.id.edtSenha)
        val btnEntrar = findViewById<Button>(R.id.btnEntrar)
        val btnCadastrar = findViewById<Button>(R.id.btnCadastrar)

        btnEntrar.setOnClickListener {
            // ✅ VERIFICA CONEXÃO ANTES DE TENTAR LOGAR
            if (!NetworkUtils.isOnline(this)) {
                Toast.makeText(
                    this,
                    "⚠️ Sem conexão com a internet. Verifique sua rede e tente novamente.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            val email = edtEmail.text.toString().trim()
            val senha = edtSenha.text.toString().trim()

            if (email.isEmpty() || senha.isEmpty()) {
                Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnEntrar.isEnabled = false
            btnEntrar.text = "ENTRANDO..."

            auth.signInWithEmailAndPassword(email, senha)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        db.collection("usuarios").document(email).get()
                            .addOnSuccessListener { document ->
                                val nome = document.getString("nome") ?: "Usuário"
                                val profissao = document.getString("profissao") ?: "Profissão"

                                val prefs = getSharedPreferences("CTR_PREFS", MODE_PRIVATE)
                                prefs.edit()
                                    .putString("emailUsuario", email)
                                    .putString("nomeUsuario", nome)
                                    .putString("profissaoUsuario", profissao)
                                    .putBoolean("logado", true)
                                    .apply()

                                Toast.makeText(this, "✅ Login realizado!", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Log.e("LOGIN", "Erro ao buscar usuário: ${e.message}")
                                Toast.makeText(this, "Erro ao carregar dados do usuário", Toast.LENGTH_SHORT).show()
                                btnEntrar.isEnabled = true
                                btnEntrar.text = "ENTRAR"
                            }
                    } else {
                        Toast.makeText(
                            this,
                            "Erro: ${task.exception?.message ?: "Email ou senha incorretos"}",
                            Toast.LENGTH_LONG
                        ).show()
                        btnEntrar.isEnabled = true
                        btnEntrar.text = "ENTRAR"
                    }
                }
        }

        btnCadastrar.setOnClickListener {
            // ✅ VERIFICA CONEXÃO ANTES DE IR PARA O CADASTRO
            if (!NetworkUtils.isOnline(this)) {
                Toast.makeText(
                    this,
                    "⚠️ Sem conexão com a internet. Verifique sua rede e tente novamente.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            startActivity(Intent(this, CadastroActivity::class.java))
        }
    }
}