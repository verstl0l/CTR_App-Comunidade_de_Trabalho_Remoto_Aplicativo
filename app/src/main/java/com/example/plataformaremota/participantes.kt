package com.example.plataformaremota

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
                        true
                    }
                    R.id.nav_groups -> {
                        startActivity(Intent(this, produtos::class.java))
                        true
                    }
                    R.id.nav_notifications -> {
                        startActivity(Intent(this, notificacao::class.java))
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