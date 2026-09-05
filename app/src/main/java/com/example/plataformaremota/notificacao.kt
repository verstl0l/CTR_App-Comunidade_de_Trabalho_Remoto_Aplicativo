package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class notificacao : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notificacao)

        try {
            val btnVoltar = findViewById<Button>(R.id.btnVoltar)
            val btnAceitar = findViewById<Button>(R.id.btnAceitar)
            val btnRecusar = findViewById<Button>(R.id.btnRecusar)

            btnVoltar.setOnClickListener {
                finish()
            }

            btnAceitar.setOnClickListener {
                Toast.makeText(this, "✅ Convite aceito!", Toast.LENGTH_SHORT).show()
                finish()
            }

            btnRecusar.setOnClickListener {
                Toast.makeText(this, "❌ Convite recusado", Toast.LENGTH_SHORT).show()
                finish()
            }

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
                    R.id.nav_notifications -> true
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