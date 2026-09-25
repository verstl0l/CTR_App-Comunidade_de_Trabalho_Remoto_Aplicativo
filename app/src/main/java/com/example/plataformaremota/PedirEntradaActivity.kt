package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PedirEntradaActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pedir_entrada)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        val emailSolicitante = auth.currentUser?.email ?: ""

        val equipeId = intent.getStringExtra("equipeId") ?: ""
        val nomeEquipe = intent.getStringExtra("nomeEquipe") ?: ""

        val txtEquipe = findViewById<TextView>(R.id.txtNomeEquipePedido)
        val edtMotivos = findViewById<EditText>(R.id.edtMotivos)
        val edtEspecialidades = findViewById<EditText>(R.id.edtEspecialidades)
        val btnEnviar = findViewById<Button>(R.id.btnEnviarPedido)
        val btnVoltar = findViewById<Button>(R.id.btnVoltarPedir)

        txtEquipe.text = "Equipe: $nomeEquipe"

        btnVoltar.setOnClickListener { finish() }

        btnEnviar.setOnClickListener {
            val motivos = edtMotivos.text.toString().trim()
            val especialidades = edtEspecialidades.text.toString().trim()

            if (motivos.isEmpty() || especialidades.isEmpty()) {
                Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnEnviar.isEnabled = false
            btnEnviar.text = "ENVIANDO..."

            lifecycleScope.launch {
                try {
                    val usuarioDoc = db.collection("usuarios").document(emailSolicitante).get().await()
                    val nomeSolicitante = usuarioDoc.getString("nome") ?: "Usuário"

                    val pedido = hashMapOf(
                        "equipeId" to equipeId,
                        "nomeEquipe" to nomeEquipe,
                        "emailSolicitante" to emailSolicitante,
                        "nomeSolicitante" to nomeSolicitante,
                        "motivos" to motivos,
                        "especialidades" to especialidades,
                        "status" to "pendente",
                        "criadoEm" to System.currentTimeMillis()
                    )

                    db.collection("pedidos_entrada").add(pedido).await()

                    Log.d("PEDIR_ENTRADA", "✅ Pedido enviado!")
                    Toast.makeText(
                        this@PedirEntradaActivity,
                        "✅ Pedido enviado! Aguarde aprovação.",
                        Toast.LENGTH_SHORT
                    ).show()

                    finish()

                } catch (e: Exception) {
                    Log.e("PEDIR_ENTRADA", "Erro: ${e.message}")
                    Toast.makeText(this@PedirEntradaActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                    btnEnviar.isEnabled = true
                    btnEnviar.text = "ENVIAR PEDIDO"
                }
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