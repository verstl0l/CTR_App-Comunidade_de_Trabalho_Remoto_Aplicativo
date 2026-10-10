package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class InfoEquipeActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var emailUsuario: String = ""
    private var equipeId: String = ""
    private var nomeEquipe: String = ""
    private var criadorEmail: String = ""
    private var ehDono: Boolean = false
    private var ehAdmin: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_info_equipe)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        emailUsuario = auth.currentUser?.email ?: ""
        equipeId = intent.getStringExtra("equipeId") ?: ""

        if (equipeId.isEmpty()) {
            Toast.makeText(this, getString(R.string.info_equipe_nao_encontrada), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val btnVoltar = findViewById<Button>(R.id.btnVoltarInfoEquipe)
        btnVoltar.setOnClickListener { finish() }

        val btnCriarGrupo = findViewById<Button>(R.id.btnCriarGrupoInfo)
        val btnVerGrupos = findViewById<Button>(R.id.btnVerGruposInfo)
        val btnSairEquipe = findViewById<Button>(R.id.btnSairEquipeInfo)
        val btnExcluirEquipe = findViewById<Button>(R.id.btnExcluirEquipeInfo)

        carregarInfo()

        btnCriarGrupo.setOnClickListener {
            val intent = Intent(this, GruposActivity::class.java)
            intent.putExtra("equipeId", equipeId)
            startActivity(intent)
        }

        btnVerGrupos.setOnClickListener {
            val intent = Intent(this, GruposActivity::class.java)
            intent.putExtra("equipeId", equipeId)
            startActivity(intent)
        }

        btnSairEquipe.setOnClickListener {
            confirmarSairEquipe()
        }

        btnExcluirEquipe.setOnClickListener {
            confirmarExcluirEquipe()
        }
    }

    private fun carregarInfo() {
        lifecycleScope.launch {
            try {
                val equipeDoc = db.collection("equipes").document(equipeId).get().await()
                nomeEquipe = equipeDoc.getString("nome") ?: getString(R.string.info_equipe_nome_fallback)
                criadorEmail = equipeDoc.getString("criadorEmail") ?: ""
                ehDono = criadorEmail == emailUsuario

                findViewById<TextView>(R.id.txtNomeEquipeInfo).text = nomeEquipe
                findViewById<TextView>(R.id.txtDescricaoEquipeInfo).text =
                    equipeDoc.getString("descricao") ?: getString(R.string.info_equipe_sem_descricao)

                val membro = db.collection("membros_equipe")
                    .whereEqualTo("equipeId", equipeId)
                    .whereEqualTo("email", emailUsuario)
                    .limit(1)
                    .get()
                    .await()

                ehAdmin = !membro.isEmpty &&
                        membro.documents[0].getString("funcao") == "administrador"

                val btnExcluir = findViewById<Button>(R.id.btnExcluirEquipeInfo)
                val btnSair = findViewById<Button>(R.id.btnSairEquipeInfo)
                val btnCriarGrupo = findViewById<Button>(R.id.btnCriarGrupoInfo)

                if (ehDono) {
                    btnExcluir.visibility = View.VISIBLE
                    btnSair.visibility = View.GONE
                    btnCriarGrupo.visibility = View.VISIBLE
                } else if (ehAdmin) {
                    btnExcluir.visibility = View.GONE
                    btnSair.visibility = View.VISIBLE
                    btnCriarGrupo.visibility = View.VISIBLE
                } else {
                    btnExcluir.visibility = View.GONE
                    btnSair.visibility = View.VISIBLE
                    btnCriarGrupo.visibility = View.GONE
                }

                carregarMembros()

            } catch (e: Exception) {
                Log.e(TAG, "Erro: ${e.message}")
            }
        }
    }

    private fun carregarMembros() {
        val container = findViewById<LinearLayout>(R.id.containerMembrosInfo)
        container.removeAllViews()

        lifecycleScope.launch {
            try {
                val membros = db.collection("membros_equipe")
                    .whereEqualTo("equipeId", equipeId)
                    .get()
                    .await()

                val inflater = LayoutInflater.from(this@InfoEquipeActivity)

                if (membros.isEmpty) {
                    val txtVazio = TextView(this@InfoEquipeActivity).apply {
                        text = getString(R.string.info_equipe_nenhum_membro)
                        setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                        textSize = 14f
                        setPadding(0, 16, 0, 16)
                    }
                    container.addView(txtVazio)
                    return@launch
                }

                // ✅ Captura strings traduzidas UMA VEZ
                val labelAdmin = getString(R.string.info_equipe_funcao_admin)
                val labelMembro = getString(R.string.info_equipe_funcao_membro)
                val nomeFallback = getString(R.string.info_equipe_usuario_fallback)

                membros.documents.forEach { doc ->
                    val emailMembro = doc.getString("email") ?: ""
                    val nomeMembro = doc.getString("nome") ?: nomeFallback
                    val funcao = doc.getString("funcao") ?: "membro"

                    val view = inflater.inflate(R.layout.item_membro_info, container, false)

                    val txtNome = view.findViewById<TextView>(R.id.txtNomeMembro)
                    val txtFuncao = view.findViewById<TextView>(R.id.txtFuncaoMembro)

                    txtNome.text = nomeMembro
                    txtFuncao.text = if (funcao == "administrador") labelAdmin else labelMembro

                    view.setOnClickListener {
                        mostrarOpcoesMembro(emailMembro, nomeMembro)
                    }

                    container.addView(view)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Erro membros: ${e.message}")
            }
        }
    }

    private fun mostrarOpcoesMembro(email: String, nome: String) {
        // ✅ Captura strings traduzidas ANTES do when
        val opConversar = getString(R.string.info_equipe_op_conversar_pv)
        val opVerPerfil = getString(R.string.info_equipe_op_ver_perfil)
        val opVerMeuPerfil = getString(R.string.info_equipe_op_ver_meu_perfil)

        val opcoes = mutableListOf<String>()

        if (email != emailUsuario) {
            opcoes.add(opConversar)
            opcoes.add(opVerPerfil)
        } else {
            opcoes.add(opVerMeuPerfil)
        }

        AlertDialog.Builder(this)
            .setTitle(nome)
            .setItems(opcoes.toTypedArray()) { _, which ->
                when (opcoes[which]) {
                    opConversar -> abrirChatPV(email)
                    opVerPerfil -> abrirPerfil(email)
                    opVerMeuPerfil -> startActivity(Intent(this, perfil::class.java))
                }
            }
            .show()
    }

    private fun abrirChatPV(email: String) {
        lifecycleScope.launch {
            try {
                val existente = db.collection("chats")
                    .whereArrayContains("participantes", emailUsuario)
                    .get().await()

                val chatExistente = existente.documents.find { doc ->
                    val parts = doc.get("participantes") as? List<*>
                    parts?.contains(email) == true
                }

                val chatId = if (chatExistente != null) {
                    chatExistente.id
                } else {
                    val novoChat = hashMapOf(
                        "participantes" to listOf(emailUsuario, email),
                        "ultimaMensagem" to "",
                        "atualizadoEm" to System.currentTimeMillis(),
                        "tipo" to "individual"
                    )
                    db.collection("chats").add(novoChat).await().id
                }

                val intent = Intent(this@InfoEquipeActivity, ChatActivity::class.java)
                intent.putExtra("outroEmail", email)
                intent.putExtra("chatId", chatId)
                startActivity(intent)

            } catch (e: Exception) {
                Toast.makeText(
                    this@InfoEquipeActivity,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun abrirPerfil(email: String) {
        val intent = Intent(this, PerfilUsuarioActivity::class.java)
        intent.putExtra("emailOutro", email)
        startActivity(intent)
    }

    private fun confirmarSairEquipe() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.info_equipe_sair_titulo))
            .setMessage(getString(R.string.info_equipe_sair_mensagem, nomeEquipe))
            .setPositiveButton(getString(R.string.info_equipe_sair_confirmar)) { _, _ ->
                lifecycleScope.launch {
                    try {
                        val membro = db.collection("membros_equipe")
                            .whereEqualTo("equipeId", equipeId)
                            .whereEqualTo("email", emailUsuario)
                            .get().await()

                        membro.documents.forEach { doc ->
                            db.collection("membros_equipe").document(doc.id).delete().await()
                        }

                        Toast.makeText(
                            this@InfoEquipeActivity,
                            getString(R.string.info_equipe_saiu_sucesso),
                            Toast.LENGTH_SHORT
                        ).show()
                        startActivity(Intent(this@InfoEquipeActivity, MainActivity::class.java))
                        finishAffinity()
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@InfoEquipeActivity,
                            getString(R.string.erro_generico, e.message ?: ""),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.info_equipe_cancelar), null)
            .show()
    }

    private fun confirmarExcluirEquipe() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.info_equipe_excluir_titulo))
            .setMessage(getString(R.string.info_equipe_excluir_mensagem))
            .setPositiveButton(getString(R.string.info_equipe_excluir_confirmar)) { _, _ ->
                lifecycleScope.launch {
                    try {
                        // 1. Anexos e comentarios dos trabalhos
                        val trabalhos = db.collection("trabalhos").whereEqualTo("equipeId", equipeId).get().await()
                        for (trabalho in trabalhos.documents) {
                            val anexos = trabalho.reference.collection("anexos").get().await()
                            anexos.documents.forEach { anexo -> anexo.reference.delete().await() }

                            val comentarios = trabalho.reference.collection("comentarios").get().await()
                            comentarios.documents.forEach { comentario -> comentario.reference.delete().await() }
                        }

                        // 2. Trabalhos
                        trabalhos.documents.forEach { db.collection("trabalhos").document(it.id).delete().await() }

                        // 3. Membros
                        val membros = db.collection("membros_equipe").whereEqualTo("equipeId", equipeId).get().await()
                        membros.documents.forEach { db.collection("membros_equipe").document(it.id).delete().await() }

                        // 4. Convites
                        val convites = db.collection("convites_equipe").whereEqualTo("equipeId", equipeId).get().await()
                        convites.documents.forEach { db.collection("convites_equipe").document(it.id).delete().await() }

                        // 5. Pedidos
                        val pedidos = db.collection("pedidos_entrada").whereEqualTo("equipeId", equipeId).get().await()
                        pedidos.documents.forEach { db.collection("pedidos_entrada").document(it.id).delete().await() }

                        // 6. Mensagens do chat de equipe
                        val msgsEquipe = db.collection("chats_equipe").document(equipeId)
                            .collection("mensagens").get().await()
                        msgsEquipe.documents.forEach {
                            db.collection("chats_equipe").document(equipeId)
                                .collection("mensagens").document(it.id).delete().await()
                        }

                        // 7. Grupos + mensagens
                        val grupos = db.collection("grupos").whereEqualTo("equipeId", equipeId).get().await()
                        grupos.documents.forEach { grupoDoc ->
                            val msgsGrupo = grupoDoc.reference.collection("mensagens").get().await()
                            msgsGrupo.documents.forEach { it.reference.delete().await() }
                            grupoDoc.reference.delete().await()
                        }

                        // 8. Doc raiz do chat de equipe
                        db.collection("chats_equipe").document(equipeId).delete().await()

                        // 9. Equipe
                        db.collection("equipes").document(equipeId).delete().await()

                        Toast.makeText(
                            this@InfoEquipeActivity,
                            getString(R.string.info_equipe_excluida_sucesso),
                            Toast.LENGTH_SHORT
                        ).show()
                        startActivity(Intent(this@InfoEquipeActivity, MainActivity::class.java))
                        finishAffinity()

                    } catch (e: Exception) {
                        Toast.makeText(
                            this@InfoEquipeActivity,
                            getString(R.string.erro_generico, e.message ?: ""),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.info_equipe_cancelar), null)
            .show()
    }

    companion object {
        private const val TAG = "INFO_EQUIPE"
    }
}