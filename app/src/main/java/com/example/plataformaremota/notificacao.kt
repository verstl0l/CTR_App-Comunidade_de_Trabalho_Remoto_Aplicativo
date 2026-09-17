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

class notificacao : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""
    private var nomeUsuario: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notificacao)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""

        Log.d("NOTIFICACAO_DEBUG", "===== onCreate =====")
        Log.d("NOTIFICACAO_DEBUG", "Email do usuário: '$emailUsuario'")

        lifecycleScope.launch {
            try {
                val userDoc = db.collection("usuarios").document(emailUsuario).get().await()
                nomeUsuario = userDoc.getString("nome") ?: "Usuário"
                Log.d("NOTIFICACAO_DEBUG", "Nome do usuário: '$nomeUsuario'")
            } catch (e: Exception) {
                nomeUsuario = "Usuário"
            }
        }

        // ✅ NÃO chama carregarConvites() aqui
        configurarBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        Log.d("NOTIFICACAO_DEBUG", "===== onResume =====")
        carregarConvites()  // ✅ Só aqui
    }

    private fun carregarConvites() {
        val container = findViewById<LinearLayout>(R.id.containerConvites)
        val containerOutros = findViewById<LinearLayout>(R.id.containerOutrosConvites)

        container.removeAllViews()
        containerOutros.removeAllViews()

        lifecycleScope.launch {
            try {
                val convites = db.collection("convites_equipe")
                    .whereEqualTo("emailConvidado", emailUsuario)
                    .whereEqualTo("status", "pendente")
                    .get()
                    .await()

                Log.d("NOTIFICACAO_DEBUG", "Total de convites encontrados: ${convites.size()}")
                convites.documents.forEachIndexed { index, doc ->
                    Log.d("NOTIFICACAO_DEBUG", "Convite [$index] ID: ${doc.id}")
                    Log.d("NOTIFICACAO_DEBUG", "   → emailConvidado: ${doc.getString("emailConvidado")}")
                    Log.d("NOTIFICACAO_DEBUG", "   → nomeEquipe: ${doc.getString("nomeEquipe")}")
                    Log.d("NOTIFICACAO_DEBUG", "   → status: ${doc.getString("status")}")
                }

                val inflater = LayoutInflater.from(this@notificacao)

                if (convites.isEmpty) {
                    val txtVazio = TextView(this@notificacao).apply {
                        text = "Nenhum convite pendente"
                        setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                        textSize = 14f
                        setPadding(0, 60, 0, 60)
                        gravity = android.view.Gravity.CENTER
                    }
                    container.addView(txtVazio)
                    return@launch
                }

                val primeiro = convites.documents[0]
                val primeiroId = primeiro.id
                val nomeEquipe1 = primeiro.getString("nomeEquipe") ?: "Equipe"
                val descEquipe1 = primeiro.getString("descricaoEquipe") ?: "Sem descrição"
                val nomeRemetente1 = primeiro.getString("nomeRemetente") ?: "Usuário"
                val equipeId1 = primeiro.getString("equipeId") ?: ""

                val cardPrincipal = inflater.inflate(R.layout.item_convite_principal, container, false)

                cardPrincipal.findViewById<TextView>(R.id.txtConvidadoPor).text = "Convidado por"
                cardPrincipal.findViewById<TextView>(R.id.txtNomeRemetente).text = nomeRemetente1
                cardPrincipal.findViewById<TextView>(R.id.txtNomeEquipeConvite).text = nomeEquipe1
                cardPrincipal.findViewById<TextView>(R.id.txtDescricaoConvite).text = descEquipe1

                cardPrincipal.findViewById<Button>(R.id.btnAceitarConvite).setOnClickListener {
                    aceitarConvite(primeiroId, equipeId1, nomeEquipe1)
                }

                cardPrincipal.findViewById<Button>(R.id.btnRecusarConvite).setOnClickListener {
                    recusarConvite(primeiroId)
                }

                container.addView(cardPrincipal)

                if (convites.size() > 1) {
                    convites.documents.drop(1).forEach { doc ->
                        val nomeEquipeOutro = doc.getString("nomeEquipe") ?: "Equipe"
                        val nomeRemetenteOutro = doc.getString("nomeRemetente") ?: "Usuário"

                        val cardOutro = inflater.inflate(android.R.layout.simple_list_item_2, containerOutros, false)
                        val t1 = cardOutro.findViewById<TextView>(android.R.id.text1)
                        val t2 = cardOutro.findViewById<TextView>(android.R.id.text2)

                        t1.text = nomeEquipeOutro
                        t1.setTextColor(android.graphics.Color.WHITE)
                        t2.text = "Por $nomeRemetenteOutro"
                        t2.setTextColor(android.graphics.Color.GRAY)
                        cardOutro.setPadding(0, 24, 0, 24)

                        containerOutros.addView(cardOutro)
                    }
                }

            } catch (e: Exception) {
                Log.e("NOTIFICACAO", "Erro: ${e.message}", e)
                Toast.makeText(this@notificacao, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun aceitarConvite(conviteId: String, equipeId: String, nomeEquipe: String) {
        lifecycleScope.launch {
            try {
                db.collection("convites_equipe").document(conviteId)
                    .update("status", "aceito")
                    .await()

                val membro = hashMapOf(
                    "equipeId" to equipeId,
                    "email" to emailUsuario,
                    "nome" to nomeUsuario,
                    "funcao" to "membro",
                    "entrouEm" to System.currentTimeMillis()
                )
                db.collection("membros_equipe").add(membro).await()

                Toast.makeText(this@notificacao, "✅ Convite aceito! Bem-vindo à $nomeEquipe", Toast.LENGTH_SHORT).show()
                carregarConvites()

            } catch (e: Exception) {
                Log.e("NOTIFICACAO", "Erro ao aceitar: ${e.message}")
                Toast.makeText(this@notificacao, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun recusarConvite(conviteId: String) {
        lifecycleScope.launch {
            try {
                db.collection("convites_equipe").document(conviteId)
                    .update("status", "recusado")
                    .await()

                Toast.makeText(this@notificacao, "❌ Convite recusado", Toast.LENGTH_SHORT).show()
                carregarConvites()
            } catch (e: Exception) {
                Toast.makeText(this@notificacao, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
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
                R.id.nav_groups -> {
                    startActivity(Intent(this, produtos::class.java))
                    finish()
                    true
                }
                R.id.nav_notifications -> true
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