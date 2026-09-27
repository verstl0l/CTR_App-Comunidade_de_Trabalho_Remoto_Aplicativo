package com.example.plataformaremota

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Helper central para gerenciar comentarios de tarefas.
 *
 * Estrutura no Firestore:
 *   trabalhos/{trabalhoId}/comentarios/{comentarioId}
 *     - autorEmail: "ana@x.com"
 *     - autorNome: "Ana"
 *     - autorFotoUrl: "https://..." ou ""
 *     - texto: "Ja comecei a trabalhar nisso"
 *     - criadoEm: 1234567890
 *     - editadoEm: null (ou timestamp se foi editado)
 */
object ComentarioHelper {

    private const val TAG = "COMENTARIO"

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    // ============================================================
    // CRIAR
    // ============================================================

    /**
     * Cria um comentario e notifica os participantes do trabalho.
     *
     * @return ID do comentario criado, ou null em caso de erro
     */
    suspend fun criar(
        trabalhoId: String,
        texto: String,
        autorEmail: String,
        autorNome: String,
        autorFotoUrl: String = ""
    ): String? {
        return try {
            val comentario = hashMapOf(
                "autorEmail" to autorEmail,
                "autorNome" to autorNome,
                "autorFotoUrl" to autorFotoUrl,
                "texto" to texto,
                "criadoEm" to System.currentTimeMillis(),
                "editadoEm" to null
            )

            val id = db.collection("trabalhos").document(trabalhoId)
                .collection("comentarios").add(comentario).await().id

            Log.d(TAG, "Comentario criado: $id")
            id
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar comentario: ${e.message}")
            null
        }
    }

    // ============================================================
    // EDITAR
    // ============================================================

    /**
     * Atualiza o texto de um comentario e marca como editado.
     */
    suspend fun editar(
        trabalhoId: String,
        comentarioId: String,
        novoTexto: String
    ): Boolean {
        return try {
            db.collection("trabalhos").document(trabalhoId)
                .collection("comentarios").document(comentarioId)
                .update(
                    mapOf(
                        "texto" to novoTexto,
                        "editadoEm" to System.currentTimeMillis()
                    )
                ).await()
            Log.d(TAG, "Comentario editado: $comentarioId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao editar comentario: ${e.message}")
            false
        }
    }

    // ============================================================
    // REMOVER
    // ============================================================

    /**
     * Remove um comentario do Firestore.
     */
    suspend fun remover(trabalhoId: String, comentarioId: String): Boolean {
        return try {
            db.collection("trabalhos").document(trabalhoId)
                .collection("comentarios").document(comentarioId)
                .delete().await()
            Log.d(TAG, "Comentario removido: $comentarioId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao remover comentario: ${e.message}")
            false
        }
    }

    // ============================================================
    // CONTAR
    // ============================================================

    /**
     * Conta quantos comentarios o trabalho tem.
     */
    suspend fun contar(trabalhoId: String): Int {
        return try {
            db.collection("trabalhos").document(trabalhoId)
                .collection("comentarios").get().await().size()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao contar comentarios: ${e.message}")
            0
        }
    }

    // ============================================================
    // NOTIFICAR PARTICIPANTES
    // ============================================================

    /**
     * Notifica os participantes do trabalho quando um novo comentario e criado.
     *
     * Participantes = criador do trabalho + todos que ja comentaram.
     * O autor atual e sempre excluido da lista.
     */
    suspend fun notificarParticipantes(
        trabalhoId: String,
        tituloTrabalho: String,
        autorEmail: String,
        autorNome: String
    ) {
        try {
            // 1. Criador do trabalho
            val trabalhoDoc = db.collection("trabalhos").document(trabalhoId).get().await()
            val criadorEmail = trabalhoDoc.getString("criadorEmail") ?: ""

            // 2. Todos que ja comentaram
            val comentarios = db.collection("trabalhos").document(trabalhoId)
                .collection("comentarios").get().await()

            val emailsComentaristas = comentarios.documents
                .mapNotNull { it.getString("autorEmail") }

            // 3. Junta, remove duplicatas e o autor atual
            val destinatarios = (listOf(criadorEmail) + emailsComentaristas)
                .filter { it.isNotEmpty() && it != autorEmail }
                .distinct()

            // 4. Envia notificacao para cada destinatario
            destinatarios.forEach { email ->
                NotificacaoHelper.criar(
                    destinatario = email,
                    tipo = "novo_comentario",
                    titulo = "Novo comentario",
                    mensagem = "$autorNome comentou em $tituloTrabalho",
                    referenciaId = trabalhoId,
                    referenciaTipo = "trabalho",
                    remetente = autorEmail,
                    nomeRemetente = autorNome
                )
            }

            Log.d(TAG, "Notificados ${destinatarios.size} participantes")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao notificar participantes: ${e.message}")
        }
    }
}