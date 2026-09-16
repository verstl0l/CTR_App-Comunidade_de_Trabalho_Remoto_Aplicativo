package com.example.plataformaremota.data.entity

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.plataformaremota.MainActivity
import com.example.plataformaremota.R
import com.example.plataformaremota.data.database.AppDatabase
import com.example.plataformaremota.notificacao
import com.example.plataformaremota.perfil
import com.example.plataformaremota.produtos
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

class CriarTrabalhoActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_criar_trabalho)

        database = AppDatabase.getDatabase(this)
        val prefs = getSharedPreferences("CTR_PREFS", MODE_PRIVATE)
        val email = prefs.getString("emailUsuario", "") ?: ""

        val edtTitulo = findViewById<EditText>(R.id.edtTituloTrabalho)
        val edtDescricao = findViewById<EditText>(R.id.edtDescricaoTrabalho)
        val edtCategoria = findViewById<EditText>(R.id.edtCategoriaTrabalho)
        val edtPrazo = findViewById<EditText>(R.id.edtPrazoTrabalho)
        val btnPublicar = findViewById<Button>(R.id.btnPublicarTrabalho)
        val btnVoltar = findViewById<Button>(R.id.btnVoltarCriarTrabalho)

        btnVoltar.setOnClickListener {
            finish()
        }

        btnPublicar.setOnClickListener {
            val titulo = edtTitulo.text.toString().trim()
            val descricao = edtDescricao.text.toString().trim()
            val categoria = edtCategoria.text.toString().trim()
            val prazo = edtPrazo.text.toString().trim()

            if (titulo.isEmpty() || descricao.isEmpty() || categoria.isEmpty() || prazo.isEmpty()) {
                Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val equipe = database.equipeDao().buscarPorCriador(email)
                if (equipe == null) {
                    Toast.makeText(this@CriarTrabalhoActivity, "Crie uma equipe primeiro", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val novoTrabalho = Trabalho(
                    titulo = titulo,
                    descricao = descricao,
                    categoria = categoria,
                    prazo = prazo,
                    criadorId = 0,
                    equipeId = equipe.id
                )
                database.trabalhoDao().inserir(novoTrabalho)

                Toast.makeText(this@CriarTrabalhoActivity, "✅ Trabalho publicado!", Toast.LENGTH_SHORT).show()

                // Volta para o dashboard
                startActivity(Intent(this@CriarTrabalhoActivity, produtos::class.java))
                finish()
            }
        }

        // Bottom Navigation
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_groups -> {
                    startActivity(Intent(this, produtos::class.java))
                    finish()
                    true
                }
                R.id.nav_notifications -> {
                    startActivity(Intent(this, notificacao::class.java))
                    finish()
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, perfil::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }
}