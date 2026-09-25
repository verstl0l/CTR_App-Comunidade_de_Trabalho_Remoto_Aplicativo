package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class participantes : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_participantes)

        configurarBottomNavigation()
    }

    private fun configurarBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.selectedItemId = R.id.nav_profile

        bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_chat -> {                                    // ✅ CORRIGIDO: faltava
                    startActivity(Intent(this, ListaConversasActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_groups -> {                                  // ✅ CORRIGIDO: era produtos
                    startActivity(Intent(this, MinhasEquipesActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_notifications -> {
                    startActivity(Intent(this, NotificacoesActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_profile -> true
                else -> false
            }
        }
    }
}