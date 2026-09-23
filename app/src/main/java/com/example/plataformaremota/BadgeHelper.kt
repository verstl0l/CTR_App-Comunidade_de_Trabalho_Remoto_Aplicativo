package com.example.plataformaremota

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.tasks.await

object BadgeHelper {

    suspend fun atualizarBadgeChat(context: Context, bottomNav: BottomNavigationView) {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val emailUsuario = auth.currentUser?.email ?: ""

        var conversasComNaoLidas = 0  // ✅ Agora conta CONVERSAS

        try {
            // 1. Chats PV com mensagens não lidas
            val chats = db.collection("chats")
                .whereArrayContains("participantes", emailUsuario)
                .get()
                .await()

            for (chat in chats.documents) {
                val mensagens = chat.reference.collection("mensagens")
                    .whereEqualTo("lida", false)
                    .get()
                    .await()

                // ✅ Se TEM pelo menos 1 mensagem não lida do outro → conta a CONVERSA
                val temNaoLida = mensagens.documents.any { msg ->
                    val remetente = msg.getString("remetente") ?: ""
                    remetente != emailUsuario
                }

                if (temNaoLida) {
                    conversasComNaoLidas++
                }
            }

            // 2. Grupos com mensagens não lidas
            val grupos = db.collection("grupos")
                .whereArrayContains("membros", emailUsuario)
                .get()
                .await()

            for (grupo in grupos.documents) {
                val mensagens = grupo.reference.collection("mensagens")
                    .whereEqualTo("lida", false)
                    .get()
                    .await()

                val temNaoLida = mensagens.documents.any { msg ->
                    val remetente = msg.getString("remetente") ?: ""
                    remetente != emailUsuario
                }

                if (temNaoLida) {
                    conversasComNaoLidas++
                }
            }

            // 3. Atualiza o badge do menu
            val menuItem = bottomNav.menu.findItem(R.id.nav_chat)
            if (conversasComNaoLidas > 0) {
                val badge = bottomNav.getOrCreateBadge(menuItem.itemId)
                badge.number = conversasComNaoLidas
                badge.backgroundColor = android.graphics.Color.parseColor("#F5E6D0")
                badge.badgeTextColor = android.graphics.Color.parseColor("#1C1311")
                badge.isVisible = true
            } else {
                bottomNav.removeBadge(menuItem.itemId)
            }

        } catch (e: Exception) {
            // Silencioso
        }
    }
}