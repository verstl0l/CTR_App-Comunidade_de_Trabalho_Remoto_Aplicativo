package com.example.plataformaremota

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

/**
 * Adapter do RecyclerView para mensagens de chat.
 * Suporta 4 tipos: texto, foto, vídeo, arquivo.
 *
 * Usa ListAdapter + DiffUtil para atualizações eficientes.
 * Chama de volta a Activity via interface Callbacks.
 */
class MensagemAdapter(
    private val emailUsuario: String,
    private val outroEmail: String,
    private val contexto: Context,
    private val callbacks: Callbacks
) : ListAdapter<Mensagem, RecyclerView.ViewHolder>(DiffCallback()) {

    // ============================================================
    // CALLBACKS (a Activity implementa)
    // ============================================================
    interface Callbacks {
        fun onResponder(msgId: String, texto: String, remetente: String, tipo: String)
        fun onFotoClick(posicaoNaGaleria: Int, listaMidias: List<Pair<String, String>>)
        fun onVideoClick(posicaoNaGaleria: Int, listaMidias: List<Pair<String, String>>)
        fun onLongPressTexto(msgId: String, texto: String, remetente: String, ehRem: Boolean)
        fun onLongPressMidia(
            tipo: String, url: String, nomeArq: String, mime: String,
            msgId: String, ehRem: Boolean, texto: String, remetente: String
        )
        fun onAbrirArquivo(url: String, mime: String, nome: String)
    }

    // ============================================================
    // LISTA DE MÍDIAS (reconstruída a cada submitList)
    // ============================================================
    private var listaMidias: List<Pair<String, String>> = emptyList()

    override fun submitList(list: List<Mensagem>?) {
        // Reconstrói a lista de mídias (foto/vídeo) para a galeria
        listaMidias = list?.mapNotNull { msg ->
            when (msg.tipo) {
                "foto" -> msg.fotoUrl?.takeIf { it.isNotEmpty() }?.let { "foto" to it }
                "video" -> msg.videoUrl?.takeIf { it.isNotEmpty() }?.let { "video" to it }
                else -> null
            }
        } ?: emptyList()
        super.submitList(list)
    }

    // ============================================================
    // VIEW TYPES
    // ============================================================
    companion object {
        private const val TIPO_TEXTO = 0
        private const val TIPO_FOTO = 1
        private const val TIPO_VIDEO = 2
        private const val TIPO_ARQUIVO = 3
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position).tipo) {
            "foto" -> TIPO_FOTO
            "video" -> TIPO_VIDEO
            "arquivo" -> TIPO_ARQUIVO
            else -> TIPO_TEXTO
        }
    }

    // ============================================================
    // ON CREATE VIEW HOLDER
    // ============================================================
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TIPO_FOTO -> FotoViewHolder(
                inflater.inflate(R.layout.item_mensagem_foto, parent, false)
            )
            TIPO_VIDEO -> VideoViewHolder(
                inflater.inflate(R.layout.item_mensagem_video, parent, false)
            )
            TIPO_ARQUIVO -> ArquivoViewHolder(
                inflater.inflate(R.layout.item_mensagem_arquivo, parent, false)
            )
            else -> TextoViewHolder(
                inflater.inflate(R.layout.item_mensagem_texto, parent, false)
            )
        }
    }

    // ============================================================
    // ON BIND VIEW HOLDER
    // ============================================================
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = getItem(position)
        val ehRem = msg.remetente == emailUsuario

        when (holder) {
            is TextoViewHolder -> bindTexto(holder, msg, ehRem)
            is FotoViewHolder -> bindFoto(holder, msg, ehRem)
            is VideoViewHolder -> bindVideo(holder, msg, ehRem)
            is ArquivoViewHolder -> bindArquivo(holder, msg, ehRem)
        }
    }

    // ============================================================
    // BIND: TEXTO
    // ============================================================
    private fun bindTexto(holder: TextoViewHolder, msg: Mensagem, ehRem: Boolean) {
        aplicarAlinhamento(holder.containerBalao, ehRem)

        if (ehRem) {
            holder.containerBalao.setBackgroundResource(R.drawable.bg_bolha_enviada)
            holder.txtTexto.setTextColor(ContextCompat.getColor(contexto, R.color.accent_dark))
        } else {
            holder.containerBalao.setBackgroundResource(R.drawable.bg_bolha_recebida)
            holder.txtTexto.setTextColor(ContextCompat.getColor(contexto, R.color.text_primary))
        }

        holder.txtTexto.text = msg.texto

        // Citação (resposta)
        val rp = msg.respostaPara
        if (rp != null) {
            holder.containerCitacao.visibility = View.VISIBLE
            holder.txtNomeCitado.text = rp.nomeRemetente
            holder.txtTextoCitado.text = when (rp.tipo) {
                "foto" -> "📷 Foto"
                "video" -> "🎥 Vídeo"
                "arquivo" -> "📎 Arquivo"
                else -> rp.texto
            }
        } else {
            holder.containerCitacao.visibility = View.GONE
        }

        // Swipe to reply
        SwipeToReplyHelper.attach(holder.containerBalao, holder.imgIndicadorResposta) {
            callbacks.onResponder(msg.id, msg.texto, msg.remetente, "texto")
        }

        // Long press
        holder.containerBalao.setOnLongClickListener {
            callbacks.onLongPressTexto(msg.id, msg.texto, msg.remetente, ehRem)
            true
        }
    }

    // ============================================================
    // BIND: FOTO
    // ============================================================
    private fun bindFoto(holder: FotoViewHolder, msg: Mensagem, ehRem: Boolean) {
        holder.txtNome.text = if (ehRem) "Você" else (msg.nomeRemetente ?: outroEmail.ifEmpty { "Usuário" })

        if (msg.texto.isNotEmpty()) {
            holder.txtLegenda.text = msg.texto
            holder.txtLegenda.visibility = View.VISIBLE
        } else {
            holder.txtLegenda.visibility = View.GONE
        }

        Glide.with(contexto.applicationContext)
            .load(msg.fotoUrl)
            .into(holder.img)

        aplicarAlinhamento(holder.containerBalao, ehRem)

        SwipeToReplyHelper.attach(holder.containerBalao, holder.imgIndicadorResposta) {
            callbacks.onResponder(msg.id, "", msg.remetente, "foto")
        }

        // ✅ CORRIGIDO: se URL vazia mostra Toast, se não acha na lista abre só essa foto
        holder.containerBalao.setOnClickListener {
            val url = msg.fotoUrl
            if (url.isNullOrEmpty()) {
                Toast.makeText(contexto, "Foto sem URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val posNaGaleria = listaMidias.indexOfFirst { it.second == url }

            if (posNaGaleria >= 0) {
                callbacks.onFotoClick(posNaGaleria, listaMidias)
            } else {
                // Fallback: abre só essa foto
                callbacks.onFotoClick(0, listOf("foto" to url))
            }
        }

        holder.containerBalao.setOnLongClickListener {
            callbacks.onLongPressMidia(
                "foto", msg.fotoUrl ?: "", "", msg.mimeType ?: "",
                msg.id, ehRem, msg.texto, msg.remetente
            )
            true
        }
    }

    // ============================================================
    // BIND: VÍDEO
    // ============================================================
    private fun bindVideo(holder: VideoViewHolder, msg: Mensagem, ehRem: Boolean) {
        holder.txtNome.text = if (ehRem) "Você" else (msg.nomeRemetente ?: outroEmail.ifEmpty { "Usuário" })
        aplicarAlinhamento(holder.containerBalao, ehRem)

        if (msg.texto.isNotEmpty()) {
            holder.txtLegenda.text = msg.texto
            holder.txtLegenda.visibility = View.VISIBLE
        } else {
            holder.txtLegenda.visibility = View.GONE
        }

        // Reset do estado do vídeo
        holder.btnPlay.text = "▶"
        holder.btnPlay.visibility = View.VISIBLE

        try {
            holder.videoView.setVideoURI(Uri.parse(msg.videoUrl ?: ""))
        } catch (e: Exception) {
            Log.e("MENSAGEM_ADAPTER", "Erro video: ${e.message}")
        }

        holder.videoView.setOnErrorListener { _, _, _ ->
            holder.btnPlay.text = "⚠"
            holder.btnPlay.visibility = View.VISIBLE
            true
        }

        holder.btnPlay.setOnClickListener {
            try {
                if (holder.videoView.isPlaying) {
                    holder.videoView.pause()
                    holder.btnPlay.text = "▶"
                    holder.btnPlay.visibility = View.VISIBLE
                } else {
                    holder.videoView.start()
                    holder.btnPlay.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e("MENSAGEM_ADAPTER", "Erro play: ${e.message}")
            }
        }

        holder.videoView.setOnCompletionListener {
            holder.btnPlay.text = "▶"
            holder.btnPlay.visibility = View.VISIBLE
        }

        SwipeToReplyHelper.attach(
            viewToTouch = holder.overlay,
            containerBalao = holder.containerBalao,
            imgIndicador = holder.imgIndicadorResposta,
            onResponder = {
                callbacks.onResponder(msg.id, "", msg.remetente, "video")
            }
        )

        // ✅ CORRIGIDO: se URL vazia mostra Toast, se não acha na lista abre só esse vídeo
        holder.overlay.setOnClickListener {
            val url = msg.videoUrl
            if (url.isNullOrEmpty()) {
                Toast.makeText(contexto, "Vídeo sem URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val posNaGaleria = listaMidias.indexOfFirst { it.second == url }

            if (posNaGaleria >= 0) {
                callbacks.onVideoClick(posNaGaleria, listaMidias)
            } else {
                callbacks.onVideoClick(0, listOf("video" to url))
            }
        }

        holder.overlay.setOnLongClickListener {
            callbacks.onLongPressMidia(
                "video", msg.videoUrl ?: "", "", "",
                msg.id, ehRem, msg.texto, msg.remetente
            )
            true
        }
    }

    // ============================================================
    // BIND: ARQUIVO
    // ============================================================
    private fun bindArquivo(holder: ArquivoViewHolder, msg: Mensagem, ehRem: Boolean) {
        holder.txtNomeRem.text = if (ehRem) "Você" else (msg.nomeRemetente ?: outroEmail.ifEmpty { "Usuário" })
        holder.txtNomeArq.text = msg.nomeArquivo ?: "arquivo"
        holder.txtTam.text = formatarTamanho(msg.tamanhoArquivo)
        aplicarAlinhamento(holder.containerBalao, ehRem)

        SwipeToReplyHelper.attach(holder.containerBalao, holder.imgIndicadorResposta) {
            callbacks.onResponder(msg.id, "", msg.remetente, "arquivo")
        }

        holder.containerBalao.setOnClickListener {
            callbacks.onAbrirArquivo(
                msg.arquivoUrl ?: "",
                msg.mimeType ?: "",
                msg.nomeArquivo ?: "arquivo"
            )
        }

        holder.containerBalao.setOnLongClickListener {
            callbacks.onLongPressMidia(
                "arquivo", msg.arquivoUrl ?: "", msg.nomeArquivo ?: "",
                msg.mimeType ?: "", msg.id, ehRem, msg.texto, msg.remetente
            )
            true
        }
    }

    // ============================================================
    // ON VIEW RECYCLED (pausa vídeos ao reciclar)
    // ============================================================
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is VideoViewHolder) {
            try {
                if (holder.videoView.isPlaying) holder.videoView.pause()
                holder.videoView.stopPlayback()
            } catch (_: Exception) {}
        }
    }

    // ============================================================
    // HELPERS
    // ============================================================
    private fun aplicarAlinhamento(containerBalao: LinearLayout, ehRemetente: Boolean) {
        val params = containerBalao.layoutParams as RelativeLayout.LayoutParams
        if (ehRemetente) {
            params.addRule(RelativeLayout.ALIGN_PARENT_END, 1)
            params.removeRule(RelativeLayout.ALIGN_PARENT_START)
        } else {
            params.addRule(RelativeLayout.ALIGN_PARENT_START, 1)
            params.removeRule(RelativeLayout.ALIGN_PARENT_END)
        }
        containerBalao.layoutParams = params
    }

    private fun formatarTamanho(b: Long): String = when {
        b < 1024 -> "$b B"
        b < 1024 * 1024 -> "${b / 1024} KB"
        b < 1024 * 1024 * 1024 -> String.format("%.1f MB", b / (1024.0 * 1024.0))
        else -> String.format("%.1f GB", b / (1024.0 * 1024.0 * 1024.0))
    }

    // ============================================================
    // VIEW HOLDERS
    // ============================================================
    class TextoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val containerBalao: LinearLayout = view.findViewById(R.id.containerBalao)
        val imgIndicadorResposta: ImageView = view.findViewById(R.id.imgIndicadorResposta)
        val txtTexto: TextView = view.findViewById(R.id.txtTextoMensagem)
        val containerCitacao: LinearLayout = view.findViewById(R.id.containerCitacao)
        val txtNomeCitado: TextView = view.findViewById(R.id.txtNomeCitado)
        val txtTextoCitado: TextView = view.findViewById(R.id.txtTextoCitado)
    }

    class FotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.imgMensagemFoto)
        val txtNome: TextView = view.findViewById(R.id.txtNomeFoto)

        val txtLegenda: TextView = view.findViewById(R.id.txtLegendaFoto)
        val containerBalao: LinearLayout = view.findViewById(R.id.containerBalao)
        val imgIndicadorResposta: ImageView = view.findViewById(R.id.imgIndicadorResposta)
    }

    class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtLegenda: TextView = view.findViewById(R.id.txtLegendaVideo)
        val videoView: VideoView = view.findViewById(R.id.videoMensagem)
        val btnPlay: Button = view.findViewById(R.id.btnPlayVideo)
        val txtNome: TextView = view.findViewById(R.id.txtNomeVideo)
        val containerBalao: LinearLayout = view.findViewById(R.id.containerBalao)
        val imgIndicadorResposta: ImageView = view.findViewById(R.id.imgIndicadorResposta)
        val overlay: View = view.findViewById(R.id.overlayVideo)
    }

    class ArquivoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtNomeRem: TextView = view.findViewById(R.id.txtNomeArquivoRemetente)
        val txtNomeArq: TextView = view.findViewById(R.id.txtNomeArquivo)
        val txtTam: TextView = view.findViewById(R.id.txtTamanhoArquivo)
        val containerBalao: LinearLayout = view.findViewById(R.id.containerBalao)
        val imgIndicadorResposta: ImageView = view.findViewById(R.id.imgIndicadorResposta)
    }

    // ============================================================
    // DIFF CALLBACK
    // ============================================================
    class DiffCallback : DiffUtil.ItemCallback<Mensagem>() {
        override fun areItemsTheSame(oldItem: Mensagem, newItem: Mensagem): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Mensagem, newItem: Mensagem): Boolean =
            oldItem == newItem
    }
}