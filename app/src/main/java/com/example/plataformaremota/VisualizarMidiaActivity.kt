package com.example.plataformaremota

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import com.bumptech.glide.Glide

class VisualizarMidiaActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_visualizar_midia)

        val tipo = intent.getStringExtra("tipo") ?: "" // "foto" ou "video"
        val url = intent.getStringExtra("url") ?: ""

        if (url.isEmpty()) {
            Toast.makeText(
                this,
                getString(R.string.visualizar_midia_nao_encontrada),
                Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }

        val imgFullscreen = findViewById<ImageView>(R.id.imgFullscreen)
        val videoFullscreen = findViewById<VideoView>(R.id.videoFullscreen)
        val btnVoltar = findViewById<Button>(R.id.btnVoltarMidia)
        val btnBaixar = findViewById<Button>(R.id.btnBaixarMidia)

        when (tipo) {
            "foto" -> {
                imgFullscreen.visibility = View.VISIBLE
                Glide.with(this).load(url).into(imgFullscreen)
            }
            "video" -> {
                videoFullscreen.visibility = View.VISIBLE
                val mediaController = MediaController(this)
                mediaController.setAnchorView(videoFullscreen)
                videoFullscreen.setMediaController(mediaController)
                videoFullscreen.setVideoURI(Uri.parse(url))
                videoFullscreen.start()
            }
        }

        btnVoltar.setOnClickListener { finish() }

        btnBaixar.setOnClickListener {
            // ✅ Nomes de arquivo com prefixo CTR (não traduzível, mas centralizado)
            val nome = if (tipo == "foto") {
                "CTR_foto_${System.currentTimeMillis()}.jpg"
            } else {
                "CTR_video_${System.currentTimeMillis()}.mp4"
            }
            val pasta = if (tipo == "foto") Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES
            baixarArquivo(url, nome, pasta)
        }
    }

    private fun baixarArquivo(url: String, nomeArquivo: String, pastaDestino: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
            request.setTitle(nomeArquivo)
            request.setDescription(getString(R.string.visualizar_midia_baixando_desc))
            request.allowScanningByMediaScanner()
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(pastaDestino, nomeArquivo)

            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            Toast.makeText(
                this,
                getString(R.string.visualizar_midia_baixando_toast),
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.visualizar_midia_erro_baixar, e.message ?: ""),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}