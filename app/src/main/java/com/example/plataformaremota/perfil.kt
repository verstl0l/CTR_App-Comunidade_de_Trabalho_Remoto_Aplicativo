package com.example.plataformaremota

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class perfil : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_perfil)

        val prefs = getSharedPreferences("CTR_PREFS", Context.MODE_PRIVATE)
        val email = prefs.getString("emailUsuario", "") ?: ""
        val nome = prefs.getString("nomeUsuario", "Usuário") ?: "Usuário"
        val profissao = prefs.getString("profissaoUsuario", "Profissão") ?: "Profissão"
        val nomeEquipe = prefs.getString("nomeEquipe_$email", "Nenhuma equipe") ?: "Nenhuma equipe"

        // CARREGA OS DADOS DO USUÁRIO
        val txtNomePerfil = findViewById<TextView>(R.id.txtNomePerfil)
        val txtProfissaoPerfil = findViewById<TextView>(R.id.txtProfissaoPerfil)
        val txtEmailPerfil = findViewById<TextView>(R.id.txtEmailPerfil)
        val txtProfissaoCard = findViewById<TextView>(R.id.txtProfissaoCard)
        val txtEquipeCard = findViewById<TextView>(R.id.txtEquipeCard)

        txtNomePerfil.text = nome
        txtProfissaoPerfil.text = profissao
        txtEmailPerfil.text = email
        txtProfissaoCard.text = profissao
        txtEquipeCard.text = nomeEquipe

        val btnVoltar = findViewById<Button>(R.id.btnVoltar)
        btnVoltar.setOnClickListener {
            finish()
        }

        // BOTÃO MEUS TRABALHOS
        val btnMeusTrabalhos = findViewById<Button>(R.id.btnMeusTrabalhos)
        btnMeusTrabalhos.setOnClickListener {
            startActivity(Intent(this, participantes::class.java))
        }

        val btnSair = findViewById<Button>(R.id.btnSair)
        btnSair.setOnClickListener {
            prefs.edit()
                .putBoolean("logado", false)
                .remove("emailUsuario")
                .remove("nomeUsuario")
                .remove("profissaoUsuario")
                .apply()

            Toast.makeText(this, "Saindo da conta...", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_groups -> {
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
                R.id.nav_profile -> true
                else -> false
            }
        }
    }
}