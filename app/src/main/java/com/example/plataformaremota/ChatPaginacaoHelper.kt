package com.example.plataformaremota

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Helper de paginacao de mensagens.
 *
 * IMPORTANTE: sempre entrega a lista COMPLETA (todasMensagens) no callback,
 * nunca so o delta. O adapter substitui a lista inteira via submitList, entao
 * ele PRECISA receber o estado completo sempre.
 */
class ChatPaginacaoHelper(
    private val collection: String,
    private val documentId: String,
    private val pageSize: Int = 50,
    private val onListaAtualizada: (todas: List<Mensagem>, inseriuNoTopo: Boolean) -> Unit,
    private val onNovasMensagens: (novas: List<Mensagem>) -> Unit
) {

    companion object {
        private const val TAG = "CHAT_PAGINACAO"
    }

    private val db = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(Dispatchers.Main)

    private val todasMensagens = mutableListOf<Mensagem>()
    private val idsConhecidos = mutableSetOf<String>()

    private var menorTimestampCarregado: Long? = null
    private var temMaisAntigas: Boolean = true

    @Volatile private var carregandoMaisAntigas: Boolean = false
    @Volatile private var ativo: Boolean = true

    private var listenerNovas: ListenerRegistration? = null
    private var maiorTimestampVisto: Long = 0L

    fun iniciar() {
        scope.launch {
            try {
                val snap = db.collection(collection).document(documentId)
                    .collection("mensagens")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(pageSize.toLong())
                    .get()
                    .await()

                if (!ativo) return@launch

                val docs = snap.documents
                // Inverte pra ASC (mais antiga -> mais recente)
                val mensagens = docs.map { Mensagem.deDocumento(it) }.reversed()

                todasMensagens.clear()
                idsConhecidos.clear()
                todasMensagens.addAll(mensagens)
                mensagens.forEach { idsConhecidos.add(it.id) }

                menorTimestampCarregado = mensagens.firstOrNull()?.timestamp
                maiorTimestampVisto = mensagens.lastOrNull()?.timestamp ?: 0L
                temMaisAntigas = docs.size >= pageSize

                // ✅ Sempre lista completa. inseriuNoTopo = false (primeira carga)
                onListaAtualizada(todasMensagens.toList(), false)

                escutarNovas()

            } catch (e: Exception) {
                Log.e(TAG, "Erro primeira pagina: ${e.message}")
                if (ativo) onListaAtualizada(emptyList(), false)
            }
        }
    }

    fun carregarMaisAntigas() {
        if (carregandoMaisAntigas || !temMaisAntigas || !ativo) return
        val cursor = menorTimestampCarregado ?: return

        carregandoMaisAntigas = true
        scope.launch {
            try {
                val snap = db.collection(collection).document(documentId)
                    .collection("mensagens")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .startAfter(cursor)
                    .limit(pageSize.toLong())
                    .get()
                    .await()

                if (!ativo) return@launch

                val docs = snap.documents
                val novas = docs.map { Mensagem.deDocumento(it) }
                    .filter { it.id !in idsConhecidos }
                    .reversed()

                if (novas.isEmpty()) {
                    temMaisAntigas = false
                    return@launch
                }

                // ✅ Atualiza cursor
                menorTimestampCarregado = novas.first().timestamp
                temMaisAntigas = docs.size >= pageSize

                // Insere no INICIO da lista completa
                todasMensagens.addAll(0, novas)
                novas.forEach { idsConhecidos.add(it.id) }

                // ✅ Entrega a LISTA COMPLETA (nao so as novas)
                onListaAtualizada(todasMensagens.toList(), true)

            } catch (e: Exception) {
                Log.e(TAG, "Erro mais antigas: ${e.message}")
            } finally {
                carregandoMaisAntigas = false
            }
        }
    }

    private fun escutarNovas() {
        if (!ativo) return
        try {
            val query = if (maiorTimestampVisto > 0L) {
                db.collection(collection).document(documentId)
                    .collection("mensagens")
                    .whereGreaterThan("timestamp", maiorTimestampVisto)
                    .orderBy("timestamp", Query.Direction.ASCENDING)
            } else {
                db.collection(collection).document(documentId)
                    .collection("mensagens")
                    .orderBy("timestamp", Query.Direction.ASCENDING)
                    .limit(pageSize.toLong())
            }

            listenerNovas = query.addSnapshotListener { snapshot, error ->
                if (!ativo) return@addSnapshotListener
                if (error != null) {
                    Log.e(TAG, "Erro listener: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val novas = snapshot.documents
                    .map { Mensagem.deDocumento(it) }
                    .filter { it.id !in idsConhecidos }

                if (novas.isEmpty()) return@addSnapshotListener

                novas.forEach { idsConhecidos.add(it.id) }
                todasMensagens.addAll(novas)
                maiorTimestampVisto = maxOf(maiorTimestampVisto, novas.last().timestamp)

                onNovasMensagens(novas)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao registrar listener: ${e.message}")
        }
    }

    fun destruir() {
        ativo = false
        listenerNovas?.remove()
        listenerNovas = null
    }
}