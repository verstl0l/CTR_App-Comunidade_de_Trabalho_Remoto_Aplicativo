package com.example.plataformaremota

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var edtEmail: EditText
    private lateinit var edtSenha: EditText
    private lateinit var btnEntrar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Se ja esta logado, vai direto pra MainActivity
        if (auth.currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        edtEmail = findViewById(R.id.edtEmail)
        edtSenha = findViewById(R.id.edtSenha)
        btnEntrar = findViewById(R.id.btnEntrar)

        btnEntrar.setOnClickListener { fazerLogin() }

        // Botao criar conta
        val btnCadastrar = findViewById<Button>(R.id.btnCadastrar)
        btnCadastrar.setOnClickListener {
            startActivity(Intent(this, CadastroActivity::class.java))
        }
    }

    // ============================================================
    // FAZER LOGIN
    // ============================================================
    private fun fazerLogin() {
        val email = edtEmail.text.toString().trim().lowercase()
        val senha = edtSenha.text.toString().trim()

        // Validacoes
        if (email.isEmpty() || senha.isEmpty()) {
            Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "E-mail invalido", Toast.LENGTH_SHORT).show()
            return
        }
        if (senha.length < 6) {
            Toast.makeText(this, "A senha deve ter pelo menos 6 caracteres", Toast.LENGTH_SHORT).show()
            return
        }
        if (!NetworkUtils.isOnline(this)) {
            Toast.makeText(this, getString(R.string.erro_sem_conexao), Toast.LENGTH_LONG).show()
            return
        }

        btnEntrar.isEnabled = false
        btnEntrar.text = "ENTRANDO..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                auth.signInWithEmailAndPassword(email, senha).await()

                // ✅ Salva prefs (UMA VEZ SÓ)
                val prefs = getSharedPreferences("CTR_PREFS", Context.MODE_PRIVATE)
                prefs.edit()
                    .putBoolean("logado", true)
                    .putString("emailUsuario", email)
                    .apply()

                // ✅ Força refresh do token
                val user = auth.currentUser
                Log.d("LOGIN", "Login OK. user: ${user?.email}, uid: ${user?.uid}")
                try {
                    user?.getIdToken(true)?.await()
                    Log.d("LOGIN", "✅ Token refresh OK")
                } catch (e: Exception) {
                    Log.e("LOGIN", "❌ Erro refresh token: ${e.message}")
                }

                // ✅ Busca nome/profissao no Firestore
                try {
                    val userDoc = db.collection("usuarios").document(email).get().await()
                    val nome = userDoc.getString("nome") ?: ""
                    val profissao = userDoc.getString("profissao") ?: ""
                    prefs.edit()
                        .putString("nomeUsuario", nome)
                        .putString("profissaoUsuario", profissao)
                        .apply()
                } catch (e: Exception) {
                    Log.e("LOGIN", "Erro ao buscar dados do usuario: ${e.message}")
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "✅ Login realizado!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                }

            } catch (e: Exception) {
                Log.e("LOGIN", "Erro no login: ${e.message}")
                withContext(Dispatchers.Main) {
                    val mensagem = traduzirErro(e.message ?: "")
                    Toast.makeText(this@LoginActivity, mensagem, Toast.LENGTH_LONG).show()
                    btnEntrar.isEnabled = true
                    btnEntrar.text = "ENTRAR"
                }
            }
        }
    }

    // ============================================================
    // TRADUZIR ERROS DO FIREBASE
    // ============================================================
    private fun traduzirErro(msg: String): String {
        return when {
            msg.contains("password is invalid", ignoreCase = true) ||
                    msg.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ->
                "E-mail ou senha incorretos"

            msg.contains("no user record", ignoreCase = true) ->
                "Nenhuma conta encontrada com este e-mail"

            msg.contains("network", ignoreCase = true) ||
                    msg.contains("timeout", ignoreCase = true) ->
                getString(R.string.erro_sem_conexao)

            msg.contains("too many", ignoreCase = true) ->
                "Muitas tentativas. Tente novamente em alguns minutos"

            msg.contains("badly formatted", ignoreCase = true) ->
                "E-mail em formato invalido"

            msg.isBlank() -> "Erro ao fazer login. Tente novamente."

            else -> "Erro: $msg"
        }
    }
}