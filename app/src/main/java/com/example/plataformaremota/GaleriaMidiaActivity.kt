package com.example.plataformaremota

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.github.chrisbanes.photoview.PhotoView

class GaleriaMidiaActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var btnVoltar: Button
    private lateinit var btnBaixar: Button

    private var listaMidias: List<MidiaItem> = emptyList()
    private var posicaoAtual: Int = 0

    private val opcoesGlide: RequestOptions by lazy {
        RequestOptions()
            .override(1080, 1920)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .centerInside()
            .format(com.bumptech.glide.load.DecodeFormat.PREFER_RGB_565)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_galeria_midia)

        val urlsArray = intent.getStringArrayExtra("urls") ?: emptyArray()
        val tiposArray = intent.getStringArrayExtra("tipos") ?: emptyArray()
        posicaoAtual = intent.getIntExtra("posicaoInicial", 0)

        if (urlsArray.isEmpty() || urlsArray.size != tiposArray.size) {
            Toast.makeText(this, "Nenhuma mídia para mostrar", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        listaMidias = urlsArray.mapIndexed { index, url ->
            MidiaItem(tipo = tiposArray[index], url = url)
        }

        viewPager = findViewById(R.id.viewPagerMidia)
        btnVoltar = findViewById(R.id.btnVoltarGaleria)
        btnBaixar = findViewById(R.id.btnBaixarGaleria)

        viewPager.offscreenPageLimit = 1

        val adapter = GaleriaAdapter(listaMidias)
        viewPager.adapter = adapter
        viewPager.setCurrentItem(posicaoAtual, false)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                posicaoAtual = position
            }
        })

        btnVoltar.setOnClickListener { finish() }

        btnBaixar.setOnClickListener {
            val midia = listaMidias[posicaoAtual]
            baixarMidia(midia.url, midia.tipo)
        }
    }

    private fun baixarMidia(url: String, tipo: String) {
        try {
            val nome = if (tipo == "foto") {
                "CTR_foto_${System.currentTimeMillis()}.jpg"
            } else {
                "CTR_video_${System.currentTimeMillis()}.mp4"
            }
            val pasta = if (tipo == "foto") Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES

            val request = DownloadManager.Request(Uri.parse(url))
            request.setTitle(nome)
            request.setDescription("Baixando do CTR...")
            request.allowScanningByMediaScanner()
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(pasta, nome)

            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            Toast.makeText(this, "⬇ Baixando...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    data class MidiaItem(
        val tipo: String,
        val url: String
    )

    // ========== ADAPTER ==========
    inner class GaleriaAdapter(private val midias: List<MidiaItem>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int {
            return if (midias[position].tipo == "video") 1 else 0
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 1) {
                val view = inflater.inflate(R.layout.item_galeria_video, parent, false)
                VideoViewHolder(view)
            } else {
                val view = inflater.inflate(R.layout.item_galeria_foto, parent, false)
                FotoViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = midias[position]

            when (holder) {
                is FotoViewHolder -> {
                    Glide.with(this@GaleriaMidiaActivity)
                        .load(item.url)
                        .apply(opcoesGlide)
                        .into(holder.photoView)
                }
                is VideoViewHolder -> {
                    // ✅ Configura o vídeo
                    holder.videoView.setVideoURI(Uri.parse(item.url))

                    // ✅ Botão central: play/pause
                    holder.btnPlayCentral.setOnClickListener {
                        if (holder.videoView.isPlaying) {
                            holder.videoView.pause()
                            holder.btnPlayCentral.visibility = View.VISIBLE
                        } else {
                            holder.videoView.start()
                            holder.btnPlayCentral.visibility = View.GONE
                        }
                    }

                    // ✅ Tap na tela também dá play/pause
                    holder.videoView.setOnClickListener {
                        if (holder.videoView.isPlaying) {
                            holder.videoView.pause()
                            holder.btnPlayCentral.visibility = View.VISIBLE
                        } else {
                            holder.videoView.start()
                            holder.btnPlayCentral.visibility = View.GONE
                        }
                    }

                    // ✅ Quando o vídeo terminar, mostra o botão de novo
                    holder.videoView.setOnCompletionListener {
                        holder.btnPlayCentral.visibility = View.VISIBLE
                    }

                    // ✅ Auto-play quando o vídeo estiver pronto
                    holder.videoView.setOnPreparedListener {
                        holder.videoView.start()
                        holder.btnPlayCentral.visibility = View.GONE
                    }
                }
            }
        }

        override fun getItemCount(): Int = midias.size

        inner class FotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val photoView: PhotoView = view.findViewById(R.id.photoViewGaleria)
        }

        inner class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val videoView: VideoView = view.findViewById(R.id.videoViewGaleria)
            val btnPlayCentral: ImageView = view.findViewById(R.id.btnPlayCentral)
        }
    }
}