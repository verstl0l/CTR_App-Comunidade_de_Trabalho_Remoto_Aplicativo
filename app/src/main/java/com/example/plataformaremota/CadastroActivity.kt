package com.example.plataformaremota

import android.content.Context
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

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.aplicarIdioma(newBase))
    }

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
                    getString(R.string.erro_sem_conexao),
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
                Toast.makeText(this, getString(R.string.erro_campos_vazios), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (senha.length < 6) {
                Toast.makeText(this, getString(R.string.cadastro_erro_senha_curta), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (senha != confSenha) {
                Toast.makeText(this, getString(R.string.cadastro_erro_senhas_diferentes), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSalvar.isEnabled = false
            btnSalvar.text = getString(R.string.cadastro_cadastrando)

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

                                Toast.makeText(this, getString(R.string.cadastro_sucesso), Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Log.e("CADASTRO", "❌ Erro ao salvar no Firestore: ${e.message}")
                                Toast.makeText(
                                    this,
                                    getString(R.string.cadastro_erro_salvar, e.message ?: ""),
                                    Toast.LENGTH_LONG
                                ).show()
                                btnSalvar.isEnabled = true
                                btnSalvar.text = getString(R.string.cadastro_botao)
                            }
                    } else {
                        Toast.makeText(
                            this,
                            getString(R.string.erro_generico, task.exception?.message ?: ""),
                            Toast.LENGTH_LONG
                        ).show()
                        btnSalvar.isEnabled = true
                        btnSalvar.text = getString(R.string.cadastro_botao)
                    }
                }
        }
    }
}