package com.example.plataformaremota

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.tasks.await

/**
 * Helper central para atualizar os badges do BottomNavigationView.
 *
 * Hoje conta:
 *   - Chat: numero de conversas com mensagens nao lidas
 *   - Notificacoes: numero de notificacoes nao lidas
 *
 * O badge e aplicado apenas se o valor for maior que zero.
 */
object BadgeHelper {

    private val COR_BADGE = android.graphics.Color.parseColor("#F5E6D0")
    private val COR_TEXTO_BADGE = android.graphics.Color.parseColor("#1C1311")

    /**
     * Atualiza TODOS os badges do bottom nav (chat + notificacoes).
     * Use esta funcao nas telas que tem o bottom nav.
     */
    suspend fun atualizarTodosBadges(context: Context, bottomNav: BottomNavigationView) {
        atualizarBadgeChat(context, bottomNav)
        atualizarBadgeNotificacoes(context, bottomNav)
    }

    // ============================================================
    // BADGE DE CHAT
    // ============================================================

    /**
     * Conta quantas conversas tem mensagens nao lidas.
     *
     * Considera:
     *   - Chats privados (colecao `chats`)
     *   - Grupos (colecao `grupos`)
     *
     * Nao considera chat de equipe (evita contar em toda aba).
     */
    suspend fun atualizarBadgeChat(context: Context, bottomNav: BottomNavigationView) {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val emailUsuario = auth.currentUser?.email ?: ""

        if (emailUsuario.isEmpty()) return

        var conversasComNaoLidas = 0

        try {
            // 1. Chats privados
            val chats = db.collection("chats")
                .whereArrayContains("participantes", emailUsuario)
                .get()
                .await()

            for (chat in chats.documents) {
                val mensagens = chat.reference.collection("mensagens")
                    .whereEqualTo("lida", false)
                    .get()
                    .await()

                val temNaoLida = mensagens.documents.any { msg ->
                    val remetente = msg.getString("remetente") ?: ""
                    remetente != emailUsuario
                }

                if (temNaoLida) conversasComNaoLidas++
            }

            // 2. Grupos
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

                if (temNaoLida) conversasComNaoLidas++
            }

            // 3. Atualiza o badge do menu chat
            aplicarBadge(
                bottomNav = bottomNav,
                menuItemId = R.id.nav_chat,
                quantidade = conversasComNaoLidas
            )

        } catch (e: Exception) {
            // Silencioso para nao quebrar a UI
        }
    }

    // ============================================================
    // BADGE DE NOTIFICACOES
    // ============================================================

    /**
     * Conta quantas notificacoes nao lidas o usuario tem.
     * Faz uma unica query na colecao `notificacoes`.
     */
    suspend fun atualizarBadgeNotificacoes(context: Context, bottomNav: BottomNavigationView) {
        val auth = FirebaseAuth.getInstance()
        val emailUsuario = auth.currentUser?.email ?: ""

        if (emailUsuario.isEmpty()) return

        try {
            val naoLidas = NotificacaoHelper.contarNaoLidas(emailUsuario)

            aplicarBadge(
                bottomNav = bottomNav,
                menuItemId = R.id.nav_notifications,
                quantidade = naoLidas
            )
        } catch (e: Exception) {
            // Silencioso
        }
    }

    // ============================================================
    // APLICAR BADGE
    // ============================================================

    /**
     * Aplica ou remove o badge de um item do menu.
     *
     * @param bottomNav BottomNavigationView alvo
     * @param menuItemId ID do item (ex: R.id.nav_chat)
     * @param quantidade numero a exibir. Se <= 0, remove o badge.
     */
    private fun aplicarBadge(
        bottomNav: BottomNavigationView,
        menuItemId: Int,
        quantidade: Int
    ) {
        val menuItem = bottomNav.menu.findItem(menuItemId) ?: return

        if (quantidade > 0) {
            val badge = bottomNav.getOrCreateBadge(menuItem.itemId)
            badge.number = quantidade
            badge.backgroundColor = COR_BADGE
            badge.badgeTextColor = COR_TEXTO_BADGE
            badge.isVisible = true
        } else {
            bottomNav.removeBadge(menuItem.itemId)
        }
    }
}