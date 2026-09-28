package com.example.plataformaremota

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class TypingIndicatorHelper(
    private val collection: String,
    private val documentId: String,
    private val emailUsuario: String,
    private val onStatusChanged: (nomeOutro: String, estaDigitando: Boolean) -> Unit
) {

    companion object {
        private const val TAG = "TYPING"
        private const val DEBOUNCE_MS = 1000L
        private const val TIMEOUT_MS = 3000L
        private const val UPDATE_INTERVAL_MS = 500L
    }

    private val db = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO)

    private var jobDebounce: Job? = null
    private var jobLimpar: Job? = null
    private var ultimoSave = 0L
    private var listener: ListenerRegistration? = null
    private var ativo = true

    fun iniciar() {
        try {
            listener = db.collection(collection).document(documentId)
                .addSnapshotListener { snapshot, error ->
                    if (!ativo) return@addSnapshotListener
                    if (error != null) {
                        Log.e(TAG, "Erro no listener: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

                    val mapa = snapshot.get("digitando") as? Map<*, *> ?: emptyMap<Any, Any>()
                    val agora = System.currentTimeMillis()

                    val digitando = mapa.entries
                        .filter { (email, timestamp) ->
                            email != emailUsuario &&
                                    timestamp is Long &&
                                    (agora - timestamp) < TIMEOUT_MS
                        }
                        .map { it.key as String }

                    val primeiroEmail = digitando.firstOrNull()
                    if (primeiroEmail != null) {
                        scope.launch {
                            try {
                                val userDoc = db.collection("usuarios").document(primeiroEmail).get().await()
                                val nome = userDoc.getString("nome") ?: primeiroEmail
                                if (ativo) onStatusChanged(nome, true)
                            } catch (e: Exception) {
                                if (ativo) onStatusChanged(primeiroEmail, true)
                            }
                        }
                    } else {
                        if (ativo) onStatusChanged("", false)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar listener: ${e.message}")
        }
    }

    fun onDigitou() {
        if (!ativo) return

        val agora = System.currentTimeMillis()
        if (agora - ultimoSave < UPDATE_INTERVAL_MS) return
        ultimoSave = agora

        scope.launch {
            try {
                // ✅ CORRIGIDO: set com merge em vez de update
                db.collection(collection).document(documentId)
                    .set(
                        mapOf("digitando" to mapOf(emailUsuario to agora)),
                        SetOptions.merge()
                    )
                    .await()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao salvar status: ${e.message}")
            }
        }

        jobLimpar?.cancel()
        jobLimpar = scope.launch {
            delay(DEBOUNCE_MS)
            limpar()
        }
    }

    fun limpar() {
        if (!ativo) return
        scope.launch {
            try {
                // ✅ CORRIGIDO: set com merge em vez de update
                db.collection(collection).document(documentId)
                    .set(
                        mapOf("digitando" to mapOf(emailUsuario to 0L)),
                        SetOptions.merge()
                    )
                    .await()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao limpar status: ${e.message}")
            }
        }
    }

    fun destruir() {
        ativo = false
        jobDebounce?.cancel()
        jobLimpar?.cancel()
        listener?.remove()
        listener = null
    }
}