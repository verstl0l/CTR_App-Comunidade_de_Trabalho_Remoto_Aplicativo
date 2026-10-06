package com.example.plataformaremota

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

object ChatResumoHelper {

    private const val TAG = "CHAT_RESUMO"
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    suspend fun atualizarResumo(
        emailUsuario: String,
        chatId: String,
        nome: String,
        ultimaMsg: String,
        timestamp: Long,
        tipo: String,
        incrementarNaoLidas: Boolean
    ) {
        try {
            val userRef = db.collection("usuarios").document(emailUsuario)

            // ✅ LÊ DO SERVIDOR, não do cache
            val userDoc = userRef.get(Source.SERVER).await()

            val resumos = (userDoc.get("chatsResumo") as? List<*>)
                ?.filterIsInstance<Map<String, Any>>()
                ?.toMutableList() ?: mutableListOf()

            // ✅ Lê o naoLidas ANTES de remover
            val naoLidasAntigo = resumos
                .find { it["chatId"] == chatId }
                ?.get("naoLidas")
                ?.let { (it as? Number)?.toLong() ?: 0L } ?: 0L

            resumos.removeAll { it["chatId"] == chatId }

            val novoNaoLidas = if (incrementarNaoLidas) naoLidasAntigo + 1 else naoLidasAntigo

            resumos.add(
                mapOf(
                    "chatId" to chatId,
                    "nome" to nome,
                    "ultimaMsg" to ultimaMsg,
                    "timestamp" to timestamp,
                    "naoLidas" to novoNaoLidas,
                    "tipo" to tipo
                )
            )

            val ordenados = resumos.sortedByDescending { (it["timestamp"] as? Long) ?: 0L }
            userRef.update("chatsResumo", ordenados).await()
            Log.d(TAG, "✅ Resumo atualizado: $emailUsuario / $chatId (naoLidas=$novoNaoLidas)")

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar resumo: ${e.message}")
        }
    }

    suspend fun zerarNaoLidas(emailUsuario: String, chatId: String) {
        try {
            val userRef = db.collection("usuarios").document(emailUsuario)
            val userDoc = userRef.get(Source.SERVER).await()

            val resumos = (userDoc.get("chatsResumo") as? List<*>)
                ?.filterIsInstance<Map<String, Any>>()
                ?.toMutableList() ?: return

            val index = resumos.indexOfFirst { it["chatId"] == chatId }
            if (index >= 0) {
                val resumo = resumos[index].toMutableMap()
                resumo["naoLidas"] = 0L
                resumos[index] = resumo
                userRef.update("chatsResumo", resumos).await()
                Log.d(TAG, "✅ naoLidas zerado: $emailUsuario / $chatId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao zerar nao lidas: ${e.message}")
        }
    }

    suspend fun removerResumo(emailUsuario: String, chatId: String) {
        try {
            val userRef = db.collection("usuarios").document(emailUsuario)
            val userDoc = userRef.get(Source.SERVER).await()

            val resumos = (userDoc.get("chatsResumo") as? List<*>)
                ?.filterIsInstance<Map<String, Any>>()
                ?.toMutableList() ?: return

            resumos.removeAll { it["chatId"] == chatId }
            userRef.update("chatsResumo", resumos).await()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao remover resumo: ${e.message}")
        }
    }
}