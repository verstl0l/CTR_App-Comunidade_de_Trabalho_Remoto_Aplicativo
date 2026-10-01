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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MensagemAdapter(
    private val emailUsuario: String,
    private val outroEmail: String,
    private val contexto: Context,
    private val callbacks: Callbacks
) : ListAdapter<ItemChat, RecyclerView.ViewHolder>(DiffCallback()) {

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

    private var listaMidias: List<Pair<String, String>> = emptyList()

    override fun submitList(list: List<ItemChat>?) {
        listaMidias = list?.mapNotNull { item ->
            if (item is ItemChat.MensagemItem) {
                val msg = item.mensagem
                when (msg.tipo) {
                    "foto" -> msg.fotoUrl?.takeIf { it.isNotEmpty() }?.let { "foto" to it }
                    "video" -> msg.videoUrl?.takeIf { it.isNotEmpty() }?.let { "video" to it }
                    else -> null
                }
            } else null
        } ?: emptyList()

        super.submitList(list)
    }

    companion object {
        private const val TIPO_TEXTO = 0
        private const val TIPO_FOTO = 1
        private const val TIPO_VIDEO = 2
        private const val TIPO_ARQUIVO = 3
        private const val TIPO_SEPARADOR = 4

        private val fmtHora = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is ItemChat.SeparadorData -> TIPO_SEPARADOR
            is ItemChat.MensagemItem -> when (item.mensagem.tipo) {
                "foto" -> TIPO_FOTO
                "video" -> TIPO_VIDEO
                "arquivo" -> TIPO_ARQUIVO
                else -> TIPO_TEXTO
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TIPO_SEPARADOR -> SeparadorViewHolder(
                inflater.inflate(R.layout.item_separador_data, parent, false)
            )
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

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (item is ItemChat.SeparadorData) {
            (holder as SeparadorViewHolder).bind(item)
            return
        }
        val msg = (item as ItemChat.MensagemItem).mensagem
        val ehRem = msg.remetente == emailUsuario

        // ✅ NOVO: se a mensagem foi apagada, renderiza como texto "apagada"
        if (msg.apagada) {
            bindApagada(holder, msg, ehRem)
            return
        }

        when (holder) {
            is TextoViewHolder -> bindTexto(holder, msg, ehRem)
            is FotoViewHolder -> bindFoto(holder, msg, ehRem)
            is VideoViewHolder -> bindVideo(holder, msg, ehRem)
            is ArquivoViewHolder -> bindArquivo(holder, msg, ehRem)
        }
    }

    // ============================================================
    // ✅ NOVO: BIND APAGADA
    // ============================================================
    private fun bindApagada(holder: RecyclerView.ViewHolder, msg: Mensagem, ehRem: Boolean) {
        val containerBalao: LinearLayout
        val txtHora: TextView?

        when (holder) {
            is TextoViewHolder -> {
                containerBalao = holder.containerBalao
                txtHora = holder.txtHora
                holder.containerCitacao.visibility = View.GONE
                holder.txtTexto.text = "🚫 Mensagem apagada"
                holder.txtTexto.setTextColor(ContextCompat.getColor(contexto, R.color.text_secondary))
                holder.txtTexto.setTypeface(null, android.graphics.Typeface.ITALIC)
            }
            is FotoViewHolder -> {
                containerBalao = holder.containerBalao
                txtHora = holder.txtHora
                holder.img.visibility = View.GONE
                holder.txtNome.visibility = View.GONE
                holder.txtLegenda.visibility = View.GONE
            }
            is VideoViewHolder -> {
                containerBalao = holder.containerBalao
                txtHora = holder.txtHora
                holder.videoView.visibility = View.GONE
                holder.btnPlay.visibility = View.GONE
                holder.txtNome.visibility = View.GONE
                holder.txtLegenda.visibility = View.GONE
            }
            is ArquivoViewHolder -> {
                containerBalao = holder.containerBalao
                txtHora = holder.txtHora
                holder.txtNomeArq.text = "🚫 Mensagem apagada"
                holder.txtNomeArq.setTextColor(ContextCompat.getColor(contexto, R.color.text_secondary))
                holder.txtTam.visibility = View.GONE
                holder.txtNomeRem.visibility = View.GONE
            }
            else -> return
        }

        aplicarAlinhamento(containerBalao, ehRem)

        // Balão cinza
        containerBalao.setBackgroundResource(R.drawable.bg_bolha_recebida)
        txtHora?.text = fmtHora.format(Date(msg.timestamp))

        // Remove listeners (não pode responder, favoritar, etc)
        containerBalao.setOnClickListener(null)
        containerBalao.setOnLongClickListener(null)
    }

    private fun SeparadorViewHolder.bind(item: ItemChat.SeparadorData) {
        txtSeparador.text = item.texto
    }

    private fun bindTexto(holder: TextoViewHolder, msg: Mensagem, ehRem: Boolean) {
        aplicarAlinhamento(holder.containerBalao, ehRem)

        // Reset do typeface (pode ter sido alterado por bindApagada)
        holder.txtTexto.setTypeface(null, android.graphics.Typeface.NORMAL)

        if (ehRem) {
            holder.containerBalao.setBackgroundResource(R.drawable.bg_bolha_enviada)
            holder.txtTexto.setTextColor(ContextCompat.getColor(contexto, R.color.accent_dark))
        } else {
            holder.containerBalao.setBackgroundResource(R.drawable.bg_bolha_recebida)
            holder.txtTexto.setTextColor(ContextCompat.getColor(contexto, R.color.text_primary))
        }

        holder.txtTexto.text = msg.texto

        holder.txtHora.text = fmtHora.format(Date(msg.timestamp))
        holder.txtHora.setTextColor(
            if (ehRem) ContextCompat.getColor(contexto, R.color.accent_dark)
            else ContextCompat.getColor(contexto, R.color.text_secondary)
        )

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

        SwipeToReplyHelper.attach(holder.containerBalao, holder.imgIndicadorResposta) {
            callbacks.onResponder(msg.id, msg.texto, msg.remetente, "texto")
        }

        holder.containerBalao.setOnLongClickListener {
            callbacks.onLongPressTexto(msg.id, msg.texto, msg.remetente, ehRem)
            true
        }
    }

    private fun bindFoto(holder: FotoViewHolder, msg: Mensagem, ehRem: Boolean) {
        holder.img.visibility = View.VISIBLE
        holder.txtNome.visibility = View.VISIBLE
        holder.txtNome.text = if (ehRem) "Você" else (msg.nomeRemetente ?: outroEmail.ifEmpty { "Usuário" })

        if (msg.texto.isNotEmpty()) {
            holder.txtLegenda.text = msg.texto
            holder.txtLegenda.visibility = View.VISIBLE
        } else {
            holder.txtLegenda.visibility = View.GONE
        }

        holder.txtHora.text = fmtHora.format(Date(msg.timestamp))

        Glide.with(contexto.applicationContext)
            .load(msg.fotoUrl)
            .into(holder.img)

        aplicarAlinhamento(holder.containerBalao, ehRem)

        SwipeToReplyHelper.attach(holder.containerBalao, holder.imgIndicadorResposta) {
            callbacks.onResponder(msg.id, "", msg.remetente, "foto")
        }

        holder.containerBalao.setOnClickListener {
            val url = msg.fotoUrl
            if (url.isNullOrEmpty()) return@setOnClickListener
            val posNaGaleria = listaMidias.indexOfFirst { it.second == url }
            if (posNaGaleria >= 0) callbacks.onFotoClick(posNaGaleria, listaMidias)
            else callbacks.onFotoClick(0, listOf("foto" to url))
        }

        holder.containerBalao.setOnLongClickListener {
            callbacks.onLongPressMidia(
                "foto", msg.fotoUrl ?: "", "", msg.mimeType ?: "",
                msg.id, ehRem, msg.texto, msg.remetente
            )
            true
        }
    }

    private fun bindVideo(holder: VideoViewHolder, msg: Mensagem, ehRem: Boolean) {
        holder.videoView.visibility = View.VISIBLE
        holder.txtNome.visibility = View.VISIBLE
        holder.txtNome.text = if (ehRem) "Você" else (msg.nomeRemetente ?: outroEmail.ifEmpty { "Usuário" })
        aplicarAlinhamento(holder.containerBalao, ehRem)

        if (msg.texto.isNotEmpty()) {
            holder.txtLegenda.text = msg.texto
            holder.txtLegenda.visibility = View.VISIBLE
        } else {
            holder.txtLegenda.visibility = View.GONE
        }

        holder.txtHora.text = fmtHora.format(Date(msg.timestamp))

        holder.btnPlay.text = "▶"
        holder.btnPlay.visibility = View.VISIBLE

        try {
            holder.videoView.setVideoURI(Uri.parse(msg.videoUrl ?: ""))
        } catch (e: Exception) {
            Log.e("MENSAGEM_ADAPTER", "Erro video: ${e.message}")
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
            } catch (e: Exception) { }
        }

        holder.videoView.setOnCompletionListener {
            holder.btnPlay.text = "▶"
            holder.btnPlay.visibility = View.VISIBLE
        }

        SwipeToReplyHelper.attach(holder.overlay, holder.containerBalao, holder.imgIndicadorResposta) {
            callbacks.onResponder(msg.id, "", msg.remetente, "video")
        }

        holder.overlay.setOnClickListener {
            val url = msg.videoUrl ?: return@setOnClickListener
            if (url.isEmpty()) return@setOnClickListener
            val pos = listaMidias.indexOfFirst { it.second == url }
            if (pos >= 0) callbacks.onVideoClick(pos, listaMidias)
            else callbacks.onVideoClick(0, listOf("video" to url))
        }

        holder.overlay.setOnLongClickListener {
            callbacks.onLongPressMidia(
                "video", msg.videoUrl ?: "", "", "",
                msg.id, ehRem, msg.texto, msg.remetente
            )
            true
        }
    }

    private fun bindArquivo(holder: ArquivoViewHolder, msg: Mensagem, ehRem: Boolean) {
        holder.txtNomeRem.visibility = View.VISIBLE
        holder.txtTam.visibility = View.VISIBLE
        holder.txtNomeRem.text = if (ehRem) "Você" else (msg.nomeRemetente ?: outroEmail.ifEmpty { "Usuário" })
        holder.txtNomeArq.text = msg.nomeArquivo ?: "arquivo"
        holder.txtNomeArq.setTextColor(ContextCompat.getColor(contexto, R.color.text_primary))
        holder.txtTam.text = formatarTamanho(msg.tamanhoArquivo)
        aplicarAlinhamento(holder.containerBalao, ehRem)

        holder.txtHora.text = fmtHora.format(Date(msg.timestamp))

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

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is VideoViewHolder) {
            try {
                if (holder.videoView.isPlaying) holder.videoView.pause()
                holder.videoView.stopPlayback()
            } catch (_: Exception) {}
        }
    }

    private fun aplicarAlinhamento(containerBalao: LinearLayout, ehRemetente: Boolean) {
        val params = containerBalao.layoutParams
        if (params is RelativeLayout.LayoutParams) {
            if (ehRemetente) {
                params.addRule(RelativeLayout.ALIGN_PARENT_END, 1)
                params.removeRule(RelativeLayout.ALIGN_PARENT_START)
            } else {
                params.addRule(RelativeLayout.ALIGN_PARENT_START, 1)
                params.removeRule(RelativeLayout.ALIGN_PARENT_END)
            }
            containerBalao.layoutParams = params
        }
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
    class SeparadorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtSeparador: TextView = view.findViewById(R.id.txtSeparadorData)
    }

    class TextoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val containerBalao: LinearLayout = view.findViewById(R.id.containerBalao)
        val imgIndicadorResposta: ImageView = view.findViewById(R.id.imgIndicadorResposta)
        val txtTexto: TextView = view.findViewById(R.id.txtTextoMensagem)
        val containerCitacao: LinearLayout = view.findViewById(R.id.containerCitacao)
        val txtNomeCitado: TextView = view.findViewById(R.id.txtNomeCitado)
        val txtTextoCitado: TextView = view.findViewById(R.id.txtTextoCitado)
        val txtHora: TextView = view.findViewById(R.id.txtHoraMensagem)
    }

    class FotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.imgMensagemFoto)
        val txtNome: TextView = view.findViewById(R.id.txtNomeFoto)
        val txtLegenda: TextView = view.findViewById(R.id.txtLegendaFoto)
        val containerBalao: LinearLayout = view.findViewById(R.id.containerBalao)
        val imgIndicadorResposta: ImageView = view.findViewById(R.id.imgIndicadorResposta)
        val txtHora: TextView = view.findViewById(R.id.txtHoraMensagemFoto)
    }

    class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtLegenda: TextView = view.findViewById(R.id.txtLegendaVideo)
        val videoView: VideoView = view.findViewById(R.id.videoMensagem)
        val btnPlay: Button = view.findViewById(R.id.btnPlayVideo)
        val txtNome: TextView = view.findViewById(R.id.txtNomeVideo)
        val containerBalao: LinearLayout = view.findViewById(R.id.containerBalao)
        val imgIndicadorResposta: ImageView = view.findViewById(R.id.imgIndicadorResposta)
        val overlay: View = view.findViewById(R.id.overlayVideo)
        val txtHora: TextView = view.findViewById(R.id.txtHoraMensagemVideo)
    }

    class ArquivoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtNomeRem: TextView = view.findViewById(R.id.txtNomeArquivoRemetente)
        val txtNomeArq: TextView = view.findViewById(R.id.txtNomeArquivo)
        val txtTam: TextView = view.findViewById(R.id.txtTamanhoArquivo)
        val containerBalao: LinearLayout = view.findViewById(R.id.containerBalao)
        val imgIndicadorResposta: ImageView = view.findViewById(R.id.imgIndicadorResposta)
        val txtHora: TextView = view.findViewById(R.id.txtHoraMensagemArquivo)
    }

    class DiffCallback : DiffUtil.ItemCallback<ItemChat>() {
        override fun areItemsTheSame(oldItem: ItemChat, newItem: ItemChat): Boolean {
            return when {
                oldItem is ItemChat.MensagemItem && newItem is ItemChat.MensagemItem ->
                    oldItem.mensagem.id == newItem.mensagem.id
                oldItem is ItemChat.SeparadorData && newItem is ItemChat.SeparadorData ->
                    oldItem.timestamp == newItem.timestamp
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: ItemChat, newItem: ItemChat): Boolean =
            oldItem == newItem
    }
}