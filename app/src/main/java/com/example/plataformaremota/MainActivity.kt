package com.example.plataformaremota

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

        // ✅ SEGURANÇA: verifica login ANTES de acessar UI
        auth = FirebaseAuth.getInstance()
        val prefs = getSharedPreferences("CTR_PREFS", MODE_PRIVATE)
        val logado = auth.currentUser != null && prefs.getBoolean("logado", false)

        if (!logado) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_inicial)
        db = FirebaseFirestore.getInstance()

        btnCadastrar = findViewById(R.id.button3)
        btnEntrarEquipe = findViewById(R.id.button)
        btnCriarEquipe = findViewById(R.id.button5)

        btnCadastrar.setOnClickListener {
            startActivity(Intent(this, CadastroActivity::class.java))
        }

        btnEntrarEquipe.setOnClickListener {
            startActivity(Intent(this, BuscarEquipesActivity::class.java))
        }

        btnCriarEquipe.setOnClickListener {
            startActivity(Intent(this, produtos::class.java))
        }

        // Botão de chat no canto superior direito
        val btnChatTopo = findViewById<ImageView>(R.id.btnChatTopo)
        btnChatTopo.setOnClickListener {
            startActivity(Intent(this, ListaConversasActivity::class.java))
        }

        // ✅ Bottom nav em 1 linha
        configurarBottomNavigation(R.id.nav_home)

        // ✅ MIGRAÇÃO: popula chatsIds e gruposIds para usuários antigos
        migrarDenormalizacao()

        // ✅ MIGRAÇÃO: ID determinístico em membros_equipe (Onda 0)
        migrarMembrosEquipeDeterministicos()

        // ✅ Salva token OneSignal
        salvarTokenOneSignal()
    }

    override fun onResume() {
        super.onResume()

        // ✅ SEGURANÇA: verifica login novamente
        val prefs = getSharedPreferences("CTR_PREFS", MODE_PRIVATE)
        if (auth.currentUser == null || !prefs.getBoolean("logado", false)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        atualizarBotoes()
    }

    // ============================================================
    // ✅ Migração: popula chatsIds e gruposIds para usuários antigos
    // ============================================================
    private fun migrarDenormalizacao() {
        lifecycleScope.launch {
            try {
                val email = auth.currentUser?.email ?: return@launch
                val userRef = db.collection("usuarios").document(email)
                val userDoc = userRef.get().await()

                // Já migrado? (tem o campo chatsIds)
                if (userDoc.contains("chatsIds") && userDoc.contains("gruposIds")) {
                    Log.d("MIGRACAO", "Usuário já migrado, pulando")
                    return@launch
                }

                Log.d("MIGRACAO", "Iniciando migração para $email")

                // 1. Popula chatsIds
                val chats = db.collection("chats")
                    .whereArrayContains("participantes", email)
                    .get().await()
                val chatsIds = chats.documents.map { it.id }

                // 2. Popula gruposIds
                val grupos = db.collection("grupos")
                    .whereArrayContains("membros", email)
                    .get().await()
                val gruposIds = grupos.documents.map { it.id }

                // 3. Salva de volta
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
    // ✅ ONDA 0 — Migração: membros_equipe com ID determinístico
    // ============================================================
    // Este script roda UMA VEZ e converte todos os docs de
    // membros_equipe com ID aleatório (do .add()) para o padrão
    // determinístico {email}_{equipeId}.
    //
    // Isso é necessário porque as Firestore Rules usam
    // exists(/membros_equipe/{email}_{equipeId}) para verificar
    // se o usuário é membro — e IDs aleatórios quebram essa checagem.
    //
    // ⚠️ Depois de rodar 1x em produção, comente a chamada
    //    em onCreate (linha "migrarMembrosEquipeDeterministicos()")
    // ============================================================
    private fun migrarMembrosEquipeDeterministicos() {
        lifecycleScope.launch {
            try {
                val prefs = getSharedPreferences("CTR_PREFS", MODE_PRIVATE)
                val jaMigrou = prefs.getBoolean("membros_migrados_v2", false)
                if (jaMigrou) {
                    Log.d("MIGRACAO_MEMBROS", "Já migrado neste dispositivo, pulando")
                    return@launch
                }

                val emailLogado = auth.currentUser?.email ?: return@launch
                Log.d("MIGRACAO_MEMBROS", "Iniciando migração para $emailLogado...")

                // ✅ Só busca os membros do usuário logado
                val membros = db.collection("membros_equipe")
                    .whereEqualTo("email", emailLogado)
                    .get()
                    .await()

                var migrados = 0
                var jaOk = 0
                var erros = 0

                membros.documents.forEach { doc ->
                    try {
                        val email = doc.getString("email")?.trim()?.lowercase() ?: return@forEach
                        val equipeId = doc.getString("equipeId")?.trim() ?: return@forEach
                        val idEsperado = "${email}_${equipeId}"

                        if (doc.id == idEsperado) {
                            jaOk++
                            return@forEach
                        }

                        // 1. Cria com ID determinístico
                        val dados = doc.data ?: emptyMap<String, Any>()
                        db.collection("membros_equipe").document(idEsperado)
                            .set(dados).await()

                        // 2. Deleta o antigo (é do usuário logado, então a rule permite)
                        doc.reference.delete().await()

                        migrados++
                        Log.d("MIGRACAO_MEMBROS", "✅ ${doc.id} -> $idEsperado")

                    } catch (e: Exception) {
                        erros++
                        Log.e("MIGRACAO_MEMBROS", "Erro em ${doc.id}: ${e.message}")
                    }
                }

                prefs.edit().putBoolean("membros_migrados_v2", true).apply()

                Log.d(
                    "MIGRACAO_MEMBROS",
                    "✅ Migração completa: $migrados migrados, $jaOk já OK, $erros erros"
                )
            } catch (e: Exception) {
                Log.e("MIGRACAO_MEMBROS", "Erro geral: ${e.message}")
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
                val emailAtual = auth.currentUser?.email ?: ""

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
        val prefs = getSharedPreferences("CTR_PREFS", MODE_PRIVATE)
        val logado = prefs.getBoolean("logado", false)
        val email = auth.currentUser?.email ?: ""

        lifecycleScope.launch {
            try {
                val equipe = db.collection("equipes")
                    .whereEqualTo("criadorEmail", email)
                    .limit(1)
                    .get()
                    .await()

                val temEquipe = !equipe.isEmpty

                btnCadastrar.visibility = if (logado) View.GONE else View.VISIBLE
                btnCriarEquipe.visibility = if (temEquipe) View.GONE else View.VISIBLE

            } catch (e: Exception) {
                btnCadastrar.visibility = if (logado) View.GONE else View.VISIBLE
                btnCriarEquipe.visibility = View.VISIBLE
            }
        }
    }
}