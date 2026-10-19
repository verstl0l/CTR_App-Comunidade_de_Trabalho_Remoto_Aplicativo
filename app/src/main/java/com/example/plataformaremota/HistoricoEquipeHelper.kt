package com.example.plataformaremota

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object HistoricoEquipeHelper {

    private const val TAG = "HISTORICO_EQUIPE"
    private const val LIMITE_BATCH = 450

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ============================================================
    // ENTRADA
    // ============================================================
    fun registrarEntradaAsync(emailUsuario: String, equipeId: String) {
        scope.launch {
            registrarEntrada(emailUsuario, equipeId)
        }
    }

    private suspend fun registrarEntrada(emailUsuario: String, equipeId: String) {
        if (emailUsuario.isEmpty() || equipeId.isEmpty()) return
        try {
            val equipeDoc = db.collection("equipes").document(equipeId).get().await()
            if (!equipeDoc.exists()) return

            val nomeEquipe = equipeDoc.getString("nome") ?: "Equipe"
            val descricaoEquipe = equipeDoc.getString("descricao") ?: ""
            val criadorEmail = equipeDoc.getString("criadorEmail") ?: ""

            val membroId = "${emailUsuario}_${equipeId}"
            val membroDoc = db.collection("membros_equipe").document(membroId).get().await()

            val funcao = membroDoc.getString("funcao") ?: "membro"
            val entrouEmMillis = membroDoc.getLong("entrouEm") ?: System.currentTimeMillis()

            val papel = when {
                criadorEmail == emailUsuario -> "dono"
                funcao == "administrador" -> "admin"
                else -> "membro"
            }

            val entrouEmTimestamp = Timestamp(entrouEmMillis / 1000, 0)

            val historicoDoc = hashMapOf<String, Any?>(
                "equipeId" to equipeId,
                "nomeEquipe" to nomeEquipe,
                "descricaoEquipe" to descricaoEquipe,
                "papel" to papel,
                "entrouEm" to entrouEmTimestamp,
                "saiuEm" to null,
                "motivoSaida" to null,
                "status" to "ativo"
            )

            db.collection("historico_equipes")
                .document(emailUsuario)
                .collection("equipes")
                .document(equipeId)
                .set(historicoDoc, SetOptions.merge())
                .await()

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao registrar entrada: ${e.message}", e)
        }
    }

    // ============================================================
    // SAÍDA
    // ============================================================
    fun registrarSaidaAsync(emailUsuario: String, equipeId: String, motivoSaida: String) {
        scope.launch {
            registrarSaida(emailUsuario, equipeId, motivoSaida)
        }
    }

    private suspend fun registrarSaida(emailUsuario: String, equipeId: String, motivoSaida: String) {
        if (emailUsuario.isEmpty() || equipeId.isEmpty()) return
        try {
            val equipeDoc = db.collection("equipes").document(equipeId).get().await()
            if (!equipeDoc.exists()) return

            val nomeEquipe = equipeDoc.getString("nome") ?: "Equipe"
            val descricaoEquipe = equipeDoc.getString("descricao") ?: ""
            val criadorEmail = equipeDoc.getString("criadorEmail") ?: ""

            val membroId = "${emailUsuario}_${equipeId}"
            val membroDoc = db.collection("membros_equipe").document(membroId).get().await()

            val funcao = membroDoc.getString("funcao") ?: "membro"
            val entrouEmMillis = membroDoc.getLong("entrouEm") ?: 0L

            val papel = when {
                criadorEmail == emailUsuario -> "dono"
                funcao == "administrador" -> "admin"
                else -> "membro"
            }

            val agora = Timestamp.now()
            val entrouEmTimestamp = if (entrouEmMillis > 0) Timestamp(entrouEmMillis / 1000, 0) else agora

            val historicoDoc = hashMapOf<String, Any?>(
                "equipeId" to equipeId,
                "nomeEquipe" to nomeEquipe,
                "descricaoEquipe" to descricaoEquipe,
                "papel" to papel,
                "entrouEm" to entrouEmTimestamp,
                "saiuEm" to agora,
                "motivoSaida" to motivoSaida,
                "status" to "inativo"
            )

            db.collection("historico_equipes")
                .document(emailUsuario)
                .collection("equipes")
                .document(equipeId)
                .set(historicoDoc, SetOptions.merge())
                .await()

            copiarTrabalhos(emailUsuario, equipeId)

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao registrar saída: ${e.message}", e)
        }
    }

    // ============================================================
    // CÓPIA DOS TRABALHOS (Com regras de privacidade)
    // ============================================================
    private suspend fun copiarTrabalhos(emailUsuario: String, equipeId: String) {
        try {
            val trabalhosSnap = db.collection("trabalhos")
                .whereEqualTo("equipeId", equipeId)
                .get()
                .await()

            if (trabalhosSnap.isEmpty) return

            // Filtra os trabalhos do usuário
            val trabalhosDoUsuario = trabalhosSnap.documents.filter { doc ->
                val responsavel = doc.getString("responsavelEmail") ?: ""
                val criador = doc.getString("criadorEmail") ?: ""
                val participante = doc.getString("participanteEmail") ?: ""
                responsavel == emailUsuario || criador == emailUsuario || participante == emailUsuario
            }

            if (trabalhosDoUsuario.isEmpty()) return

            val refHistorico = db.collection("historico_equipes")
                .document(emailUsuario)
                .collection("equipes")
                .document(equipeId)
                .collection("trabalhos")

            for (trabalhoDoc in trabalhosDoUsuario) {
                val trabalhoId = trabalhoDoc.id

                // Copia o doc do trabalho
                val trabalhoData = hashMapOf<String, Any>(
                    "trabalhoId" to trabalhoId,
                    "titulo" to (trabalhoDoc.getString("titulo") ?: ""),
                    "descricao" to (trabalhoDoc.getString("descricao") ?: ""),
                    "status" to (trabalhoDoc.getString("status") ?: "pendente"),
                    "prazo" to (trabalhoDoc.getString("prazo") ?: ""),
                    "categoria" to (trabalhoDoc.getString("categoria") ?: "")
                )

                val criadoEm = trabalhoDoc.getLong("criadoEm") ?: 0L
                if (criadoEm > 0) {
                    trabalhoData["criadoEm"] = Timestamp(criadoEm / 1000, 0)
                }

                refHistorico.document(trabalhoId).set(trabalhoData).await()

                // ✅ Copia Comentários (todos)
                copiarSubcolecao(emailUsuario, equipeId, trabalhoId, trabalhoDoc.reference, "comentarios")

                // ✅ Copia Anexos (filtrando os privados)
                copiarSubcolecao(emailUsuario, equipeId, trabalhoId, trabalhoDoc.reference, "anexos")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao copiar trabalhos: ${e.message}", e)
        }
    }

    private suspend fun copiarSubcolecao(
        emailUsuario: String,
        equipeId: String,
        trabalhoId: String,
        trabalhoRef: com.google.firebase.firestore.DocumentReference,
        subcolecao: String
    ) {
        try {
            val snap = trabalhoRef.collection(subcolecao).get().await()
            if (snap.isEmpty) return

            val refDestino = db.collection("historico_equipes")
                .document(emailUsuario)
                .collection("equipes")
                .document(equipeId)
                .collection("trabalhos")
                .document(trabalhoId)
                .collection(subcolecao)

            // ✅ AQUI É A CORREÇÃO: Se for anexo, filtra os privados de terceiros
            val docsFiltrados = if (subcolecao == "anexos") {
                snap.documents.filter { doc ->
                    val isPrivado = doc.getBoolean("privado") ?: false
                    val enviadoPor = doc.getString("enviadoPor") ?: ""

                    // Regra: copia se NÃO for privado OU se quem enviou fui EU
                    !isPrivado || enviadoPor == emailUsuario
                }
            } else {
                snap.documents
            }

            if (docsFiltrados.isEmpty()) return

            docsFiltrados.chunked(LIMITE_BATCH).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { doc ->
                    val dados = doc.data ?: return@forEach
                    val convertidos = converterDatas(dados)
                    batch.set(refDestino.document(doc.id), convertidos)
                }
                batch.commit().await()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao copiar $subcolecao: ${e.message}")
        }
    }

    private fun converterDatas(dados: Map<String, Any>): Map<String, Any> {
        val camposData = listOf("criadoEm", "atualizadoEm", "enviadoEm", "timestamp", "editadoEm")
        val resultado = dados.toMutableMap()

        camposData.forEach { campo ->
            val valor = resultado[campo]
            if (valor is Long && valor > 0) {
                resultado[campo] = Timestamp(valor / 1000, 0)
            }
        }
        return resultado
    }

    // ============================================================
    // EXCLUSÃO
    // ============================================================
    fun registrarExclusaoParaTodosAsync(equipeId: String) {
        scope.launch {
            registrarExclusaoParaTodos(equipeId)
        }
    }

    private suspend fun registrarExclusaoParaTodos(equipeId: String) {
        try {
            val membrosSnap = db.collection("membros_equipe")
                .whereEqualTo("equipeId", equipeId)
                .get()
                .await()

            val emails = membrosSnap.documents.mapNotNull { it.getString("email") }

            val equipeDoc = db.collection("equipes").document(equipeId).get().await()
            val criadorEmail = equipeDoc.getString("criadorEmail") ?: ""

            val todos = (emails + criadorEmail).distinct().filter { it.isNotEmpty() }

            todos.forEach { email ->
                registrarSaida(
                    emailUsuario = email,
                    equipeId = equipeId,
                    motivoSaida = "equipe_excluida"
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao registrar exclusão pra todos: ${e.message}", e)
        }
    }
}