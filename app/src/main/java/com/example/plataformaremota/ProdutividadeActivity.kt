package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProdutividadeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""
    private var equipeId: String = ""
    private var ehDono: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_produtividade)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""
        equipeId = intent.getStringExtra("equipeId") ?: ""

        if (equipeId.isEmpty()) {
            Toast.makeText(this, "Equipe não encontrada", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val btnVoltar = findViewById<Button>(R.id.btnVoltarProdutividade)
        btnVoltar.setOnClickListener { finish() }

        configurarBottomNavigation()
        carregarEstatisticas()
    }

    private fun carregarEstatisticas() {
        lifecycleScope.launch {
            try {
                // 1. Verifica se é dono
                val equipeDoc = db.collection("equipes").document(equipeId).get().await()
                val criadorEmail = equipeDoc.getString("criadorEmail") ?: ""
                ehDono = criadorEmail == emailUsuario

                // 2. Busca todos os trabalhos da equipe
                val trabalhos = db.collection("trabalhos")
                    .whereEqualTo("equipeId", equipeId)
                    .get()
                    .await()

                val total = trabalhos.size()
                var concluidos = 0
                var emProgresso = 0
                var pendentes = 0

                // Contador por membro (email -> quantidade)
                val porMembro = mutableMapOf<String, Int>()

                trabalhos.documents.forEach { doc ->
                    val status = doc.getString("status") ?: "pendente"
                    val responsavel = doc.getString("responsavelEmail") ?: ""

                    when (status) {
                        "concluido" -> concluidos++
                        "em_progresso" -> emProgresso++
                        else -> pendentes++
                    }

                    if (responsavel.isNotEmpty()) {
                        porMembro[responsavel] = (porMembro[responsavel] ?: 0) + 1
                    }
                }

                // 3. Atualiza a UI
                findViewById<TextView>(R.id.txtTotalTrabalhos).text = total.toString()

                findViewById<TextView>(R.id.txtQtdConcluido).text = concluidos.toString()
                findViewById<TextView>(R.id.txtQtdProgresso).text = emProgresso.toString()
                findViewById<TextView>(R.id.txtQtdPendente).text = pendentes.toString()

                // ProgressBars (proporção em %)
                if (total > 0) {
                    findViewById<ProgressBar>(R.id.barConcluido).progress = (concluidos * 100) / total
                    findViewById<ProgressBar>(R.id.barProgresso).progress = (emProgresso * 100) / total
                    findViewById<ProgressBar>(R.id.barPendente).progress = (pendentes * 100) / total
                }

                // Taxa de conclusão
                val taxa = if (total > 0) (concluidos * 100) / total else 0
                findViewById<TextView>(R.id.txtTaxaConclusao).text = "$taxa%"
                findViewById<TextView>(R.id.txtDetalheTaxa).text = "$concluidos de $total trabalhos"

                // 4. Card "Por Membro" (só pro dono)
                if (ehDono && porMembro.isNotEmpty()) {
                    findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardPorMembro).visibility = View.VISIBLE
                    montarListaPorMembro(porMembro, total)
                }

            } catch (e: Exception) {
                Log.e("PRODUTIVIDADE", "Erro: ${e.message}")
                Toast.makeText(this@ProdutividadeActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun montarListaPorMembro(porMembro: Map<String, Int>, total: Int) {
        val container = findViewById<LinearLayout>(R.id.containerPorMembro)
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)

        // Ordena por quantidade (maior primeiro)
        val ordenado = porMembro.entries.sortedByDescending { it.value }

        lifecycleScope.launch {
            try {
                ordenado.forEach { (emailMembro, qtd) ->
                    // Busca o nome do membro
                    val userDoc = db.collection("usuarios").document(emailMembro).get().await()
                    val nome = userDoc.getString("nome") ?: emailMembro

                    val view = inflater.inflate(R.layout.item_produtividade_membro, container, false)

                    view.findViewById<TextView>(R.id.txtNomeMembroProd).text = nome
                    view.findViewById<TextView>(R.id.txtQtdMembroProd).text = qtd.toString()

                    val progresso = if (total > 0) (qtd * 100) / total else 0
                    view.findViewById<ProgressBar>(R.id.barMembroProd).progress = progresso

                    container.addView(view)
                }
            } catch (e: Exception) {
                Log.e("PRODUTIVIDADE", "Erro nomes: ${e.message}")
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
                R.id.nav_groups -> {
                    startActivity(Intent(this, MinhasEquipesActivity::class.java))
                    finish()
                    true
                }
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
            BadgeHelper.atualizarBadgeChat(this@ProdutividadeActivity, bottomNav)
        }
    }
}