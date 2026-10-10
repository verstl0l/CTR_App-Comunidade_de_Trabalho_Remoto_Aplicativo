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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.gowtham.library.utils.TrimVideo
import com.yalantis.ucrop.UCrop
import java.io.File
import java.util.Locale

class PreviewMidiaActivity : BaseActivity() {

    private var uriAtual: Uri? = null
    private var uriOriginal: Uri? = null
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
            val resultUri = result.data?.let { UCrop.getOutput(it) }
            if (resultUri != null) {
                Log.d(TAG, "Crop OK: $resultUri")
                uriAtual = resultUri
                carregarFoto()
                Toast.makeText(
                    this,
                    getString(R.string.preview_midia_imagem_cortada),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Log.e(TAG, "Crop retornou URI nula")
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            val error = result.data?.let { UCrop.getError(it) }
            Log.e(TAG, "Crop erro: ${error?.message}")
            Toast.makeText(
                this,
                getString(R.string.preview_midia_erro_cortar, error?.message ?: ""),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ✅ Launcher do video-trimmer
    private val abrirVideoTrimmer = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                val trimmedPath = TrimVideo.getTrimmedVideoPath(data)
                if (!trimmedPath.isNullOrEmpty()) {
                    Log.d(TAG, "Trim OK: $trimmedPath")
                    val novaUri = Uri.parse(trimmedPath)
                    uriAtual = novaUri
                    carregarVideo()
                    Toast.makeText(
                        this,
                        getString(R.string.preview_midia_video_cortado),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Log.e(TAG, "Trim retornou path nulo")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview_midia)

        val uriString = intent.getStringExtra("uri") ?: ""
        tipo = intent.getStringExtra("tipo") ?: "foto"
        chatTipo = intent.getStringExtra("chatTipo") ?: ""
        chatId = intent.getStringExtra("chatId") ?: ""

        if (uriString.isEmpty()) {
            Toast.makeText(
                this,
                getString(R.string.preview_midia_invalida),
                Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }
        uriAtual = Uri.parse(uriString)
        uriOriginal = uriAtual

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
        btnCortar.setOnClickListener { abrirCrop() }
        btnEditarVideo.setOnClickListener { abrirTrimVideo() }
    }

    private fun configurarFoto() {
        imgPreview.visibility = View.VISIBLE
        videoPreview.visibility = View.GONE
        containerArquivo.visibility = View.GONE

        carregarFoto()

        btnCortar.visibility = View.VISIBLE
        btnCortar.isEnabled = true
        btnEditarVideo.visibility = View.GONE
    }

    private fun carregarFoto() {
        Glide.with(this).load(uriAtual).into(imgPreview)
    }

    private fun configurarVideo() {
        imgPreview.visibility = View.GONE
        videoPreview.visibility = View.VISIBLE
        containerArquivo.visibility = View.GONE

        carregarVideo()

        btnCortar.visibility = View.GONE
        btnEditarVideo.visibility = View.VISIBLE
        btnEditarVideo.isEnabled = true
    }

    private fun carregarVideo() {
        try {
            videoPreview.setVideoURI(uriAtual)
            videoPreview.setOnPreparedListener { mp ->
                mp.isLooping = true
                videoPreview.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro video: ${e.message}")
        }
    }

    private fun configurarArquivo() {
        imgPreview.visibility = View.GONE
        videoPreview.visibility = View.GONE
        containerArquivo.visibility = View.VISIBLE

        var nome = getString(R.string.preview_midia_arquivo_fallback)
        var tamanho = 0L
        var mime = "application/octet-stream"

        try {
            contentResolver.query(uriAtual!!, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni != -1) nome = c.getString(ni)
                        ?: getString(R.string.preview_midia_arquivo_fallback)
                    if (si != -1) tamanho = c.getLong(si)
                }
            }
            mime = contentResolver.getType(uriAtual!!) ?: "application/octet-stream"
        } catch (_: Exception) { }

        txtNomeArquivo.text = nome
        txtTamanhoArquivo.text = formatarTamanho(tamanho)

        // ✅ Siglas de tipo de arquivo (universais, mas centralizadas)
        txtIconeArquivo.text = when {
            mime == "application/pdf" -> getString(R.string.preview_midia_sigla_pdf)
            mime.contains("word") || mime.contains("document") -> getString(R.string.preview_midia_sigla_doc)
            mime.contains("sheet") || mime.contains("excel") -> getString(R.string.preview_midia_sigla_xls)
            mime.contains("presentation") || mime.contains("powerpoint") -> getString(R.string.preview_midia_sigla_ppt)
            mime.startsWith("image/") -> getString(R.string.preview_midia_sigla_img)
            mime.startsWith("video/") -> getString(R.string.preview_midia_sigla_vid)
            mime.startsWith("audio/") -> getString(R.string.preview_midia_sigla_aud)
            mime.contains("zip") || mime.contains("rar") -> getString(R.string.preview_midia_sigla_zip)
            else -> getString(R.string.preview_midia_sigla_arq)
        }

        btnCortar.visibility = View.GONE
        btnEditarVideo.visibility = View.GONE
    }

    private fun abrirCrop() {
        val origem = uriAtual ?: return

        val pastaCache = File(cacheDir, "crop_temp").apply { mkdirs() }
        val arquivoSaida = File(pastaCache, "crop_${System.currentTimeMillis()}.jpg")

        val destinoUri = try {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", arquivoSaida)
        } catch (e: Exception) {
            Log.e(TAG, "FileProvider erro: ${e.message}")
            Toast.makeText(
                this,
                getString(R.string.preview_midia_erro_preparar_crop),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val opcoes = UCrop.Options().apply {
            setCompressionFormat(android.graphics.Bitmap.CompressFormat.JPEG)
            setCompressionQuality(90)
            setHideBottomControls(false)
            setFreeStyleCropEnabled(true)
            // ✅ Título da toolbar do uCrop — traduzível
            setToolbarTitle(getString(R.string.preview_midia_cortar_titulo))
            setToolbarColor(ContextCompat.getColor(this@PreviewMidiaActivity, R.color.bg_primary))
            setToolbarWidgetColor(ContextCompat.getColor(this@PreviewMidiaActivity, R.color.text_primary))
            setRootViewBackgroundColor(ContextCompat.getColor(this@PreviewMidiaActivity, R.color.bg_primary))
        }

        val intent = UCrop.of(origem, destinoUri).withOptions(opcoes).getIntent(this)

        try {
            abrirUCrop.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao abrir uCrop: ${e.message}")
            Toast.makeText(
                this,
                getString(R.string.preview_midia_erro_abrir_editor),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun abrirTrimVideo() {
        val origem = uriAtual ?: return

        try {
            TrimVideo.activity(origem.toString())
                .setHideSeekBar(false)
                .start(this, abrirVideoTrimmer)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao abrir trim: ${e.message}")
            Toast.makeText(
                this,
                getString(R.string.preview_midia_erro_abrir_editor_video),
                Toast.LENGTH_LONG
            ).show()
        }
    }

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

    /**
     * ✅ Formata tamanho com locale do sistema (evita vírgula em pt-BR)
     * Usa o separador decimal do locale, mas com espaço entre número e unidade.
     */
    private fun formatarTamanho(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format(
                Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0)
            )
            else -> String.format(
                Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0)
            )
        }
    }

    companion object {
        private const val TAG = "PREVIEW_MIDIA"
    }
}