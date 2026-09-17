package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.plataformaremota.data.database.AppDatabase
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

class EntregarTrabalhoActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_entregar_trabalho)

        database = AppDatabase.getDatabase(this)
        val prefs = getSharedPreferences("CTR_PREFS", MODE_PRIVATE)
        val email = prefs.getString("emailUsuario", "") ?: ""

        val txtNomeEquipe = findViewById<TextView>(R.id.txtNomeEquipeMembro)
        val containerTrabalhos = findViewById<LinearLayout>(R.id.containerTrabalhosMembro)

        lifecycleScope.launch {
            // Busca o membro
            val membro = database.membroEquipeDao().buscarPorEmail(email)
            if (membro == null) {
                Toast.makeText(
                    this@EntregarTrabalhoActivity,
                    "Você não é membro de nenhuma equipe",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
                return@launch
            }

            // Busca a equipe
            val equipe = database.equipeDao().buscarPorId(membro.equipeId)
            txtNomeEquipe.text = equipe?.nome ?: "Equipe"

            // Busca os trabalhos da equipe
            val trabalhos = database.trabalhoDao().listarPorEquipe(membro.equipeId)
            val inflater = LayoutInflater.from(this@EntregarTrabalhoActivity)

            if (trabalhos.isEmpty()) {
                val txtVazio = TextView(this@EntregarTrabalhoActivity).apply {
                    text = "Nenhum trabalho atribuído ainda"
                    setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                    textSize = 14f
                    setPadding(0, 60, 0, 60)
                    gravity = android.view.Gravity.CENTER
                }
                containerTrabalhos.addView(txtVazio)
                return@launch
            }

            trabalhos.forEach { trabalho ->
                val view = inflater.inflate(R.layout.item_trabalho_membro, containerTrabalhos, false)

                view.findViewById<TextView>(R.id.txtTituloTrabalhoMembro).text = trabalho.titulo
                view.findViewById<TextView>(R.id.txtDescricaoTrabalhoMembro).text = trabalho.descricao
                view.findViewById<TextView>(R.id.txtPrazoTrabalhoMembro).text = "Entrega: ${trabalho.prazo}"
                view.findViewById<TextView>(R.id.txtStatusTrabalhoMembro).text = "Em Progresso"

                view.findViewById<Button>(R.id.btnEntregarTrabalho).setOnClickListener {
                    Toast.makeText(
                        this@EntregarTrabalhoActivity,
                        "✅ Trabalho '${trabalho.titulo}' iniciado!",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                containerTrabalhos.addView(view)
            }
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
                R.id.nav_groups -> true
                R.id.nav_notifications -> {
                    startActivity(Intent(this, notificacao::class.java))
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
    }
}