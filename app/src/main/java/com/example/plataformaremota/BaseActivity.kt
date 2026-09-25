package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

/**
 * Activity base que centraliza:
 *   - Configuracao do BottomNavigationView
 *   - Navegacao entre as 5 telas principais
 *   - Badge de chat e de notificacoes
 *
 * Uso: herde de BaseActivity e chame configurarBottomNavigation(R.id.nav_xxx)
 */
abstract class BaseActivity : AppCompatActivity() {

    /**
     * Configura o BottomNavigationView de forma padronizada.
     *
     * @param currentItemId ID do item atual (fica destacado). Se null, nenhum e destacado.
     */
    protected fun configurarBottomNavigation(currentItemId: Int? = null) {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation) ?: return

        if (currentItemId != null) {
            bottomNav.selectedItemId = currentItemId
        }

        bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == currentItemId) {
                return@setOnItemSelectedListener true
            }

            val destino = when (item.itemId) {
                R.id.nav_home -> MainActivity::class.java
                R.id.nav_chat -> ListaConversasActivity::class.java
                R.id.nav_groups -> MinhasEquipesActivity::class.java
                R.id.nav_notifications -> NotificacoesActivity::class.java
                R.id.nav_profile -> perfil::class.java
                else -> return@setOnItemSelectedListener false
            }

            startActivity(Intent(this, destino))
            finish()
            true
        }

        // Atualiza os badges (chat + notificacoes)
        lifecycleScope.launch {
            BadgeHelper.atualizarTodosBadges(this@BaseActivity, bottomNav)
        }
    }

    override fun onResume() {
        super.onResume()
        // Reaplica os badges ao voltar para a tela
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        if (bottomNav != null) {
            lifecycleScope.launch {
                BadgeHelper.atualizarTodosBadges(this@BaseActivity, bottomNav)
            }
        }
    }
}