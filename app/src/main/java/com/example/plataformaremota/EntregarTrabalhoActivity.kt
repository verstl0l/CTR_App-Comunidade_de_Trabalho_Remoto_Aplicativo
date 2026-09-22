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

class EntregarTrabalhoActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_entregar_trabalho)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        val email = auth.currentUser?.email ?: ""

        val txtNomeEquipe = findViewById<TextView>(R.id.txtNomeEquipeMembro)
        val containerTrabalhos = findViewById<LinearLayout>(R.id.containerTrabalhosMembro)

        lifecycleScope.launch {
            try {
                val membro = db.collection("membros_equipe")
                    .whereEqualTo("email", email)
                    .limit(1)
                    .get()
                    .await()

                if (membro.isEmpty) {
                    Toast.makeText(this@EntregarTrabalhoActivity, "Você não é membro de nenhuma equipe", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }

                val equipeId = membro.documents[0].getString("equipeId") ?: ""

                val equipeDoc = db.collection("equipes").document(equipeId).get().await()
                txtNomeEquipe.text = equipeDoc.getString("nome") ?: "Equipe"

                val trabalhos = db.collection("trabalhos")
                    .whereEqualTo("equipeId", equipeId)
                    .get()
                    .await()

                val inflater = LayoutInflater.from(this@EntregarTrabalhoActivity)

                if (trabalhos.isEmpty) {
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

                trabalhos.documents.forEach { doc ->
                    val view = inflater.inflate(R.layout.item_trabalho_membro, containerTrabalhos, false)

                    view.findViewById<TextView>(R.id.txtTituloTrabalhoMembro).text = doc.getString("titulo") ?: ""
                    view.findViewById<TextView>(R.id.txtDescricaoTrabalhoMembro).text = doc.getString("descricao") ?: ""
                    view.findViewById<TextView>(R.id.txtPrazoTrabalhoMembro).text = "Entrega: ${doc.getString("prazo") ?: ""}"
                    view.findViewById<TextView>(R.id.txtStatusTrabalhoMembro).text = "Em Progresso"

                    view.findViewById<Button>(R.id.btnEntregarTrabalho).setOnClickListener {
                        Toast.makeText(this@EntregarTrabalhoActivity, "✅ Trabalho '${doc.getString("titulo")}' iniciado!", Toast.LENGTH_SHORT).show()
                    }

                    containerTrabalhos.addView(view)
                }

            } catch (e: Exception) {
                Log.e("ENTREGAR_TRABALHO", "Erro: ${e.message}")
            }
        }

        configurarBottomNavigation()
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