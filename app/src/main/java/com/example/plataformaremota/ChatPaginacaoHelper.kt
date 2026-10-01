package com.example.plataformaremota

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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

    // ✅ NOVO: controla se a primeira carga já foi feita
    private var primeiraCargaFeita = false

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
                val mensagens = docs.map { Mensagem.deDocumento(it) }.reversed()

                todasMensagens.clear()
                idsConhecidos.clear()
                todasMensagens.addAll(mensagens)
                mensagens.forEach { idsConhecidos.add(it.id) }

                menorTimestampCarregado = mensagens.firstOrNull()?.timestamp
                maiorTimestampVisto = mensagens.lastOrNull()?.timestamp ?: 0L
                temMaisAntigas = docs.size >= pageSize

                onListaAtualizada(todasMensagens.toList(), false)

                primeiraCargaFeita = true
                escutarAlteracoes()

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

                menorTimestampCarregado = novas.first().timestamp
                temMaisAntigas = docs.size >= pageSize

                todasMensagens.addAll(0, novas)
                novas.forEach { idsConhecidos.add(it.id) }

                onListaAtualizada(todasMensagens.toList(), true)

            } catch (e: Exception) {
                Log.e(TAG, "Erro mais antigas: ${e.message}")
            } finally {
                carregandoMaisAntigas = false
            }
        }
    }

    // ============================================================
    // ✅ NOVO: listener que escuta NOVAS + UPDATES (apagadas)
    // ============================================================
    private fun escutarAlteracoes() {
        if (!ativo) return
        try {
            // Escuta TODAS as mensagens a partir do menor timestamp carregado
            // (não só as novas — assim pega updates também)
            val cursor = menorTimestampCarregado ?: 0L

            val query = db.collection(collection).document(documentId)
                .collection("mensagens")
                .whereGreaterThanOrEqualTo("timestamp", cursor)
                .orderBy("timestamp", Query.Direction.ASCENDING)

            listenerNovas = query.addSnapshotListener { snapshot, error ->
                if (!ativo) return@addSnapshotListener
                if (error != null) {
                    Log.e(TAG, "Erro listener: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val mensagensAtualizadas = snapshot.documents
                    .map { Mensagem.deDocumento(it) }

                // Encontra novas (não conhecidas)
                val novas = mensagensAtualizadas.filter { it.id !in idsConhecidos }

                // Encontra atualizadas (já conhecidas mas com dados diferentes)
                val atualizadas = mensagensAtualizadas.filter { nova ->
                    val antiga = todasMensagens.find { it.id == nova.id }
                    antiga != null && antiga != nova
                }

                if (novas.isEmpty() && atualizadas.isEmpty()) return@addSnapshotListener

                // Atualiza lista em memória
                novas.forEach { nova ->
                    todasMensagens.add(nova)
                    idsConhecidos.add(nova.id)
                    maiorTimestampVisto = maxOf(maiorTimestampVisto, nova.timestamp)
                }

                atualizadas.forEach { nova ->
                    val index = todasMensagens.indexOfFirst { it.id == nova.id }
                    if (index >= 0) {
                        todasMensagens[index] = nova
                    }
                }

                // Notifica a Activity
                if (novas.isNotEmpty()) {
                    onNovasMensagens(novas)
                }

                if (atualizadas.isNotEmpty()) {
                    // Reenvia a lista completa (isso regenera os separadores de data)
                    onListaAtualizada(todasMensagens.toList(), false)
                }
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