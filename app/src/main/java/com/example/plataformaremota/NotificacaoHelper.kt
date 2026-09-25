package com.example.plataformaremota

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Helper central para criacao de notificacoes in-app.
 *
 * Todas as notificacoes vao para a colecao `notificacoes` no Firestore.
 * O destinatario as ve na tela NotificacoesActivity.
 */
object NotificacaoHelper {

    private const val TAG = "NOTIFICACAO"

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    // ============================================================
    // FUNCAO GENERICA
    // ============================================================

    suspend fun criar(
        destinatario: String,
        tipo: String,
        titulo: String,
        mensagem: String,
        referenciaId: String = "",
        referenciaTipo: String = "",
        remetente: String = "",
        nomeRemetente: String = ""
    ) {
        if (destinatario.isEmpty()) {
            Log.w(TAG, "Destinatario vazio, ignorando notificacao")
            return
        }

        try {
            val notificacao = hashMapOf(
                "destinatario" to destinatario,
                "tipo" to tipo,
                "titulo" to titulo,
                "mensagem" to mensagem,
                "referenciaId" to referenciaId,
                "referenciaTipo" to referenciaTipo,
                "remetente" to remetente,
                "nomeRemetente" to nomeRemetente,
                "lida" to false,
                "criadoEm" to System.currentTimeMillis()
            )

            db.collection("notificacoes").add(notificacao).await()
            Log.d(TAG, "Notificacao criada: $tipo para $destinatario")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar notificacao: ${e.message}")
        }
    }

    // ============================================================
    // METODOS ESPECIFICOS
    // ============================================================

    suspend fun notificarConviteEquipe(
        destinatario: String,
        remetente: String,
        nomeRemetente: String,
        equipeId: String,
        nomeEquipe: String
    ) {
        criar(
            destinatario = destinatario,
            tipo = "convite_equipe",
            titulo = "Novo convite de equipe",
            mensagem = "$nomeRemetente te convidou para $nomeEquipe",
            referenciaId = equipeId,
            referenciaTipo = "equipe",
            remetente = remetente,
            nomeRemetente = nomeRemetente
        )
    }

    suspend fun notificarNovoTrabalhoParaMembros(
        emailsMembros: List<String>,
        remetente: String,
        nomeRemetente: String,
        trabalhoId: String,
        tituloTrabalho: String,
        nomeEquipe: String
    ) {
        emailsMembros.forEach { email ->
            criar(
                destinatario = email,
                tipo = "novo_trabalho",
                titulo = "Novo trabalho: $tituloTrabalho",
                mensagem = "$nomeRemetente publicou em $nomeEquipe",
                referenciaId = trabalhoId,
                referenciaTipo = "trabalho",
                remetente = remetente,
                nomeRemetente = nomeRemetente
            )
        }
    }

    suspend fun notificarConviteTrabalho(
        destinatario: String,
        remetente: String,
        nomeRemetente: String,
        trabalhoId: String,
        tituloTrabalho: String,
        nomeEquipe: String
    ) {
        criar(
            destinatario = destinatario,
            tipo = "convite_trabalho",
            titulo = "Convite para trabalho",
            mensagem = "$nomeRemetente te convidou para $tituloTrabalho em $nomeEquipe",
            referenciaId = trabalhoId,
            referenciaTipo = "trabalho",
            remetente = remetente,
            nomeRemetente = nomeRemetente
        )
    }

    suspend fun notificarPedidoAceito(
        destinatario: String,
        remetente: String,
        nomeRemetente: String,
        equipeId: String,
        nomeEquipe: String
    ) {
        criar(
            destinatario = destinatario,
            tipo = "pedido_aceito",
            titulo = "Pedido aceito",
            mensagem = "Voce entrou na equipe $nomeEquipe",
            referenciaId = equipeId,
            referenciaTipo = "equipe",
            remetente = remetente,
            nomeRemetente = nomeRemetente
        )
    }

    suspend fun notificarMembroAdicionado(
        destinatario: String,
        remetente: String,
        nomeRemetente: String,
        grupoId: String,
        nomeGrupo: String
    ) {
        criar(
            destinatario = destinatario,
            tipo = "membro_adicionado",
            titulo = "Adicionado a um grupo",
            mensagem = "$nomeRemetente te adicionou ao grupo $nomeGrupo",
            referenciaId = grupoId,
            referenciaTipo = "grupo",
            remetente = remetente,
            nomeRemetente = nomeRemetente
        )
    }

    suspend fun notificarComentario(
        destinatario: String,
        remetente: String,
        nomeRemetente: String,
        trabalhoId: String,
        tituloTrabalho: String
    ) {
        criar(
            destinatario = destinatario,
            tipo = "novo_comentario",
            titulo = "Novo comentario",
            mensagem = "$nomeRemetente comentou em $tituloTrabalho",
            referenciaId = trabalhoId,
            referenciaTipo = "trabalho",
            remetente = remetente,
            nomeRemetente = nomeRemetente
        )
    }

    // ============================================================
    // HELPERS DE ESTADO
    // ============================================================

    suspend fun marcarComoLida(notificacaoId: String) {
        try {
            db.collection("notificacoes").document(notificacaoId)
                .update("lida", true).await()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao marcar como lida: ${e.message}")
        }
    }

    suspend fun marcarTodasComoLidas(emailUsuario: String) {
        try {
            val naoLidas = db.collection("notificacoes")
                .whereEqualTo("destinatario", emailUsuario)
                .whereEqualTo("lida", false)
                .get()
                .await()

            if (naoLidas.isEmpty) return

            val batch = db.batch()
            naoLidas.documents.forEach { doc ->
                batch.update(doc.reference, "lida", true)
            }
            batch.commit().await()

            Log.d(TAG, "${naoLidas.size()} notificacoes marcadas como lidas")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao marcar todas como lidas: ${e.message}")
        }
    }

    suspend fun contarNaoLidas(emailUsuario: String): Int {
        return try {
            val naoLidas = db.collection("notificacoes")
                .whereEqualTo("destinatario", emailUsuario)
                .whereEqualTo("lida", false)
                .get()
                .await()
            naoLidas.size()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao contar nao lidas: ${e.message}")
            0
        }
    }
}