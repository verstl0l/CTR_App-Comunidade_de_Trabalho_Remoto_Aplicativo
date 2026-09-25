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

class ConvidarTrabalhoActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_convidar_trabalho)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        val emailRemetente = auth.currentUser?.email ?: ""

        val trabalhoId = intent.getStringExtra("trabalhoId") ?: ""
        val tituloTrabalho = intent.getStringExtra("tituloTrabalho") ?: ""

        val txtTitulo = findViewById<TextView>(R.id.txtTituloTrabalhoConvite)
        val edtEmail = findViewById<EditText>(R.id.edtEmailConvidado)
        val btnEnviar = findViewById<Button>(R.id.btnEnviarConviteTrabalho)
        val btnVoltar = findViewById<Button>(R.id.btnVoltarConvidar)

        txtTitulo.text = "Trabalho: $tituloTrabalho"

        btnVoltar.setOnClickListener { finish() }

        btnEnviar.setOnClickListener {
            val emailConvidado = edtEmail.text.toString().trim().lowercase()

            if (emailConvidado.isEmpty()) {
                Toast.makeText(this, "Digite um email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnEnviar.isEnabled = false
            btnEnviar.text = "ENVIANDO..."

            lifecycleScope.launch {
                try {
                    val equipe = db.collection("equipes")
                        .whereEqualTo("criadorEmail", emailRemetente)
                        .limit(1)
                        .get()
                        .await()

                    val nomeEquipe = if (!equipe.isEmpty) {
                        equipe.documents[0].getString("nome") ?: "Equipe"
                    } else "Equipe"

                    val convite = hashMapOf(
                        "trabalhoId" to trabalhoId,
                        "tituloTrabalho" to tituloTrabalho,
                        "emailConvidado" to emailConvidado,
                        "emailRemetente" to emailRemetente,
                        "nomeEquipe" to nomeEquipe,
                        "status" to "pendente",
                        "criadoEm" to System.currentTimeMillis()
                    )

                    db.collection("convites_trabalho").add(convite).await()

                    Log.d("CONVITE_TRABALHO", "✅ Convite enviado para $emailConvidado")
                    Toast.makeText(
                        this@ConvidarTrabalhoActivity,
                        "✅ Convite enviado para $emailConvidado",
                        Toast.LENGTH_SHORT
                    ).show()

                    finish()

                } catch (e: Exception) {
                    Log.e("CONVITE_TRABALHO", "Erro: ${e.message}")
                    Toast.makeText(this@ConvidarTrabalhoActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                    btnEnviar.isEnabled = true
                    btnEnviar.text = "ENVIAR CONVITE"
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
    }
}