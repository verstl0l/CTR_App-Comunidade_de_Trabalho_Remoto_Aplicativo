package com.example.plataformaremota

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class perfil : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_perfil)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val prefs = getSharedPreferences("CTR_PREFS", Context.MODE_PRIVATE)
        val email = auth.currentUser?.email
            ?: prefs.getString("emailUsuario", "")
            ?: ""

        val txtNomePerfil = findViewById<TextView>(R.id.txtNomePerfil)
        val txtProfissaoPerfil = findViewById<TextView>(R.id.txtProfissaoPerfil)
        val txtEmailPerfil = findViewById<TextView>(R.id.txtEmailPerfil)
        val txtProfissaoCard = findViewById<TextView>(R.id.txtProfissaoCard)
        val txtEquipeCard = findViewById<TextView>(R.id.txtEquipeCard)

        txtEmailPerfil.text = email

        // ========== CARREGA DADOS DO FIRESTORE ==========
        lifecycleScope.launch {
            try {
                // Busca dados do usuário no Firestore
                val usuarioDoc = db.collection("usuarios").document(email).get().await()
                val nome = usuarioDoc.getString("nome") ?: "Usuário"
                val profissao = usuarioDoc.getString("profissao") ?: "Profissão"

                txtNomePerfil.text = nome
                txtProfissaoPerfil.text = profissao
                txtProfissaoCard.text = profissao

                // Atualiza o cache local
                prefs.edit()
                    .putString("nomeUsuario", nome)
                    .putString("profissaoUsuario", profissao)
                    .apply()

                // ========== BUSCA A EQUIPE DO USUÁRIO ==========
                // 1. Verifica se é CRIADOR
                val equipeCriador = db.collection("equipes")
                    .whereEqualTo("criadorEmail", email)
                    .limit(1)
                    .get()
                    .await()

                if (!equipeCriador.isEmpty) {
                    txtEquipeCard.text = equipeCriador.documents[0].getString("nome")
                } else {
                    // 2. Verifica se é MEMBRO
                    val membro = db.collection("membros_equipe")
                        .whereEqualTo("email", email)
                        .limit(1)
                        .get()
                        .await()

                    if (!membro.isEmpty) {
                        val equipeId = membro.documents[0].getString("equipeId") ?: ""
                        val equipeDoc = db.collection("equipes").document(equipeId).get().await()
                        txtEquipeCard.text = equipeDoc.getString("nome") ?: "Nenhuma equipe"
                    } else {
                        txtEquipeCard.text = "Nenhuma equipe"
                    }
                }
            } catch (e: Exception) {
                Log.e("PERFIL", "Erro ao carregar dados: ${e.message}")
                Toast.makeText(this@perfil, "Erro ao carregar dados: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        // ========== BOTÃO VOLTAR ==========
        val btnVoltar = findViewById<Button>(R.id.btnVoltar)
        btnVoltar.setOnClickListener { finish() }

        // ========== BOTÃO MEUS TRABALHOS ==========
        val btnMeusTrabalhos = findViewById<Button>(R.id.btnMeusTrabalhos)
        btnMeusTrabalhos.setOnClickListener {
            startActivity(Intent(this, participantes::class.java))
        }

        // ========== BOTÃO SAIR ==========
        val btnSair = findViewById<Button>(R.id.btnSair)
        btnSair.setOnClickListener {
            auth.signOut()
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

        // ========== BOTTOM NAVIGATION ==========
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_groups -> {
                    startActivity(Intent(this, produtos::class.java))
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