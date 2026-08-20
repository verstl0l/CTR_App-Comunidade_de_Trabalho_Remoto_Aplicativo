package com.example.plataformaremota.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.plataformaremota.R
import com.example.plataformaremota.data.entity.Trabalho

class TrabalhoAdapter(
    private var trabalhos: List<Trabalho>
) : RecyclerView.Adapter<TrabalhoAdapter.TrabalhoViewHolder>() {

    class TrabalhoViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        val txtTitulo: TextView =
            itemView.findViewById(R.id.txtTitulo)

        val txtCategoria: TextView =
            itemView.findViewById(R.id.txtCategoria)

        val txtDescricao: TextView =
            itemView.findViewById(R.id.txtDescricao)

        val txtPrazo: TextView =
            itemView.findViewById(R.id.txtPrazo)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): TrabalhoViewHolder {

        val view = LayoutInflater.from(
            parent.context
        ).inflate(
            R.layout.item_trabalho,
            parent,
            false
        )

        return TrabalhoViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: TrabalhoViewHolder,
        position: Int
    ) {

        val trabalho = trabalhos[position]

        holder.txtTitulo.text = trabalho.titulo

        holder.txtCategoria.text =
            "Categoria: ${trabalho.categoria}"

        holder.txtDescricao.text =
            trabalho.descricao

        holder.txtPrazo.text =
            "Prazo: ${trabalho.prazo}"
    }

    override fun getItemCount(): Int {
        return trabalhos.size
    }

    fun atualizarLista(
        novaLista: List<Trabalho>
    ) {

        trabalhos = novaLista

        notifyDataSetChanged()
    }
}