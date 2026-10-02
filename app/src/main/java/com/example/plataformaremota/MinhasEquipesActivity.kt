package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MinhasEquipesActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""

    // ✅ Guard contra chamadas simultaneas
    private var carregando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_minhas_equipes)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""

        val btnVoltar = findViewById<Button>(R.id.btnVoltarMinhasEquipes)
        btnVoltar.setOnClickListener { finish() }

        val btnNovaEquipe = findViewById<Button>(R.id.btnNovaEquipe)
        btnNovaEquipe.setOnClickListener {
            startActivity(Intent(this, CriarEquipeActivity::class.java))
        }

        // ✅ NAO chama carregarEquipes aqui — o onResume cuida
        configurarBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        if (emailUsuario.isNotEmpty()) {
            carregando = false
            carregarEquipes()
        }
    }

    private fun carregarEquipes() {
        // ✅ Guard
        if (carregando) return
        carregando = true

        val container = findViewById<LinearLayout>(R.id.containerMinhasEquipes)
        container.removeAllViews()

        lifecycleScope.launch {
            try {
                val equipes = mutableListOf<EquipeItem>()

                // 1. Equipes que o usuario e CRIADOR
                val equipesCriador = db.collection("equipes")
                    .whereEqualTo("criadorEmail", emailUsuario)
                    .get()
                    .await()

                for (doc in equipesCriador.documents) {
                    val equipeId = doc.id
                    val nome = doc.getString("nome") ?: "Equipe"
                    val descricao = doc.getString("descricao") ?: "Sem descricao"

                    val trabalhos = db.collection("trabalhos")
                        .whereEqualTo("equipeId", equipeId)
                        .get()
                        .await()

                    equipes.add(
                        EquipeItem(
                            equipeId = equipeId,
                            nome = nome,
                            descricao = descricao,
                            papel = "Criador",
                            totalTrabalhos = trabalhos.size(),
                            ehDono = true
                        )
                    )
                }

                // 2. Equipes que o usuario e MEMBRO
                val membros = db.collection("membros_equipe")
                    .whereEqualTo("email", emailUsuario)
                    .get()
                    .await()

                for (membro in membros.documents) {
                    val equipeId = membro.getString("equipeId") ?: ""
                    val funcao = membro.getString("funcao") ?: "membro"

                    if (equipes.any { it.equipeId == equipeId }) continue

                    val equipeDoc = db.collection("equipes").document(equipeId).get().await()
                    if (!equipeDoc.exists()) continue

                    val nome = equipeDoc.getString("nome") ?: "Equipe"
                    val descricao = equipeDoc.getString("descricao") ?: "Sem descricao"

                    val trabalhos = db.collection("trabalhos")
                        .whereEqualTo("equipeId", equipeId)
                        .get()
                        .await()

                    equipes.add(
                        EquipeItem(
                            equipeId = equipeId,
                            nome = nome,
                            descricao = descricao,
                            papel = if (funcao == "administrador") "Administrador" else "Membro",
                            totalTrabalhos = trabalhos.size(),
                            ehDono = false
                        )
                    )
                }

                // 3. Mostra na tela
                if (equipes.isEmpty()) {
                    val txtVazio = TextView(this@MinhasEquipesActivity).apply {
                        text = "Voce nao esta em nenhuma equipe"
                        setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                        textSize = 14f
                        setPadding(0, 60, 0, 60)
                        gravity = android.view.Gravity.CENTER
                    }
                    container.addView(txtVazio)
                    return@launch
                }

                val inflater = LayoutInflater.from(this@MinhasEquipesActivity)

                equipes.forEach { equipe ->
                    val view = inflater.inflate(R.layout.item_minha_equipe, container, false)

                    val txtNome = view.findViewById<TextView>(R.id.txtNomeMinhaEquipe)
                    val txtDescricao = view.findViewById<TextView>(R.id.txtDescricaoMinhaEquipe)
                    val txtPapel = view.findViewById<TextView>(R.id.txtPapelMinhaEquipe)
                    val txtTrabalhos = view.findViewById<TextView>(R.id.txtTrabalhosMinhaEquipe)

                    txtNome.text = equipe.nome
                    txtDescricao.text = equipe.descricao
                    txtPapel.text = equipe.papel
                    txtTrabalhos.text = "${equipe.totalTrabalhos} trabalhos"

                    if (equipe.ehDono) {
                        txtPapel.setTextColor(android.graphics.Color.parseColor("#F5E6D0"))
                    } else {
                        txtPapel.setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                    }

                    view.setOnClickListener {
                        if (equipe.ehDono) {
                            val intent = Intent(this@MinhasEquipesActivity, produtos::class.java)
                            intent.putExtra("equipeId", equipe.equipeId)
                            startActivity(intent)
                        } else {
                            val intent = Intent(this@MinhasEquipesActivity, EntregarTrabalhoActivity::class.java)
                            intent.putExtra("equipeId", equipe.equipeId)
                            startActivity(intent)
                        }
                    }

                    container.addView(view)
                }

            } catch (e: Exception) {
                Log.e("MINHAS_EQUIPES", "Erro: ${e.message}")
                Toast.makeText(this@MinhasEquipesActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                // ✅ Libera o guard
                carregando = false
            }
        }
    }

    private fun configurarBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_chat -> {
                    startActivity(Intent(this, ListaConversasActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_groups -> true  // Ja estamos aqui
                R.id.nav_notifications -> {
                    startActivity(Intent(this, NotificacoesActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, perfil::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }

        lifecycleScope.launch {
            BadgeHelper.atualizarBadgeChat(this@MinhasEquipesActivity, bottomNav)
        }
    }

    data class EquipeItem(
        val equipeId: String,
        val nome: String,
        val descricao: String,
        val papel: String,
        val totalTrabalhos: Int,
        val ehDono: Boolean
    )
}