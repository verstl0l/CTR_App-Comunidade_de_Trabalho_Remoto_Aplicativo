package com.example.plataformaremota

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * Tela de preview + legenda antes de enviar midia.
 *
 * Recebe via Intent:
 *   - uri: string da URI da midia
 *   - tipo: "foto" | "video" | "arquivo"
 *   - chatTipo: "pv" | "equipe" | "grupo"
 *   - chatId: ID do chat/equipe/grupo
 *
 * Retorna via setResult:
 *   - RESULT_OK + extras: "uri", "legenda", "acao" = "enviar"
 *   - RESULT_CANCELED: usuario cancelou
 */
class PreviewMidiaActivity : AppCompatActivity() {

    private var uriAtual: Uri? = null
    private var tipo: String = "foto"
    private var chatTipo: String = ""
    private var chatId: String = ""

    private lateinit var imgPreview: ImageView
    private lateinit var videoPreview: VideoView
    private lateinit var containerArquivo: LinearLayout
    private lateinit var txtIconeArquivo: TextView
    private lateinit var txtNomeArquivo: TextView
    private lateinit var txtTamanhoArquivo: TextView
    private lateinit var edtLegenda: EditText
    private lateinit var btnEnviar: FloatingActionButton
    private lateinit var btnVoltar: ImageButton
    private lateinit var btnCortar: ImageButton
    private lateinit var btnEditarVideo: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview_midia)

        //  LOGS DE DEBUG
        Log.d("PREVIEW_MIDIA", "=== onCreate ===")
        Log.d("PREVIEW_MIDIA", "uri = '${intent.getStringExtra("uri")}'")
        Log.d("PREVIEW_MIDIA", "tipo = '${intent.getStringExtra("tipo")}'")
        Log.d("PREVIEW_MIDIA", "chatTipo = '${intent.getStringExtra("chatTipo")}'")
        Log.d("PREVIEW_MIDIA", "chatId = '${intent.getStringExtra("chatId")}'")

        val uriString = intent.getStringExtra("uri") ?: ""
        tipo = intent.getStringExtra("tipo") ?: "foto"
        chatTipo = intent.getStringExtra("chatTipo") ?: ""
        chatId = intent.getStringExtra("chatId") ?: ""

        if (uriString.isEmpty()) {
            Toast.makeText(this, "Mídia inválida", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        uriAtual = Uri.parse(uriString)

        imgPreview = findViewById(R.id.imgPreview)
        videoPreview = findViewById(R.id.videoPreview)
        containerArquivo = findViewById(R.id.containerArquivo)
        txtIconeArquivo = findViewById(R.id.txtIconeArquivo)
        txtNomeArquivo = findViewById(R.id.txtNomeArquivo)
        txtTamanhoArquivo = findViewById(R.id.txtTamanhoArquivo)
        edtLegenda = findViewById(R.id.edtLegenda)
        btnEnviar = findViewById(R.id.btnEnviar)
        btnVoltar = findViewById(R.id.btnVoltar)
        btnCortar = findViewById(R.id.btnCortar)
        btnEditarVideo = findViewById(R.id.btnEditarVideo)

        when (tipo) {
            "foto" -> configurarFoto()
            "video" -> configurarVideo()
            "arquivo" -> configurarArquivo()
        }

        btnVoltar.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        btnEnviar.setOnClickListener { confirmarEnvio() }

        // Crop/trim ficam desabilitados (Fase 8.2 e 8.3)
        btnCortar.isEnabled = false
        btnEditarVideo.isEnabled = false
    }

    // ============================================================
    // CONFIGURAR FOTO
    // ============================================================
    private fun configurarFoto() {
        imgPreview.visibility = View.VISIBLE
        videoPreview.visibility = View.GONE
        containerArquivo.visibility = View.GONE

        Glide.with(this)
            .load(uriAtual)
            .into(imgPreview)

        btnCortar.visibility = View.VISIBLE
        btnEditarVideo.visibility = View.GONE
    }

    // ============================================================
    // CONFIGURAR VÍDEO
    // ============================================================
    private fun configurarVideo() {
        imgPreview.visibility = View.GONE
        videoPreview.visibility = View.VISIBLE
        containerArquivo.visibility = View.GONE

        try {
            videoPreview.setVideoURI(uriAtual)
            videoPreview.setOnPreparedListener { mp ->
                mp.isLooping = true
                videoPreview.start()
            }
        } catch (e: Exception) {
            Log.e("PREVIEW_MIDIA", "Erro video: ${e.message}")
        }

        btnCortar.visibility = View.GONE
        btnEditarVideo.visibility = View.VISIBLE
    }

    // ============================================================
    // CONFIGURAR ARQUIVO
    // ============================================================
    private fun configurarArquivo() {
        imgPreview.visibility = View.GONE
        videoPreview.visibility = View.GONE
        containerArquivo.visibility = View.VISIBLE

        var nome = "arquivo"
        var tamanho = 0L
        var mime = "application/octet-stream"

        try {
            contentResolver.query(uriAtual!!, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni != -1) nome = c.getString(ni) ?: "arquivo"
                    if (si != -1) tamanho = c.getLong(si)
                }
            }
            mime = contentResolver.getType(uriAtual!!) ?: "application/octet-stream"
        } catch (_: Exception) { }

        txtNomeArquivo.text = nome
        txtTamanhoArquivo.text = formatarTamanho(tamanho)

        txtIconeArquivo.text = when {
            mime == "application/pdf" -> "PDF"
            mime.contains("word") || mime.contains("document") -> "DOC"
            mime.contains("sheet") || mime.contains("excel") -> "XLS"
            mime.contains("presentation") || mime.contains("powerpoint") -> "PPT"
            mime.startsWith("image/") -> "IMG"
            mime.startsWith("video/") -> "VID"
            mime.startsWith("audio/") -> "AUD"
            mime.contains("zip") || mime.contains("rar") -> "ZIP"
            else -> "ARQ"
        }

        btnCortar.visibility = View.GONE
        btnEditarVideo.visibility = View.GONE
    }

    // ============================================================
    // CONFIRMAR ENVIO
    // ============================================================
    private fun confirmarEnvio() {
        val legenda = edtLegenda.text.toString().trim()

        val resultado = Intent().apply {
            putExtra("acao", "enviar")
            putExtra("uri", uriAtual.toString())
            putExtra("tipo", tipo)
            putExtra("legenda", legenda)
        }
        setResult(Activity.RESULT_OK, resultado)
        finish()
    }

    // ============================================================
    // HELPERS
    // ============================================================
    private fun formatarTamanho(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}