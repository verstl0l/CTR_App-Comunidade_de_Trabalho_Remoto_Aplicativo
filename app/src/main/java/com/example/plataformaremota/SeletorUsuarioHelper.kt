package com.example.plataformaremota

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object SeletorUsuarioHelper {

    private const val TAG = "SELETOR_USUARIO"

    /**
     * Abre um dialog com busca em tempo real de usuários.
     *
     * @param context     Contexto (Activity)
     * @param emailExcluir Email do usuário logado (não aparece na lista)
     * @param onSelecionado Callback quando um user é escolhido
     */
    fun abrir(
        context: Context,
        emailExcluir: String,
        onSelecionado: (email: String, nome: String) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()

        // ============================================================
        // LAYOUT DO DIALOG
        // ============================================================
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 16)
        }

        // Campo de busca
        val edtBusca = EditText(context).apply {
            hint = "Buscar por nome ou email..."
            setSingleLine()
            setPadding(24, 24, 24, 24)
            background = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.edittext_bg)
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.GRAY)
        }

        // RecyclerView
        val recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (context.resources.displayMetrics.heightPixels * 0.5).toInt()
            )
            setPadding(0, 16, 0, 0)
        }

        root.addView(edtBusca)
        root.addView(recycler)

        // ============================================================
        // DIALOG
        // ============================================================
        val dialog = AlertDialog.Builder(context)
            .setTitle("Selecionar usuário")
            .setView(root)
            .setNegativeButton("Cancelar", null)
            .create()

        // ============================================================
        // ADAPTER
        // ============================================================
        val adapter = UsuarioAdapter { email, nome ->
            onSelecionado(email, nome)
            dialog.dismiss()
        }
        recycler.adapter = adapter

        // ============================================================
        // CARREGA USUÁRIOS (em background)
        // ============================================================
        val todosUsuarios = mutableListOf<UsuarioItem>()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val snap = withContext(Dispatchers.IO) {
                    db.collection("usuarios").get().await()
                }

                snap.documents.forEach { doc ->
                    val email = doc.id
                    if (email == emailExcluir) return@forEach

                    val nome = doc.getString("nome") ?: email
                    val profissao = doc.getString("profissao") ?: ""
                    val fotoUrl = doc.getString("fotoUrl") ?: ""

                    todosUsuarios.add(
                        UsuarioItem(
                            email = email,
                            nome = nome,
                            profissao = profissao,
                            fotoUrl = fotoUrl
                        )
                    )
                }

                // Ordena por nome
                todosUsuarios.sortBy { it.nome.lowercase() }

                adapter.submitList(todosUsuarios)
                Log.d(TAG, "✅ ${todosUsuarios.size} usuários carregados")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao carregar usuários: ${e.message}")
            }
        }

        // ============================================================
        // BUSCA EM TEMPO REAL
        // ============================================================
        edtBusca.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val termo = s?.toString()?.trim()?.lowercase() ?: ""
                val filtrados = if (termo.isEmpty()) {
                    todosUsuarios
                } else {
                    todosUsuarios.filter {
                        it.nome.lowercase().contains(termo) ||
                                it.email.lowercase().contains(termo) ||
                                it.profissao.lowercase().contains(termo)
                    }
                }
                adapter.submitList(filtrados)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        dialog.show()
    }

    // ============================================================
    // DATA CLASS
    // ============================================================
    data class UsuarioItem(
        val email: String,
        val nome: String,
        val profissao: String,
        val fotoUrl: String
    )

    // ============================================================
    // ADAPTER
    // ============================================================
    private class UsuarioAdapter(
        val onClick: (email: String, nome: String) -> Unit
    ) : RecyclerView.Adapter<UsuarioAdapter.VH>() {

        private val lista = mutableListOf<UsuarioItem>()

        fun submitList(nova: List<UsuarioItem>) {
            lista.clear()
            lista.addAll(nova)
            notifyDataSetChanged()
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val cardAvatar: MaterialCardView = view.findViewById(R.id.cardAvatarBusca)
            val imgAvatar: ImageView = view.findViewById(R.id.imgAvatarBusca)
            val txtIniciais: TextView = view.findViewById(R.id.txtIniciaisBusca)
            val txtNome: TextView = view.findViewById(R.id.txtNomeBusca)
            val txtInfo: TextView = view.findViewById(R.id.txtInfoBusca)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_usuario_busca, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = lista[position]

            holder.txtNome.text = item.nome
            holder.txtInfo.text = if (item.profissao.isNotEmpty()) item.profissao else item.email

            // Avatar
            if (item.fotoUrl.isNotEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(item.fotoUrl)
                    .circleCrop()
                    .into(holder.imgAvatar)
                holder.imgAvatar.visibility = View.VISIBLE
                holder.txtIniciais.visibility = View.GONE
            } else {
                val iniciais = item.nome.split(" ")
                    .take(2)
                    .map { it.firstOrNull()?.uppercase() ?: "" }
                    .joinToString("")
                    .ifEmpty { "?" }
                holder.txtIniciais.text = iniciais
                holder.txtIniciais.visibility = View.VISIBLE
                holder.imgAvatar.visibility = View.GONE
            }

            holder.itemView.setOnClickListener {
                onClick(item.email, item.nome)
            }
        }

        override fun getItemCount() = lista.size
    }
}