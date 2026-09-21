package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : AppCompatActivity() {

    private lateinit var btnCadastrar: Button
    private lateinit var btnEntrarEquipe: Button
    private lateinit var btnCriarEquipe: Button

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inicial)

        // ✅ Inicializa o Cloudinary
        CloudinaryConfig.init(this)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

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
            startActivity(Intent(this, produtos::class.java))
        }

        configurarBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        atualizarBotoes()
    }

    private fun atualizarBotoes() {
        val prefs = getSharedPreferences("CTR_PREFS", MODE_PRIVATE)
        val logado = prefs.getBoolean("logado", false)
        val email = auth.currentUser?.email ?: ""

        lifecycleScope.launch {
            try {
                val equipe = db.collection("equipes")
                    .whereEqualTo("criadorEmail", email)
                    .limit(1)
                    .get()
                    .await()

                val temEquipe = !equipe.isEmpty

                btnCadastrar.visibility = if (logado) View.GONE else View.VISIBLE
                btnCriarEquipe.visibility = if (temEquipe) View.GONE else View.VISIBLE

            } catch (e: Exception) {
                btnCadastrar.visibility = if (logado) View.GONE else View.VISIBLE
                btnCriarEquipe.visibility = View.VISIBLE
            }
        }
    }

    private fun configurarBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> true

                R.id.nav_groups -> {
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