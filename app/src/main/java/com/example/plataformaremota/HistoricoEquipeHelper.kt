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

/**
 * Helper central para gravar histórico de equipes.
 *
 * Regras:
 *  - AO ENTRAR: cria/atualiza doc com status="ativo"
 *  - AO SAIR: atualiza status="inativo" + copia trabalhos (acumula)
 *  - Se reentrar: volta status="ativo" e MANTÉM os trabalhos antigos
 *
 * Doc:
 *  historico_equipes/{email}/equipes/{equipeId}
 *    - equipeId, nomeEquipe, descricaoEquipe
 *    - papel: "dono" | "admin" | "membro"
 *    - entrouEm: Timestamp
 *    - saiuEm: Timestamp | null
 *    - motivoSaida: string | null
 *    - status: "ativo" | "inativo"
 *
 * ⚠️ Usa um CoroutineScope próprio (SupervisorJob + IO) para que as
 * operações NÃO sejam canceladas quando a Activity que chamou for finalizada.
 */
object HistoricoEquipeHelper {

    private const val TAG = "HISTORICO_EQUIPE"
    private const val LIMITE_BATCH = 450

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    /**
     * ✅ Scope próprio — NÃO é cancelado quando a Activity fecha.
     * Roda em IO (background). SupervisorJob garante que um erro numa
     * coroutine não cancela as outras.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ============================================================
    // ENTRADA (Async — fire-and-forget)
    // ============================================================
    /**
     * Chamado quando o usuário entra numa equipe (criou, aceitou convite, foi adicionado).
     * Cria ou atualiza o doc do histórico com status="ativo".
     *
     * ⚠️ NÃO limpa os trabalhos antigos (a regra é ACUMULAR).
     */
    fun registrarEntradaAsync(emailUsuario: String, equipeId: String) {
        scope.launch {
            registrarEntrada(emailUsuario, equipeId)
        }
    }

    // ============================================================
    // SAÍDA (Async — fire-and-forget)
    // ============================================================
    /**
     * Chamado quando o usuário sai/é removido, ou a equipe é excluída.
     * Atualiza status="inativo" + copia trabalhos (acumula).
     */
    fun registrarSaidaAsync(emailUsuario: String, equipeId: String, motivoSaida: String) {
        scope.launch {
            registrarSaida(emailUsuario, equipeId, motivoSaida)
        }
    }

    // ============================================================
    // EXCLUSÃO EM MASSA (Async — fire-and-forget)
    // ============================================================
    /**
     * Registra a exclusão de uma equipe pra TODOS os membros ativos.
     * Deve ser chamado ANTES de deletar a equipe.
     */
    fun registrarExclusaoParaTodosAsync(equipeId: String) {
        scope.launch {
            registrarExclusaoParaTodos(equipeId)
        }
    }

    // ============================================================
    // ENTRADA (suspend — pra chamadas internas)
    // ============================================================
    private suspend fun registrarEntrada(emailUsuario: String, equipeId: String) {
        if (emailUsuario.isEmpty() || equipeId.isEmpty()) {
            Log.w(TAG, "Email ou equipeId vazio, ignorando entrada")
            return
        }

        try {
            Log.d(TAG, "registrarEntrada: $emailUsuario / $equipeId")

            val equipeDoc = db.collection("equipes").document(equipeId).get().await()
            if (!equipeDoc.exists()) {
                Log.w(TAG, "Equipe não existe, ignorando")
                return
            }

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

            Log.d(TAG, "Doc de entrada gravado (status=ativo)")

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao registrar entrada: ${e.message}", e)
        }
    }

    // ============================================================
    // SAÍDA (suspend — pra chamadas internas)
    // ============================================================
    private suspend fun registrarSaida(emailUsuario: String, equipeId: String, motivoSaida: String) {
        if (emailUsuario.isEmpty() || equipeId.isEmpty()) {
            Log.w(TAG, "Email ou equipeId vazio, ignorando saída")
            return
        }

        try {
            Log.d(TAG, "registrarSaida: $emailUsuario / $equipeId / $motivoSaida")

            val equipeDoc = db.collection("equipes").document(equipeId).get().await()
            if (!equipeDoc.exists()) {
                Log.w(TAG, "Equipe não existe mais, ignorando")
                return
            }

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
            val entrouEmTimestamp = if (entrouEmMillis > 0) {
                Timestamp(entrouEmMillis / 1000, 0)
            } else {
                agora
            }

            // ✅ Mantém os trabalhos antigos: NÃO apaga nada, só atualiza o doc pai
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

            Log.d(TAG, "Doc de saída gravado (status=inativo)")

            // Copia trabalhos (não apaga os antigos — usa set por trabalhoId)
            copiarTrabalhos(emailUsuario, equipeId)

            Log.d(TAG, "Histórico completo gravado")

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao registrar saída: ${e.message}", e)
        }
    }

    // ============================================================
    // EXCLUSÃO EM MASSA (suspend — pra chamadas internas)
    // ============================================================
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

            Log.d(TAG, "Gravando histórico de exclusão para ${todos.size} membros")

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

    // ============================================================
    // COPIAR TRABALHOS (interno)
    // ============================================================
    private suspend fun copiarTrabalhos(emailUsuario: String, equipeId: String) {
        try {
            val trabalhosSnap = db.collection("trabalhos")
                .whereEqualTo("equipeId", equipeId)
                .get()
                .await()

            if (trabalhosSnap.isEmpty) {
                Log.d(TAG, "Sem trabalhos na equipe")
                return
            }

            val trabalhosDoUsuario = trabalhosSnap.documents.filter { doc ->
                val responsavel = doc.getString("responsavelEmail") ?: ""
                val criador = doc.getString("criadorEmail") ?: ""
                responsavel == emailUsuario || criador == emailUsuario
            }

            if (trabalhosDoUsuario.isEmpty()) {
                Log.d(TAG, "Usuário foi apenas membro, sem trabalhos")
                return
            }

            Log.d(TAG, "Copiando ${trabalhosDoUsuario.size} trabalhos (de ${trabalhosSnap.size()} totais)")

            val refHistorico = db.collection("historico_equipes")
                .document(emailUsuario)
                .collection("equipes")
                .document(equipeId)
                .collection("trabalhos")

            for (trabalhoDoc in trabalhosDoUsuario) {
                val trabalhoId = trabalhoDoc.id

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

                copiarSubcolecao(emailUsuario, equipeId, trabalhoId, trabalhoDoc.reference, "comentarios")
                copiarSubcolecao(emailUsuario, equipeId, trabalhoId, trabalhoDoc.reference, "anexos")
            }

            Log.d(TAG, "Trabalhos copiados")

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

            snap.documents.chunked(LIMITE_BATCH).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { doc ->
                    val dados = doc.data ?: return@forEach
                    val convertidos = converterDatas(dados)
                    batch.set(refDestino.document(doc.id), convertidos)
                }
                batch.commit().await()
            }

            Log.d(TAG, "  $subcolecao copiados: ${snap.size()}")

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
}