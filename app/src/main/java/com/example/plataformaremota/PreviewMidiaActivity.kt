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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.yalantis.ucrop.UCrop
import java.io.File

class PreviewMidiaActivity : AppCompatActivity() {

    private var uriAtual: Uri? = null
    private var uriOriginal: Uri? = null   // ✅ guarda a original (nunca perde)
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

    // ✅ Launcher do uCrop
    private val abrirUCrop = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val resultUri = data?.let { UCrop.getOutput(it) }
            if (resultUri != null) {
                Log.d("PREVIEW_MIDIA", "Crop OK: $resultUri")
                uriAtual = resultUri
                carregarFoto()  // recarrega com a nova URI
                Toast.makeText(this, "Imagem cortada", Toast.LENGTH_SHORT).show()
            } else {
                Log.e("PREVIEW_MIDIA", "Crop retornou URI nula")
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            val error = result.data?.let { UCrop.getError(it) }
            Log.e("PREVIEW_MIDIA", "Crop erro: ${error?.message}")
            Toast.makeText(this, "Erro ao cortar: ${error?.message}", Toast.LENGTH_LONG).show()
        }
        // Se cancelou (RESULT_CANCELED), não faz nada
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview_midia)

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
        uriOriginal = uriAtual   // ✅ guarda a original

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

        // ✅ Botão de cortar (só funciona em foto)
        btnCortar.setOnClickListener { abrirCrop() }

        // Trim de vídeo ainda não implementado (Fase 8.3)
        btnEditarVideo.isEnabled = false
    }

    // ============================================================
    // CONFIGURAR FOTO
    // ============================================================
    private fun configurarFoto() {
        imgPreview.visibility = View.VISIBLE
        videoPreview.visibility = View.GONE
        containerArquivo.visibility = View.GONE

        carregarFoto()

        // ✅ Botão de cortar visível e habilitado em foto
        btnCortar.visibility = View.VISIBLE
        btnCortar.isEnabled = true
        btnEditarVideo.visibility = View.GONE
    }

    /** Carrega a imagem do uriAtual no imgPreview (reutilizado após o crop). */
    private fun carregarFoto() {
        Glide.with(this)
            .load(uriAtual)
            .into(imgPreview)
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
    // ✅ ABRIR O UCROP
    // ============================================================
    private fun abrirCrop() {
        val origem = uriAtual ?: return

        // Cria arquivo temporário pra uCrop salvar o resultado
        val pastaCache = File(cacheDir, "crop_temp").apply { mkdirs() }
        val arquivoSaida = File(pastaCache, "crop_${System.currentTimeMillis()}.jpg")

        // Pega URI via FileProvider (precisa estar declarado no Manifest)
        val destinoUri = try {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", arquivoSaida)
        } catch (e: Exception) {
            Log.e("PREVIEW_MIDIA", "FileProvider erro: ${e.message}")
            Toast.makeText(this, "Erro ao preparar crop", Toast.LENGTH_LONG).show()
            return
        }

        // Configura o uCrop
        val opcoes = UCrop.Options().apply {
            setCompressionFormat(android.graphics.Bitmap.CompressFormat.JPEG)
            setCompressionQuality(90)
            setHideBottomControls(false)
            setFreeStyleCropEnabled(true)   // ✅ crop livre
            setToolbarTitle("Cortar imagem")
            setToolbarColor(getColor(R.color.bg_primary))
            setToolbarWidgetColor(getColor(R.color.text_primary))
            setRootViewBackgroundColor(getColor(R.color.bg_primary))
        }

        val intent = UCrop.of(origem, destinoUri)
            .withOptions(opcoes)
            .getIntent(this)

        try {
            abrirUCrop.launch(intent)
        } catch (e: Exception) {
            Log.e("PREVIEW_MIDIA", "Erro ao abrir uCrop: ${e.message}")
            Toast.makeText(this, "Erro ao abrir editor", Toast.LENGTH_LONG).show()
        }
    }

    // ============================================================
    // CONFIRMAR ENVIO
    // ============================================================
    private fun confirmarEnvio() {
        val legenda = edtLegenda.text.toString().trim()

        val resultado = Intent().apply {
            putExtra("acao", "enviar")
            putExtra("uri", uriAtual.toString())   // ✅ manda a URI ATUAL (após crop, se houver)
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