package com.example.plataformaremota

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Helper central para gravar histórico de equipes.
 *
 * Regra de "participou": um trabalho entra no histórico do usuário se
 * ele for RESPONSÁVEL ou CRIADOR do trabalho.
 *
 * Se entrar, copiamos:
 *  - Todos os comentários (de todos os envolvidos)
 *  - Todos os anexos (de todos os envolvidos)
 *
 * Se o usuário foi APENAS MEMBRO (sem trabalhos), o histórico registra
 * a equipe mas sem subcoleção de trabalhos.
 *
 * Datas são gravadas como Timestamp do Firestore (formatado no Console).
 */
object HistoricoEquipeHelper {

    private const val TAG = "HISTORICO_EQUIPE"
    private const val LIMITE_BATCH = 450

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    /**
     * Grava o histórico de uma equipe pra um usuário específico.
     *
     * @param emailUsuario Email do usuário que vai receber o histórico
     * @param equipeId ID da equipe
     * @param motivoSaida "saiu" | "removido" | "equipe_excluida"
     */
    suspend fun registrarSaida(
        emailUsuario: String,
        equipeId: String,
        motivoSaida: String
    ) {
        if (emailUsuario.isEmpty() || equipeId.isEmpty()) {
            Log.w(TAG, "Email ou equipeId vazio, ignorando")
            return
        }

        try {
            Log.d(TAG, "Iniciando registro: $emailUsuario / $equipeId / $motivoSaida")

            // 1. Lê dados da equipe
            val equipeDoc = db.collection("equipes").document(equipeId).get().await()
            if (!equipeDoc.exists()) {
                Log.w(TAG, "Equipe não existe mais, ignorando")
                return
            }

            val nomeEquipe = equipeDoc.getString("nome") ?: "Equipe"
            val descricaoEquipe = equipeDoc.getString("descricao") ?: ""
            val criadorEmail = equipeDoc.getString("criadorEmail") ?: ""

            // 2. Lê dados do membro (se existir)
            val membroId = "${emailUsuario}_${equipeId}"
            val membroDoc = db.collection("membros_equipe").document(membroId).get().await()

            val funcao = membroDoc.getString("funcao") ?: "membro"
            val entrouEmMillis = membroDoc.getLong("entrouEm") ?: 0L

            val papel = when {
                criadorEmail == emailUsuario -> "dono"
                funcao == "administrador" -> "admin"
                else -> "membro"
            }

            // 3. Monta o doc do histórico
            // ✅ Usa Timestamp do Firestore para ficar legível no Console
            val agora = Timestamp.now()
            val entrouEmTimestamp = if (entrouEmMillis > 0) {
                Timestamp(entrouEmMillis / 1000, 0)
            } else {
                agora
            }

            val historicoDoc = hashMapOf(
                "equipeId" to equipeId,
                "nomeEquipe" to nomeEquipe,
                "descricaoEquipe" to descricaoEquipe,
                "papel" to papel,
                "entrouEm" to entrouEmTimestamp,
                "saiuEm" to agora,
                "motivoSaida" to motivoSaida
            )

            db.collection("historico_equipes")
                .document(emailUsuario)
                .collection("equipes")
                .document(equipeId)
                .set(historicoDoc, SetOptions.merge())
                .await()

            Log.d(TAG, "Doc principal gravado")

            // 4. Copia os trabalhos onde o usuário participou
            copiarTrabalhos(emailUsuario, equipeId)

            Log.d(TAG, "Histórico completo gravado")

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao registrar saída: ${e.message}", e)
        }
    }

    /**
     * Copia SÓ os trabalhos onde o usuário é responsável ou criador.
     * Se não houver nenhum, não cria subcoleção (histórico só de equipe).
     */
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

            // ✅ Filtra só os trabalhos do usuário
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

                // ✅ Também converte campos de data do trabalho, se houver
                val trabalhoData = hashMapOf<String, Any>(
                    "trabalhoId" to trabalhoId,
                    "titulo" to (trabalhoDoc.getString("titulo") ?: ""),
                    "descricao" to (trabalhoDoc.getString("descricao") ?: ""),
                    "status" to (trabalhoDoc.getString("status") ?: "pendente"),
                    "prazo" to (trabalhoDoc.getString("prazo") ?: ""),
                    "categoria" to (trabalhoDoc.getString("categoria") ?: "")
                )

                // Se o trabalho tem criadoEm (Long), converte pra Timestamp
                val criadoEm = trabalhoDoc.getLong("criadoEm") ?: 0L
                if (criadoEm > 0) {
                    trabalhoData["criadoEm"] = Timestamp(criadoEm / 1000, 0)
                }

                refHistorico.document(trabalhoId).set(trabalhoData).await()

                // Copia TODOS os comentários e anexos desse trabalho
                copiarSubcolecao(emailUsuario, equipeId, trabalhoId, trabalhoDoc.reference, "comentarios")
                copiarSubcolecao(emailUsuario, equipeId, trabalhoId, trabalhoDoc.reference, "anexos")
            }

            Log.d(TAG, "Trabalhos copiados")

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao copiar trabalhos: ${e.message}", e)
        }
    }

    /**
     * Copia uma subcoleção (comentarios OU anexos) do trabalho original
     * pra o trabalho no histórico.
     *
     * Também converte campos de data (Long → Timestamp) automaticamente.
     */
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

            // Divide em batches
            snap.documents.chunked(LIMITE_BATCH).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { doc ->
                    val dadosOriginais = doc.data ?: return@forEach

                    // ✅ Converte campos de data comuns (Long → Timestamp)
                    val dadosConvertidos = converterDatas(dadosOriginais)

                    batch.set(refDestino.document(doc.id), dadosConvertidos)
                }
                batch.commit().await()
            }

            Log.d(TAG, "  $subcolecao copiados: ${snap.size()}")

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao copiar $subcolecao: ${e.message}")
        }
    }

    /**
     * Converte campos de data comuns (que estão como Long em milissegundos)
     * para Timestamp do Firestore, pra ficar legível no Console.
     *
     * Campos convertidos: criadoEm, atualizadoEm, enviadoEm, timestamp
     */
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

    /**
     * Registra a exclusão de uma equipe pra TODOS os membros ativos.
     * Deve ser chamado ANTES de deletar a equipe.
     */
    suspend fun registrarExclusaoParaTodos(equipeId: String) {
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
}