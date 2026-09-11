package com.example.plataformaremota

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class produtos : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("CTR_PREFS", Context.MODE_PRIVATE)
        val email = prefs.getString("emailUsuario", "") ?: ""
        val temEquipe = prefs.getBoolean("temEquipe_$email", false)  // ✅ Por usuário

        if (temEquipe) {
            setContentView(R.layout.activity_produtos)

            val btnCriarTrabalho = findViewById<Button>(R.id.btnCriarTrabalho)

            btnCriarTrabalho.setOnClickListener {
                startActivity(Intent(this, TrabalhoActivity::class.java))
            }
        } else {
            setContentView(R.layout.activity_produtos_vazio)

            val btnCriarEquipeVazio = findViewById<Button>(R.id.btnCriarEquipeVazio)

            btnCriarEquipeVazio.setOnClickListener {
                startActivity(Intent(this, equipe::class.java))
            }
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    true
                }
                R.id.nav_groups -> true
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

