package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.plataformaremota.data.database.AppDatabase
import com.example.plataformaremota.data.entity.Equipe
import kotlinx.coroutines.launch

class CriarEquipeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_criar_equipe)

        val edtNome = findViewById<EditText>(R.id.edtNomeEquipe)
        val edtDescricao = findViewById<EditText>(R.id.edtDescricaoEquipe)
        val btnSalvar = findViewById<Button>(R.id.btnSalvarEquipe)
        val btnVoltar = findViewById<Button>(R.id.btnVoltarCriar)

        val database = AppDatabase.getDatabase(this)
        val prefs = getSharedPreferences("CTR_PREFS", MODE_PRIVATE)
        val email = prefs.getString("emailUsuario", "") ?: ""

        btnVoltar.setOnClickListener {
            finish()
        }

        btnSalvar.setOnClickListener {
            val nome = edtNome.text.toString().trim()
            val descricao = edtDescricao.text.toString().trim()

            if (nome.isEmpty()) {
                Toast.makeText(this, "O nome da equipe é obrigatório", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val novaEquipe = Equipe(
                    nome = nome,
                    descricao = descricao,
                    criadorEmail = email
                )

                database.equipeDao().inserir(novaEquipe)

                prefs.edit()
                    .putBoolean("temEquipe_$email", true)
                    .putString("nomeEquipe_$email", nome)
                    .apply()

                Toast.makeText(
                    this@CriarEquipeActivity,
                    "✅ Equipe criada com sucesso!",
                    Toast.LENGTH_SHORT
                ).show()

                // ✅ Volta para produtos (que agora mostra o dashboard)
                startActivity(Intent(this@CriarEquipeActivity, produtos::class.java))
                finish()
            }
        }
    }
}