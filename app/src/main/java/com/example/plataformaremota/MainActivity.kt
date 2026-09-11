package com.example.plataformaremota

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inicial)

        val btnCadastrar = findViewById<Button>(R.id.button3)
        val btnEntrarEquipe = findViewById<Button>(R.id.button)
        val btnCriarEquipe = findViewById<Button>(R.id.button5)

        val prefs = getSharedPreferences("CTR_PREFS", Context.MODE_PRIVATE)
        val logado = prefs.getBoolean("logado", false)
        val email = prefs.getString("emailUsuario", "") ?: ""

        // ✅ Verifica se ESTE usuário tem equipe
        val temEquipe = prefs.getBoolean("temEquipe_$email", false)

        if (logado) {
            btnCadastrar.visibility = android.view.View.GONE
        } else {
            btnCadastrar.visibility = android.view.View.VISIBLE
        }

        if (temEquipe) {
            btnCriarEquipe.visibility = android.view.View.GONE
        } else {
            btnCriarEquipe.visibility = android.view.View.VISIBLE
        }

        btnCadastrar.setOnClickListener {
            startActivity(Intent(this, CadastroActivity::class.java))
        }

        btnEntrarEquipe.setOnClickListener {
            startActivity(Intent(this, participantes::class.java))
        }

        btnCriarEquipe.setOnClickListener {
            startActivity(Intent(this, equipe::class.java))
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> true
                R.id.nav_groups -> {
                    startActivity(Intent(this, produtos::class.java))
                    true
                }
                R.id.nav_notifications -> {
                    startActivity(Intent(this, notificacao::class.java))
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, participantes::class.java))
                    true
                }
                else -> false
            }
        }
    }
}