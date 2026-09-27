package com.example.plataformaremota

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ComentariosTrabalhoActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""
    private var nomeUsuario: String = "Usuario"
    private var fotoUsuario: String = ""

    private var trabalhoId: String = ""
    private var tituloTrabalho: String = "Trabalho"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comentarios_trabalho)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""
        trabalhoId = intent.getStringExtra("trabalhoId") ?: ""
        tituloTrabalho = intent.getStringExtra("tituloTrabalho") ?: "Trabalho"

        if (trabalhoId.isEmpty()) {
            Toast.makeText(this, "Trabalho nao encontrado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val btnVoltar = findViewById<Button>(R.id.btnVoltarComentarios)
        btnVoltar.setOnClickListener { finish() }

        val txtTitulo = findViewById<TextView>(R.id.txtTituloTrabalhoComentarios)
        txtTitulo.text = tituloTrabalho

        val edtNovo = findViewById<EditText>(R.id.edtNovoComentario)
        val btnEnviar = findViewById<Button>(R.id.btnEnviarComentario)

        btnEnviar.setOnClickListener {
            val texto = edtNovo.text.toString().trim()
            if (texto.isEmpty()) return@setOnClickListener
            edtNovo.text.clear()
            enviarComentario(texto)
        }

        // Carrega dados do usuario e depois a lista
        lifecycleScope.launch {
            carregarDadosUsuario()
            carregarComentarios()
        }
    }

    // ============================================================
    // CARREGAR DADOS DO USUARIO
    // ============================================================
    private suspend fun carregarDadosUsuario() {
        try {
            val doc = db.collection("usuarios").document(emailUsuario).get().await()
            nomeUsuario = doc.getString("nome") ?: "Usuario"
            fotoUsuario = doc.getString("fotoUrl") ?: ""
        } catch (e: Exception) {
            Log.e("COMENTARIOS", "Erro ao carregar usuario: ${e.message}")
        }
    }

    // ============================================================
    // CARREGAR LISTA DE COMENTARIOS
    // ============================================================
    private fun carregarComentarios() {
        val container = findViewById<LinearLayout>(R.id.containerComentarios)
        val containerVazio = findViewById<LinearLayout>(R.id.containerVazioComentarios)
        val scroll = findViewById<ScrollView>(R.id.scrollComentarios)

        container.removeAllViews()

        db.collection("trabalhos").document(trabalhoId)
            .collection("comentarios")
            .orderBy("criadoEm", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("COMENTARIOS", "Erro no listener: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshots == null) return@addSnapshotListener

                container.removeAllViews()

                if (snapshots.isEmpty) {
                    containerVazio.visibility = View.VISIBLE
                    return@addSnapshotListener
                }

                containerVazio.visibility = View.GONE

                val inflater = LayoutInflater.from(this)

                snapshots.documents.forEach { doc ->
                    val comentarioId = doc.id
                    val autorEmail = doc.getString("autorEmail") ?: ""
                    val autorNome = doc.getString("autorNome") ?: "Usuario"
                    val autorFotoUrl = doc.getString("autorFotoUrl") ?: ""
                    val texto = doc.getString("texto") ?: ""
                    val criadoEm = doc.getLong("criadoEm") ?: 0L
                    val editadoEm = doc.getLong("editadoEm")

                    val ehAutor = autorEmail == emailUsuario

                    val view = inflater.inflate(R.layout.item_comentario, container, false)

                    val cardAvatar = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardAvatarComentario)
                    val imgAvatar = view.findViewById<ImageView>(R.id.imgAvatarComentario)
                    val txtIniciais = view.findViewById<TextView>(R.id.txtIniciaisComentario)
                    val txtNome = view.findViewById<TextView>(R.id.txtNomeAutorComentario)
                    val txtData = view.findViewById<TextView>(R.id.txtDataComentario)
                    val txtEditado = view.findViewById<TextView>(R.id.txtEditado)
                    val txtTexto = view.findViewById<TextView>(R.id.txtTextoComentario)

                    txtNome.text = if (ehAutor) "Voce" else autorNome
                    txtData.text = formatarDataRelativa(criadoEm)
                    txtTexto.text = texto

                    // Indicador (editado)
                    if (editadoEm != null && editadoEm > 0) {
                        txtEditado.visibility = View.VISIBLE
                    } else {
                        txtEditado.visibility = View.GONE
                    }

                    // Avatar
                    configurarAvatar(cardAvatar, imgAvatar, txtIniciais, autorNome, autorFotoUrl)

                    // Long press: menu (editar/excluir se for autor)
                    if (ehAutor) {
                        view.setOnLongClickListener {
                            mostrarMenuComentario(comentarioId, texto)
                            true
                        }
                    }

                    container.addView(view)
                }

                // Rola para o final
                scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
            }
    }

    // ============================================================
    // CONFIGURAR AVATAR (foto ou iniciais)
    // ============================================================
    private fun configurarAvatar(
        cardAvatar: com.google.android.material.card.MaterialCardView,
        imgAvatar: ImageView,
        txtIniciais: TextView,
        nome: String,
        fotoUrl: String
    ) {
        if (fotoUrl.isNotEmpty()) {
            Glide.with(this)
                .load(fotoUrl)
                .circleCrop()
                .into(imgAvatar)
            imgAvatar.visibility = View.VISIBLE
            txtIniciais.visibility = View.GONE
        } else {
            val iniciais = nome.split(" ")
                .take(2)
                .map { it.firstOrNull()?.uppercase() ?: "" }
                .joinToString("")
                .ifEmpty { "?" }

            txtIniciais.text = iniciais
            txtIniciais.visibility = View.VISIBLE
            imgAvatar.visibility = View.GONE

            cardAvatar.setCardBackgroundColor(
                ContextCompat.getColor(this, R.color.accent)
            )
            txtIniciais.setTextColor(
                ContextCompat.getColor(this, R.color.accent_dark)
            )
        }
    }

    // ============================================================
    // ENVIAR COMENTARIO
    // ============================================================
    private fun enviarComentario(texto: String) {
        lifecycleScope.launch {
            try {
                val id = ComentarioHelper.criar(
                    trabalhoId = trabalhoId,
                    texto = texto,
                    autorEmail = emailUsuario,
                    autorNome = nomeUsuario,
                    autorFotoUrl = fotoUsuario
                )

                if (id == null) {
                    Toast.makeText(
                        this@ComentariosTrabalhoActivity,
                        "Erro ao enviar comentario",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // Notifica participantes (criador + quem ja comentou)
                ComentarioHelper.notificarParticipantes(
                    trabalhoId = trabalhoId,
                    tituloTrabalho = tituloTrabalho,
                    autorEmail = emailUsuario,
                    autorNome = nomeUsuario
                )

            } catch (e: Exception) {
                Toast.makeText(
                    this@ComentariosTrabalhoActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ============================================================
    // MENU: EDITAR / EXCLUIR
    // ============================================================
    private fun mostrarMenuComentario(comentarioId: String, textoAtual: String) {
        AlertDialog.Builder(this)
            .setTitle("Opcoes")
            .setItems(arrayOf("Editar", "Excluir")) { _, which ->
                when (which) {
                    0 -> abrirDialogEdicao(comentarioId, textoAtual)
                    1 -> confirmarExclusao(comentarioId)
                }
            }
            .show()
    }

    // ============================================================
    // EDITAR COMENTARIO
    // ============================================================
    private fun abrirDialogEdicao(comentarioId: String, textoAtual: String) {
        val edtEdit = EditText(this).apply {
            setText(textoAtual)
            setPadding(40, 30, 40, 30)
            setSelection(textoAtual.length)
        }

        AlertDialog.Builder(this)
            .setTitle("Editar comentario")
            .setView(edtEdit)
            .setPositiveButton("Salvar") { _, _ ->
                val novoTexto = edtEdit.text.toString().trim()
                if (novoTexto.isEmpty()) {
                    Toast.makeText(this, "Digite um texto", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (novoTexto == textoAtual) {
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    val ok = ComentarioHelper.editar(trabalhoId, comentarioId, novoTexto)
                    if (ok) {
                        Toast.makeText(
                            this@ComentariosTrabalhoActivity,
                            "Comentario atualizado",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this@ComentariosTrabalhoActivity,
                            "Erro ao atualizar",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    // ============================================================
    // EXCLUIR COMENTARIO
    // ============================================================
    private fun confirmarExclusao(comentarioId: String) {
        AlertDialog.Builder(this)
            .setTitle("Excluir comentario")
            .setMessage("Tem certeza que deseja excluir?")
            .setPositiveButton("Excluir") { _, _ ->
                lifecycleScope.launch {
                    val ok = ComentarioHelper.remover(trabalhoId, comentarioId)
                    if (ok) {
                        Toast.makeText(
                            this@ComentariosTrabalhoActivity,
                            "Comentario excluido",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this@ComentariosTrabalhoActivity,
                            "Erro ao excluir",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    // ============================================================
    // FORMATAR DATA RELATIVA
    // ============================================================
    private fun formatarDataRelativa(timestamp: Long): String {
        if (timestamp == 0L) return ""

        val agora = System.currentTimeMillis()
        val diff = agora - timestamp

        return when {
            diff < 60_000 -> "Agora"
            diff < 3_600_000 -> {
                val min = TimeUnit.MILLISECONDS.toMinutes(diff)
                "Ha ${min}min"
            }
            diff < 86_400_000 -> {
                val horas = TimeUnit.MILLISECONDS.toHours(diff)
                "Ha ${horas}h"
            }
            diff < 604_800_000 -> {
                val dias = TimeUnit.MILLISECONDS.toDays(diff)
                "Ha ${dias}d"
            }
            else -> {
                val formato = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
                formato.format(Date(timestamp))
            }
        }
    }
}