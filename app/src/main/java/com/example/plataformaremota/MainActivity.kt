package com.example.plataformaremota

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.plataformaremota.data.database.AppDatabase
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var btnCadastrar: Button
    private lateinit var btnEntrarEquipe: Button
    private lateinit var btnCriarEquipe: Button
    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inicial)

        database = AppDatabase.getDatabase(this)

        btnCadastrar = findViewById(R.id.button3)
        btnEntrarEquipe = findViewById(R.id.button)
        btnCriarEquipe = findViewById(R.id.button5)

        btnCadastrar.setOnClickListener {
            startActivity(Intent(this, CadastroActivity::class.java))
        }

        btnEntrarEquipe.setOnClickListener {
            startActivity(Intent(this, participantes::class.java))
        }

        btnCriarEquipe.setOnClickListener {
            // Sempre vai para produtos, que decide se mostra vazio ou dashboard
            startActivity(Intent(this, produtos::class.java))
        }

        configurarBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        atualizarBotoes()
    }

    private fun atualizarBotoes() {
        val prefs = getSharedPreferences("CTR_PREFS", Context.MODE_PRIVATE)
        val logado = prefs.getBoolean("logado", false)
        val email = prefs.getString("emailUsuario", "") ?: ""

        // Verifica direto no banco se tem equipe
        lifecycleScope.launch {
            val equipe = database.equipeDao().buscarPorCriador(email)
            val temEquipe = equipe != null

            // Botão "Cadastrar-se" só aparece se NÃO estiver logado
            btnCadastrar.visibility = if (logado) View.GONE else View.VISIBLE

            // Botão "Criar Equipe" só aparece se NÃO tiver equipe
            btnCriarEquipe.visibility = if (temEquipe) View.GONE else View.VISIBLE
        }
    }

    private fun configurarBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> true

                R.id.nav_groups -> {
                    // ✅ SEMPRE vai para produtos
                    // O produtos.kt decide se mostra vazio ou dashboard
                    startActivity(Intent(this@MainActivity, produtos::class.java))
                    true
                }

                R.id.nav_notifications -> {
                    startActivity(Intent(this, notificacao::class.java))
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, perfil::class.java))
                    true
                }
                else -> false
            }
        }
    }
}