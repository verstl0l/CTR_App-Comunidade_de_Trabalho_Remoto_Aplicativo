package com.example.plataformaremota

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.cloudinary.android.MediaManager
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Helper central para gerenciar anexos de tarefas.
 *
 * Estrutura no Firestore:
 *   trabalhos/{trabalhoId}/anexos/{anexoId}
 *     - nome: "briefing.pdf"
 *     - url: "https://res.cloudinary.com/..."
 *     - tipo: "pdf" | "imagem" | "video" | "outro"
 *     - mimeType: "application/pdf"
 *     - tamanho: 1234567
 *     - enviadoPor: "ana@x.com"
 *     - nomeEnviadoPor: "Ana"
 *     - enviadoEm: 1234567890
 */
object AnexoHelper {

    private const val TAG = "ANEXO"

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    // ============================================================
    // UPLOAD
    // ============================================================

    fun uploadAnexo(
        context: Context,
        trabalhoId: String,
        uri: Uri,
        emailUsuario: String,
        nomeUsuario: String,
        onProgress: (Double) -> Unit = {},
        onSucesso: (String) -> Unit,
        onErro: (String) -> Unit
    ) {
        var nomeArquivo = "arquivo"
        var tamanhoArquivo = 0L
        var mimeType = "application/octet-stream"

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) nomeArquivo = cursor.getString(nameIndex) ?: "arquivo"
                    if (sizeIndex != -1) tamanhoArquivo = cursor.getLong(sizeIndex)
                }
            }
            mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao ler metadados: ${e.message}")
        }

        val nomeFinal = nomeArquivo
        val tamanhoFinal = tamanhoArquivo
        val mimeFinal = mimeType
        val tipo = detectarTipo(mimeFinal)

        val resourceType = when (tipo) {
            "imagem" -> "image"
            "video" -> "video"
            else -> "raw"
        }

        MediaManager.get().upload(uri)
            .unsigned("fqb729sb")
            .option("resource_type", resourceType)
            .option("folder", "anexos_trabalhos/")
            .callback(object : com.cloudinary.android.callback.UploadCallback {
                override fun onStart(requestId: String?) {
                    Log.d(TAG, "Iniciando upload de $nomeFinal")
                }

                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {
                    if (totalBytes > 0) {
                        onProgress(bytes.toDouble() / totalBytes.toDouble())
                    }
                }

                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val url = resultData?.get("secure_url") as? String
                    if (url == null) {
                        onErro("URL nao retornada pelo Cloudinary")
                        return
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val anexo = hashMapOf(
                                "nome" to nomeFinal,
                                "url" to url,
                                "tipo" to tipo,
                                "mimeType" to mimeFinal,
                                "tamanho" to tamanhoFinal,
                                "enviadoPor" to emailUsuario,
                                "nomeEnviadoPor" to nomeUsuario,
                                "enviadoEm" to System.currentTimeMillis()
                            )

                            val anexoId = db.collection("trabalhos").document(trabalhoId)
                                .collection("anexos").add(anexo).await().id

                            Log.d(TAG, "Anexo criado: $anexoId")

                            withContext(Dispatchers.Main) {
                                onSucesso(anexoId)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Erro ao salvar anexo: ${e.message}")
                            withContext(Dispatchers.Main) {
                                onErro("Erro ao salvar: ${e.message}")
                            }
                        }
                    }
                }

                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    Log.e(TAG, "Erro no upload: ${error?.description}")
                    onErro(error?.description ?: "Erro desconhecido no upload")
                }

                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    Log.w(TAG, "Upload reagendado: ${error?.description}")
                }
            })
            .dispatch()
    }

    // ============================================================
    // REMOVER
    // ============================================================

    suspend fun removerAnexo(trabalhoId: String, anexoId: String): Boolean {
        return try {
            db.collection("trabalhos").document(trabalhoId)
                .collection("anexos").document(anexoId)
                .delete().await()
            Log.d(TAG, "Anexo removido: $anexoId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao remover anexo: ${e.message}")
            false
        }
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private fun detectarTipo(mimeType: String): String {
        return when {
            mimeType.startsWith("image/") -> "imagem"
            mimeType.startsWith("video/") -> "video"
            mimeType == "application/pdf" -> "pdf"
            mimeType.contains("word") || mimeType.contains("document") -> "documento"
            mimeType.contains("sheet") || mimeType.contains("excel") -> "planilha"
            mimeType.contains("presentation") || mimeType.contains("powerpoint") -> "apresentacao"
            mimeType.startsWith("text/") -> "texto"
            mimeType.startsWith("audio/") -> "audio"
            mimeType.contains("zip") || mimeType.contains("rar") -> "compactado"
            else -> "outro"
        }
    }

    fun formatarTamanho(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}