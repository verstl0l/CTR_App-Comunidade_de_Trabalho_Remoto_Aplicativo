package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class CriarTrabalhoActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_criar_trabalho)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        val email = auth.currentUser?.email ?: ""

        val edtTitulo = findViewById<EditText>(R.id.edtTituloTrabalho)
        val edtDescricao = findViewById<EditText>(R.id.edtDescricaoTrabalho)
        val edtCategoria = findViewById<EditText>(R.id.edtCategoriaTrabalho)
        val edtPrazo = findViewById<EditText>(R.id.edtPrazoTrabalho)
        val btnPublicar = findViewById<Button>(R.id.btnPublicarTrabalho)
        val btnVoltar = findViewById<Button>(R.id.btnVoltarCriarTrabalho)

        btnVoltar.setOnClickListener { finish() }

        btnPublicar.setOnClickListener {
            val titulo = edtTitulo.text.toString().trim()
            val descricao = edtDescricao.text.toString().trim()
            val categoria = edtCategoria.text.toString().trim()
            val prazo = edtPrazo.text.toString().trim()

            if (titulo.isEmpty() || descricao.isEmpty() || categoria.isEmpty() || prazo.isEmpty()) {
                Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnPublicar.isEnabled = false
            btnPublicar.text = "PUBLICANDO..."

            lifecycleScope.launch {
                try {
                    // ✅ Aceita equipeId vindo do Intent OU busca a equipe do criador
                    val equipeIdIntent = intent.getStringExtra("equipeId")

                    val equipeId = if (equipeIdIntent != null) {
                        equipeIdIntent
                    } else {
                        val equipe = db.collection("equipes")
                            .whereEqualTo("criadorEmail", email)
                            .limit(1)
                            .get()
                            .await()

                        if (equipe.isEmpty) {
                            Toast.makeText(this@CriarTrabalhoActivity, "Crie uma equipe primeiro", Toast.LENGTH_SHORT).show()
                            btnPublicar.isEnabled = true
                            btnPublicar.text = "PUBLICAR TRABALHO"
                            return@launch
                        }
                        equipe.documents[0].id
                    }

                    val trabalho = hashMapOf(
                        "titulo" to titulo,
                        "descricao" to descricao,
                        "categoria" to categoria,
                        "prazo" to prazo,
                        "equipeId" to equipeId,
                        "criadorEmail" to email,
                        "responsavelEmail" to email,   // ✅ BUG CORRIGIDO: dono vira responsável padrão
                        "status" to "pendente",
                        "criadoEm" to System.currentTimeMillis()
                    )

                    db.collection("trabalhos")
                        .add(trabalho)
                        .addOnSuccessListener {
                            Log.d("CRIAR_TRABALHO", "✅ Trabalho publicado com responsável: $email")
                            Toast.makeText(this@CriarTrabalhoActivity, "✅ Trabalho publicado!", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Log.e("CRIAR_TRABALHO", "Erro: ${e.message}")
                            Toast.makeText(this@CriarTrabalhoActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                            btnPublicar.isEnabled = true
                            btnPublicar.text = "PUBLICAR TRABALHO"
                        }

                } catch (e: Exception) {
                    Log.e("CRIAR_TRABALHO", "Erro: ${e.message}")
                    Toast.makeText(this@CriarTrabalhoActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                    btnPublicar.isEnabled = true
                    btnPublicar.text = "PUBLICAR TRABALHO"
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