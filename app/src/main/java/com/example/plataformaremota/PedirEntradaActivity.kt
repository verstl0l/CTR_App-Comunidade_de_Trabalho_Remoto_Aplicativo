package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.plataformaremota.data.database.AppDatabase
import com.example.plataformaremota.data.entity.PedidoEntrada
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

class PedirEntradaActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pedir_entrada)

        val database = AppDatabase.getDatabase(this)
        val prefs = getSharedPreferences("CTR_PREFS", MODE_PRIVATE)
        val emailSolicitante = prefs.getString("emailUsuario", "") ?: ""

        val equipeId = intent.getIntExtra("equipeId", -1)
        val nomeEquipe = intent.getStringExtra("nomeEquipe") ?: ""

        val txtEquipe = findViewById<TextView>(R.id.txtNomeEquipePedido)
        val edtNome = findViewById<EditText>(R.id.edtNomeSolicitante)
        val edtMotivos = findViewById<EditText>(R.id.edtMotivos)
        val edtEspecialidades = findViewById<EditText>(R.id.edtEspecialidades)
        val btnEnviar = findViewById<Button>(R.id.btnEnviarPedido)
        val btnVoltar = findViewById<Button>(R.id.btnVoltarPedir)

        txtEquipe.text = "Equipe: $nomeEquipe"

        btnVoltar.setOnClickListener { finish() }

        btnEnviar.setOnClickListener {
            val nome = edtNome.text.toString().trim()
            val motivos = edtMotivos.text.toString().trim()
            val especialidades = edtEspecialidades.text.toString().trim()

            if (nome.isEmpty() || motivos.isEmpty() || especialidades.isEmpty()) {
                Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val pedido = PedidoEntrada(
                    equipeId = equipeId,
                    nomeEquipe = nomeEquipe,
                    emailSolicitante = emailSolicitante,
                    nomeSolicitante = nome,
                    motivos = motivos,
                    especialidades = especialidades
                )

                database.pedidoEntradaDao().inserir(pedido)

                Toast.makeText(
                    this@PedirEntradaActivity,
                    "✅ Pedido enviado! Aguarde aprovação.",
                    Toast.LENGTH_SHORT
                ).show()

                finish()
            }
        }

        // Bottom Navigation
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