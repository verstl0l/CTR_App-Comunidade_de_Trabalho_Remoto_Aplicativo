package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.plataformaremota.data.database.AppDatabase
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var edtEmail: EditText
    private lateinit var edtSenha: EditText
    private lateinit var btnEntrar: Button
    private lateinit var btnCadastrar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_login)

        edtEmail = findViewById(R.id.edtEmail)
        edtSenha = findViewById(R.id.edtSenha)

        btnEntrar = findViewById(R.id.btnEntrar)
        btnCadastrar = findViewById(R.id.btnCadastrar)

        btnEntrar.setOnClickListener {
            realizarLogin()
        }

        btnCadastrar.setOnClickListener {

            val intent = Intent(
                this,
                CadastroActivity::class.java
            )

            startActivity(intent)
        }
    }

    private fun realizarLogin() {

        val email = edtEmail.text.toString().trim()
        val senha = edtSenha.text.toString().trim()

        if (email.isEmpty() || senha.isEmpty()) {

            Toast.makeText(
                this,
                "Preencha todos os campos",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        lifecycleScope.launch {

            val database = AppDatabase.getDatabase(this@LoginActivity)

            val usuario = database.usuarioDao().login(
                email,
                senha
            )

            runOnUiThread {

                if (usuario != null) {

                    val intent = Intent(
                        this@LoginActivity,
                        MainActivity::class.java
                    )

                    intent.putExtra(
                        "usuarioId",
                        usuario.id
                    )

                    intent.putExtra(
                        "nomeUsuario",
                        usuario.nome
                    )

                    startActivity(intent)

                    finish()

                } else {

                    Toast.makeText(
                        this@LoginActivity,
                        "E-mail ou senha incorretos",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}