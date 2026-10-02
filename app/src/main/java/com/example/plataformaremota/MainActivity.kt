package com.example.plataformaremota

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.onesignal.OneSignal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : BaseActivity() {

    private lateinit var btnCadastrar: Button
    private lateinit var btnEntrarEquipe: Button
    private lateinit var btnCriarEquipe: Button

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // ✅ SEGURANÇA: verifica cache local ANTES de acessar UI
        val prefs = getSharedPreferences("CTR_PREFS", Context.MODE_PRIVATE)
        val emailCache = prefs.getString("emailUsuario", "") ?: ""

        if (emailCache.isEmpty()) {
            // Sem cache: vai pro Login
            Log.d("MAIN", "Sem cache. Indo pro Login")
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // ✅ Tem cache: segue normal (mesmo que auth.currentUser seja null)
        Log.d("MAIN", "Cache OK: $emailCache. auth.currentUser: ${auth.currentUser?.email}")

        setContentView(R.layout.activity_inicial)

        btnCadastrar = findViewById(R.id.btnCadastrar)
        btnEntrarEquipe = findViewById(R.id.btnEntrarEquipe)
        btnCriarEquipe = findViewById(R.id.btnCriarEquipe)

        btnCadastrar.setOnClickListener {
            startActivity(Intent(this, CadastroActivity::class.java))
        }

        btnEntrarEquipe.setOnClickListener {
            startActivity(Intent(this, BuscarEquipesActivity::class.java))
        }

        btnCriarEquipe.setOnClickListener {
            startActivity(Intent(this, CriarEquipeActivity::class.java))
        }

        // Botão de chat no canto superior direito
        val btnChatTopo = findViewById<ImageView>(R.id.btnChatTopo)
        btnChatTopo.setOnClickListener {
            startActivity(Intent(this, ListaConversasActivity::class.java))
        }

        // Bottom nav
        configurarBottomNavigation(R.id.nav_home)

        // Migração de denormalização
        migrarDenormalizacao()

        // Salva token OneSignal
        salvarTokenOneSignal()
    }

    override fun onResume() {
        super.onResume()

        val prefs = getSharedPreferences("CTR_PREFS", Context.MODE_PRIVATE)
        val emailCache = prefs.getString("emailUsuario", "") ?: ""

        // ✅ Só vai pro Login se NÃO tem cache
        if (emailCache.isEmpty()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Atualiza botões normalmente
        atualizarBotoes()
    }

    // ============================================================
    // ✅ Migração: popula chatsIds e gruposIds para usuários antigos
    // ============================================================
    private fun migrarDenormalizacao() {
        lifecycleScope.launch {
            try {
                val email = auth.currentUser?.email ?: run {
                    val prefs = getSharedPreferences("CTR_PREFS", Context.MODE_PRIVATE)
                    prefs.getString("emailUsuario", "") ?: return@launch
                }

                val userRef = db.collection("usuarios").document(email)
                val userDoc = userRef.get().await()

                if (userDoc.contains("chatsIds") && userDoc.contains("gruposIds")) {
                    Log.d("MIGRACAO", "Usuário já migrado, pulando")
                    return@launch
                }

                Log.d("MIGRACAO", "Iniciando migração para $email")

                val chats = db.collection("chats")
                    .whereArrayContains("participantes", email)
                    .get().await()
                val chatsIds = chats.documents.map { it.id }

                val grupos = db.collection("grupos")
                    .whereArrayContains("membros", email)
                    .get().await()
                val gruposIds = grupos.documents.map { it.id }

                userRef.update(
                    mapOf(
                        "chatsIds" to chatsIds,
                        "gruposIds" to gruposIds
                    )
                ).await()

                Log.d("MIGRACAO", "✅ Migração completa: ${chatsIds.size} chats, ${gruposIds.size} grupos")

            } catch (e: Exception) {
                Log.e("MIGRACAO", "Erro: ${e.message}")
            }
        }
    }

    // ============================================================
    // ✅ Salva token OneSignal
    // ============================================================
    private fun salvarTokenOneSignal() {
        lifecycleScope.launch {
            try {
                OneSignal.Notifications.requestPermission(true)
                delay(2000)

                val subscriptionId = OneSignal.User.pushSubscription.id
                val prefs = getSharedPreferences("CTR_PREFS", Context.MODE_PRIVATE)
                val emailAtual = auth.currentUser?.email
                    ?: prefs.getString("emailUsuario", "")
                    ?: ""

                if (!subscriptionId.isNullOrEmpty() && emailAtual.isNotEmpty()) {
                    db.collection("usuarios").document(emailAtual)
                        .update("fcmToken", subscriptionId)
                        .await()
                    Log.d("ONESIGNAL", "✅ Token salvo: $subscriptionId")
                } else {
                    Log.e("ONESIGNAL", "❌ Subscription ID nulo ou usuário não logado")
                }
            } catch (e: Exception) {
                Log.e("ONESIGNAL", "❌ Erro: ${e.message}")
            }
        }
    }

    // ============================================================
    // ✅ Atualiza visibilidade dos botões
    // ============================================================
    private fun atualizarBotoes() {
        val prefs = getSharedPreferences("CTR_PREFS", Context.MODE_PRIVATE)
        val emailEfetivo = auth.currentUser?.email
            ?: prefs.getString("emailUsuario", "")
            ?: ""

        lifecycleScope.launch {
            try {
                val equipe = db.collection("equipes")
                    .whereEqualTo("criadorEmail", emailEfetivo)
                    .limit(1)
                    .get()
                    .await()

                // "CADASTRAR-SE" sempre escondido (já logado)
                btnCadastrar.visibility = View.GONE

                // "CRIAR EQUIPE" sempre visível
                btnCriarEquipe.visibility = View.VISIBLE

                // "ENTRAR EM EQUIPE" sempre visível
                btnEntrarEquipe.visibility = View.VISIBLE

            } catch (e: Exception) {
                Log.e("MAIN", "Erro atualizarBotoes: ${e.message}")
                btnCadastrar.visibility = View.GONE
                btnCriarEquipe.visibility = View.VISIBLE
                btnEntrarEquipe.visibility = View.VISIBLE
            }
        }
    }
}