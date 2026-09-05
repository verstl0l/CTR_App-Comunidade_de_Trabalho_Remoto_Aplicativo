package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inicial)

        try {
            val btnCadastrar = findViewById<Button>(R.id.button3)
            val btnEntrarEquipe = findViewById<Button>(R.id.button)
            val btnCriarEquipe = findViewById<Button>(R.id.button5)

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
        } catch (e: Exception) {
            Toast.makeText(this, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}