package com.example.plataformaremota

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.plataformaremota.data.database.AppDatabase
import com.example.plataformaremota.data.entity.Trabalho
import kotlinx.coroutines.launch

class TrabalhoActivity : AppCompatActivity() {

    private lateinit var edtTitulo: EditText
    private lateinit var edtDescricao: EditText
    private lateinit var edtCategoria: EditText
    private lateinit var edtPrazo: EditText
    private lateinit var btnPublicar: Button

    private var usuarioId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_trabalho)

        edtTitulo = findViewById(R.id.edtTitulo)
        edtDescricao = findViewById(R.id.edtDescricao)
        edtCategoria = findViewById(R.id.edtCategoria)
        edtPrazo = findViewById(R.id.edtPrazo)

        btnPublicar = findViewById(R.id.btnPublicar)

        usuarioId = intent.getIntExtra(
            "usuarioId",
            0
        )

        btnPublicar.setOnClickListener {
            publicarTrabalho()
        }
    }

    private fun publicarTrabalho() {

        val titulo = edtTitulo.text.toString().trim()
        val descricao = edtDescricao.text.toString().trim()
        val categoria = edtCategoria.text.toString().trim()
        val prazo = edtPrazo.text.toString().trim()

        if (
            titulo.isEmpty() ||
            descricao.isEmpty() ||
            categoria.isEmpty() ||
            prazo.isEmpty()
        ) {

            Toast.makeText(
                this,
                "Preencha todos os campos",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        if (usuarioId == 0) {

            Toast.makeText(
                this,
                "Usuário não identificado",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        lifecycleScope.launch {

            val database = AppDatabase.getDatabase(
                this@TrabalhoActivity
            )

            val trabalho = Trabalho(

                titulo = titulo,

                descricao = descricao,

                categoria = categoria,

                prazo = prazo,

                criadorId = usuarioId
            )

            database.trabalhoDao().inserir(trabalho)

            runOnUiThread {

                Toast.makeText(
                    this@TrabalhoActivity,
                    "Trabalho publicado!",
                    Toast.LENGTH_SHORT
                ).show()

                finish()
            }
        }
    }
}