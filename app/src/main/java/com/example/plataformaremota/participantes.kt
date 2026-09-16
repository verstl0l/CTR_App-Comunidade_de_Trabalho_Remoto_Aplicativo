package com.example.plataformaremota

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class participantes : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_participantes)

        try {
            val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
            bottomNav.setOnItemSelectedListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.nav_home -> {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                        true
                    }
                    R.id.nav_groups -> {
                        val prefs = getSharedPreferences("CTR_PREFS", Context.MODE_PRIVATE)
                        val email = prefs.getString("emailUsuario", "") ?: ""
                        val temEquipe = prefs.getBoolean("temEquipe_$email", false)

                        if (temEquipe) {
                            startActivity(Intent(this, produtos::class.java))
                        } else {
                            startActivity(Intent(this, CriarEquipeActivity::class.java))
                        }
                        finish()
                        true
                    }
                    R.id.nav_notifications -> {
                        startActivity(Intent(this, notificacao::class.java))
                        finish()
                        true
                    }
                    R.id.nav_profile -> {
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