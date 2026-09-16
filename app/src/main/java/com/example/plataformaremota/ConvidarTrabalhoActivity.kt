package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.plataformaremota.data.database.AppDatabase
import com.example.plataformaremota.data.entity.ConviteTrabalho
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

class ConvidarTrabalhoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_convidar_trabalho)

        val database = AppDatabase.getDatabase(this)
        val prefs = getSharedPreferences("CTR_PREFS", MODE_PRIVATE)
        val emailRemetente = prefs.getString("emailUsuario", "") ?: ""

        val trabalhoId = intent.getIntExtra("trabalhoId", -1)
        val tituloTrabalho = intent.getStringExtra("tituloTrabalho") ?: ""

        val txtTitulo = findViewById<TextView>(R.id.txtTituloTrabalhoConvite)
        val edtEmail = findViewById<EditText>(R.id.edtEmailConvidado)
        val btnEnviar = findViewById<Button>(R.id.btnEnviarConviteTrabalho)
        val btnVoltar = findViewById<Button>(R.id.btnVoltarConvidar)

        txtTitulo.text = "Trabalho: $tituloTrabalho"

        btnVoltar.setOnClickListener { finish() }

        btnEnviar.setOnClickListener {
            val emailConvidado = edtEmail.text.toString().trim()

            if (emailConvidado.isEmpty()) {
                Toast.makeText(this, "Digite um email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val equipe = database.equipeDao().buscarPorCriador(emailRemetente)
                val nomeEquipe = equipe?.nome ?: "Equipe"

                val convite = ConviteTrabalho(
                    trabalhoId = trabalhoId,
                    tituloTrabalho = tituloTrabalho,
                    emailConvidado = emailConvidado,
                    emailRemetente = emailRemetente,
                    nomeEquipe = nomeEquipe
                )

                database.conviteTrabalhoDao().inserir(convite)

                Toast.makeText(
                    this@ConvidarTrabalhoActivity,
                    "✅ Convite enviado para $emailConvidado",
                    Toast.LENGTH_SHORT
                ).show()

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