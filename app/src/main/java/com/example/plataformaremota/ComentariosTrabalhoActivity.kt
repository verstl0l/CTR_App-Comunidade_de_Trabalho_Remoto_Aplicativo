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

class ComentariosTrabalhoActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""
    private var nomeUsuario: String = ""
    private var fotoUsuario: String = ""

    private var trabalhoId: String = ""
    private var tituloTrabalho: String = ""
    private var equipeId: String = ""

    // Verifica se pode excluir comentarios dos outros (dono ou admin)
    private var podeApagarQualquer: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comentarios_trabalho)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""
        trabalhoId = intent.getStringExtra("trabalhoId") ?: ""
        tituloTrabalho = intent.getStringExtra("tituloTrabalho") ?: getString(R.string.comentarios_trabalho_padrao)
        equipeId = intent.getStringExtra("equipeId") ?: ""

        nomeUsuario = getString(R.string.usuario_padrao)

        if (trabalhoId.isEmpty()) {
            Toast.makeText(this, getString(R.string.comentarios_nao_encontrado), Toast.LENGTH_SHORT).show()
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

        // Carrega permissao + dados do usuario + lista
        lifecycleScope.launch {
            verificarPermissao()
            carregarDadosUsuario()
            carregarComentarios()
        }
    }

    // ============================================================
    // VERIFICA SE PODE APAGAR COMENTARIOS DOS OUTROS
    // ============================================================
    private suspend fun verificarPermissao() {
        if (equipeId.isEmpty()) return

        try {
            val equipeDoc = db.collection("equipes").document(equipeId).get().await()
            val criadorEmail = equipeDoc.getString("criadorEmail") ?: ""

            if (criadorEmail == emailUsuario) {
                podeApagarQualquer = true
                return
            }

            val membro = db.collection("membros_equipe")
                .whereEqualTo("equipeId", equipeId)
                .whereEqualTo("email", emailUsuario)
                .limit(1)
                .get()
                .await()

            if (!membro.isEmpty) {
                val funcao = membro.documents[0].getString("funcao") ?: ""
                podeApagarQualquer = funcao == "administrador"
            }
        } catch (e: Exception) {
            Log.e("COMENTARIOS", "Erro permissao: ${e.message}")
        }
    }

    // ============================================================
    // CARREGAR DADOS DO USUARIO
    // ============================================================
    private suspend fun carregarDadosUsuario() {
        try {
            val doc = db.collection("usuarios").document(emailUsuario).get().await()
            nomeUsuario = doc.getString("nome") ?: getString(R.string.usuario_padrao)
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
                    val autorNome = doc.getString("autorNome") ?: getString(R.string.usuario_padrao)
                    val autorFotoUrl = doc.getString("autorFotoUrl") ?: ""
                    val texto = doc.getString("texto") ?: ""
                    val criadoEm = doc.getLong("criadoEm") ?: 0L
                    val editadoEm = doc.getLong("editadoEm")

                    val ehAutor = autorEmail == emailUsuario
                    val podeApagar = ehAutor || podeApagarQualquer

                    val view = inflater.inflate(R.layout.item_comentario, container, false)

                    val cardAvatar = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardAvatarComentario)
                    val imgAvatar = view.findViewById<ImageView>(R.id.imgAvatarComentario)
                    val txtIniciais = view.findViewById<TextView>(R.id.txtIniciaisComentario)
                    val txtNome = view.findViewById<TextView>(R.id.txtNomeAutorComentario)
                    val txtData = view.findViewById<TextView>(R.id.txtDataComentario)
                    val txtEditado = view.findViewById<TextView>(R.id.txtEditado)
                    val txtTexto = view.findViewById<TextView>(R.id.txtTextoComentario)

                    txtNome.text = if (ehAutor) getString(R.string.comentarios_voce) else autorNome
                    txtData.text = formatarDataRelativa(criadoEm)
                    txtTexto.text = texto

                    if (editadoEm != null && editadoEm > 0) {
                        txtEditado.visibility = View.VISIBLE
                    } else {
                        txtEditado.visibility = View.GONE
                    }

                    configurarAvatar(cardAvatar, imgAvatar, txtIniciais, autorNome, autorFotoUrl)

                    if (podeApagar) {
                        view.setOnLongClickListener {
                            val opcoes = mutableListOf<String>()
                            if (ehAutor) opcoes.add(getString(R.string.comentarios_opcao_editar))
                            opcoes.add(getString(R.string.comentarios_opcao_excluir))

                            val editarStr = getString(R.string.comentarios_opcao_editar)
                            val excluirStr = getString(R.string.comentarios_opcao_excluir)

                            AlertDialog.Builder(this@ComentariosTrabalhoActivity)
                                .setTitle(getString(R.string.comentarios_opcoes_titulo))
                                .setItems(opcoes.toTypedArray()) { _, which ->
                                    when (opcoes[which]) {
                                        editarStr -> abrirDialogEdicao(comentarioId, texto)
                                        excluirStr -> confirmarExclusao(comentarioId)
                                    }
                                }
                                .show()
                            true
                        }
                    }

                    container.addView(view)
                }

                scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
            }
    }

    // ============================================================
    // CONFIGURAR AVATAR
    // ============================================================
    private fun configurarAvatar(
        cardAvatar: com.google.android.material.card.MaterialCardView,
        imgAvatar: ImageView,
        txtIniciais: TextView,
        nome: String,
        fotoUrl: String
    ) {
        if (fotoUrl.isNotEmpty()) {
            Glide.with(this).load(fotoUrl).circleCrop().into(imgAvatar)
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
                    Toast.makeText(this@ComentariosTrabalhoActivity, getString(R.string.comentarios_erro_enviar), Toast.LENGTH_SHORT).show()
                    return@launch
                }

                ComentarioHelper.notificarParticipantes(
                    trabalhoId = trabalhoId,
                    tituloTrabalho = tituloTrabalho,
                    autorEmail = emailUsuario,
                    autorNome = nomeUsuario
                )
            } catch (e: Exception) {
                Toast.makeText(this@ComentariosTrabalhoActivity, getString(R.string.erro_generico, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
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
            .setTitle(getString(R.string.comentarios_editar_titulo))
            .setView(edtEdit)
            .setPositiveButton(R.string.salvar) { _, _ ->
                val novoTexto = edtEdit.text.toString().trim()
                if (novoTexto.isEmpty()) {
                    Toast.makeText(this, getString(R.string.comentarios_digite_texto), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (novoTexto == textoAtual) return@setPositiveButton

                lifecycleScope.launch {
                    val ok = ComentarioHelper.editar(trabalhoId, comentarioId, novoTexto)
                    if (ok) {
                        Toast.makeText(this@ComentariosTrabalhoActivity, getString(R.string.comentarios_atualizado), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ComentariosTrabalhoActivity, getString(R.string.comentarios_erro_atualizar), Toast.LENGTH_SHORT).show()
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
            .setTitle(getString(R.string.comentarios_excluir_titulo))
            .setMessage(getString(R.string.comentarios_excluir_msg))
            .setPositiveButton(R.string.excluir) { _, _ ->
                lifecycleScope.launch {
                    val ok = ComentarioHelper.remover(trabalhoId, comentarioId)
                    if (ok) {
                        Toast.makeText(this@ComentariosTrabalhoActivity, getString(R.string.comentarios_excluido), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ComentariosTrabalhoActivity, getString(R.string.comentarios_erro_excluir), Toast.LENGTH_SHORT).show()
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
            diff < 60_000 -> getString(R.string.tempo_agora)
            diff < 3_600_000 -> {
                val min = TimeUnit.MILLISECONDS.toMinutes(diff)
                getString(R.string.tempo_ha_min, min)
            }
            diff < 86_400_000 -> {
                val horas = TimeUnit.MILLISECONDS.toHours(diff)
                getString(R.string.tempo_ha_horas, horas)
            }
            diff < 604_800_000 -> {
                val dias = TimeUnit.MILLISECONDS.toDays(diff)
                getString(R.string.tempo_ha_dias, dias)
            }
            else -> {
                val locale = Locale.getDefault()
                val formato = SimpleDateFormat("dd/MM/yyyy", locale)
                formato.format(Date(timestamp))
            }
        }
    }
}