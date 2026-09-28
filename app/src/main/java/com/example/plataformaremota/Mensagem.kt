package com.example.plataformaremota

import com.google.firebase.firestore.DocumentSnapshot

/**
 * Data class que representa uma mensagem no chat.
 * Usada pelo MensagemAdapter (RecyclerView) para tipagem e DiffUtil.
 *
 * Converte um DocumentSnapshot do Firestore em um objeto tipado,
 * evitando o uso de getString()/getLong() espalhados pelo código.
 */
data class Mensagem(
    val id: String = "",
    val remetente: String = "",
    val nomeRemetente: String? = null,
    val texto: String = "",
    val tipo: String = "texto", // "texto", "foto", "video", "arquivo"
    val fotoUrl: String? = null,
    val videoUrl: String? = null,
    val arquivoUrl: String? = null,
    val nomeArquivo: String? = null,
    val tamanhoArquivo: Long = 0L,
    val mimeType: String? = null,
    val timestamp: Long = 0L,
    val lida: Boolean = false,
    val respostaPara: RespostaPara? = null
) {

    /**
     * Representa a mensagem que está sendo respondida (quote/reply).
     * Vem do campo "respostaPara" do Firestore.
     */
    data class RespostaPara(
        val msgId: String = "",
        val texto: String = "",
        val remetente: String = "",
        val nomeRemetente: String = "",
        val tipo: String = "texto"
    )

    companion object {

        /**
         * Converte um DocumentSnapshot do Firestore em uma Mensagem tipada.
         * Faz o parse de todos os campos com fallback seguro (null / valor padrão).
         */
        fun deDocumento(doc: DocumentSnapshot): Mensagem {
            // Parse do campo "respostaPara" (Map aninhado)
            val respostaMap = doc.get("respostaPara") as? Map<*, *>
            val respostaPara = respostaMap?.let {
                RespostaPara(
                    msgId = it["msgId"] as? String ?: "",
                    texto = it["texto"] as? String ?: "",
                    remetente = it["remetente"] as? String ?: "",
                    nomeRemetente = it["nomeRemetente"] as? String ?: "",
                    tipo = it["tipo"] as? String ?: "texto"
                )
            }

            return Mensagem(
                id = doc.id,
                remetente = doc.getString("remetente") ?: "",
                nomeRemetente = doc.getString("nomeRemetente"),
                texto = doc.getString("texto") ?: "",
                tipo = doc.getString("tipo") ?: "texto",
                fotoUrl = doc.getString("fotoUrl"),
                videoUrl = doc.getString("videoUrl"),
                arquivoUrl = doc.getString("arquivoUrl"),
                nomeArquivo = doc.getString("nomeArquivo"),
                tamanhoArquivo = doc.getLong("tamanhoArquivo") ?: 0L,
                mimeType = doc.getString("mimeType"),
                timestamp = doc.getLong("timestamp") ?: 0L,
                lida = doc.getBoolean("lida") ?: false,
                respostaPara = respostaPara
            )
        }
    }
}