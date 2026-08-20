package com.example.plataformaremota

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.plataformaremota.data.database.AppDatabase
import com.example.plataformaremota.data.entity.Usuario
import kotlinx.coroutines.launch

class CadastroActivity : AppCompatActivity() {

    private lateinit var edtNome: EditText
    private lateinit var edtEmail: EditText
    private lateinit var edtSenha: EditText
    private lateinit var edtProfissao: EditText
    private lateinit var btnSalvar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_cadastro)

        edtNome = findViewById(R.id.edtNome)
        edtEmail = findViewById(R.id.edtEmail)
        edtSenha = findViewById(R.id.edtSenha)
        edtProfissao = findViewById(R.id.edtProfissao)

        btnSalvar = findViewById(R.id.btnSalvar)

        btnSalvar.setOnClickListener {
            cadastrarUsuario()
        }
    }

    private fun cadastrarUsuario() {

        val nome = edtNome.text.toString().trim()
        val email = edtEmail.text.toString().trim()
        val senha = edtSenha.text.toString().trim()
        val profissao = edtProfissao.text.toString().trim()

        if (
            nome.isEmpty() ||
            email.isEmpty() ||
            senha.isEmpty() ||
            profissao.isEmpty()
        ) {

            Toast.makeText(
                this,
                "Preencha todos os campos",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        lifecycleScope.launch {

            val database = AppDatabase.getDatabase(
                this@CadastroActivity
            )

            val usuarioExistente =
                database.usuarioDao().buscarPorEmail(email)

            if (usuarioExistente != null) {

                runOnUiThread {

                    Toast.makeText(
                        this@CadastroActivity,
                        "Este e-mail já está cadastrado",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                return@launch
            }

            val usuario = Usuario(
                nome = nome,
                email = email,
                senha = senha,
                profissao = profissao
            )

            database.usuarioDao().inserir(usuario)

            runOnUiThread {

                Toast.makeText(
                    this@CadastroActivity,
                    "Cadastro realizado com sucesso!",
                    Toast.LENGTH_SHORT
                ).show()

                finish()
            }
        }
    }
}