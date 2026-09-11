package com.example.plataformaremota

import android.content.Intent
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cadastro)

        val edtNome = findViewById<EditText>(R.id.edtNome)
        val edtEmail = findViewById<EditText>(R.id.edtEmail)
        val edtSenha = findViewById<EditText>(R.id.edtSenha)
        val edtConfSenha = findViewById<EditText>(R.id.edtConfSenha)
        val edtProfissao = findViewById<EditText>(R.id.edtProfissao)
        val btnSalvar = findViewById<Button>(R.id.btnSalvar)

        val database = AppDatabase.getDatabase(this)

        btnSalvar.setOnClickListener {
            val nome = edtNome.text.toString().trim()
            val email = edtEmail.text.toString().trim()
            val senha = edtSenha.text.toString().trim()
            val confSenha = edtConfSenha.text.toString().trim()
            val profissao = edtProfissao.text.toString().trim()

            // Validação 1: Campos vazios
            if (nome.isEmpty() || email.isEmpty() || senha.isEmpty() || confSenha.isEmpty() || profissao.isEmpty()) {
                Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validação 2: Senhas diferentes
            if (senha != confSenha) {
                Toast.makeText(this, "As senhas não coincidem", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validação 3: Email já cadastrado (consulta no banco)
            lifecycleScope.launch {
                val emailJaExiste = database.usuarioDao().buscarPorEmail(email)

                if (emailJaExiste != null) {
                    Toast.makeText(this@CadastroActivity, "Email já cadastrado", Toast.LENGTH_SHORT).show()
                } else {
                    // Cadastra o usuário
                    val novoUsuario = Usuario(
                        nome = nome,
                        email = email,
                        senha = senha,
                        profissao = profissao
                    )
                    database.usuarioDao().inserir(novoUsuario)

                    Toast.makeText(this@CadastroActivity, "✅ Cadastro realizado com sucesso!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@CadastroActivity, LoginActivity::class.java))
                    finish()
                }
            }
        }
    }
}