package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class GerenciarEquipeActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var equipeId: String? = null
    private var nomeEquipe: String = ""
    private var descricaoEquipe: String = ""
    private var equipePrivada: Boolean = false

    private var email: String = ""
    private var nomeUsuario: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gerenciar_equipe)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        email = auth.currentUser?.email ?: ""
        nomeUsuario = getString(R.string.usuario_padrao)

        lifecycleScope.launch {
            try {
                val userDoc = db.collection("usuarios").document(email).get().await()
                nomeUsuario = userDoc.getString("nome") ?: getString(R.string.usuario_padrao)
            } catch (_: Exception) { }
        }

        val edtNomeEquipe = findViewById<EditText>(R.id.edtGerenciarNomeEquipe)
        val edtDescEquipe = findViewById<EditText>(R.id.edtGerenciarDescEquipe)
        val switchPrivada = findViewById<Switch>(R.id.switchEquipePrivada)
        val btnSalvarEquipe = findViewById<Button>(R.id.btnSalvarAlteracoesEquipe)
        val btnExcluirEquipe = findViewById<Button>(R.id.btnExcluirEquipe)
        val btnVoltar = findViewById<Button>(R.id.btnVoltarGerenciar)

        val edtEmailConvite = findViewById<EditText>(R.id.edtEmailConviteEquipe)
        val btnEnviarConvite = findViewById<Button>(R.id.btnEnviarConviteEquipe)

        btnVoltar.setOnClickListener { finish() }

        // ✅ Clicar no campo abre lista de usuários disponíveis
        edtEmailConvite.isFocusable = false
        edtEmailConvite.isClickable = true
        edtEmailConvite.setOnClickListener {
            SeletorUsuarioHelper.abrir(this, email) { emailEscolhido, nome ->
                edtEmailConvite.setText(emailEscolhido)
                Toast.makeText(
                    this,
                    getString(R.string.gerenciar_usuario_selecionado, nome),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        lifecycleScope.launch {
            try {
                val result = db.collection("equipes")
                    .whereEqualTo("criadorEmail", email)
                    .limit(1)
                    .get()
                    .await()

                if (!result.isEmpty) {
                    val doc = result.documents[0]
                    equipeId = doc.id
                    nomeEquipe = doc.getString("nome") ?: ""
                    descricaoEquipe = doc.getString("descricao") ?: ""
                    equipePrivada = doc.getBoolean("privada") ?: false

                    edtNomeEquipe.setText(nomeEquipe)
                    edtDescEquipe.setText(descricaoEquipe)
                    switchPrivada.isChecked = equipePrivada

                    carregarTrabalhos(equipeId!!)
                    carregarPedidos(equipeId!!)
                    carregarMembros(equipeId!!)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar equipe: ${e.message}")
            }
        }

        btnSalvarEquipe.setOnClickListener {
            val novoNome = edtNomeEquipe.text.toString().trim()
            val novaDesc = edtDescEquipe.text.toString().trim()
            val privada = switchPrivada.isChecked

            if (novoNome.isEmpty()) {
                Toast.makeText(this, getString(R.string.erro_nome_equipe_obrigatorio), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            equipeId?.let { id ->
                lifecycleScope.launch {
                    try {
                        db.collection("equipes").document(id)
                            .update(
                                mapOf(
                                    "nome" to novoNome,
                                    "descricao" to novaDesc,
                                    "privada" to privada
                                )
                            )
                            .await()

                        nomeEquipe = novoNome
                        descricaoEquipe = novaDesc
                        equipePrivada = privada

                        Toast.makeText(this@GerenciarEquipeActivity, getString(R.string.gerenciar_dados_atualizados), Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@GerenciarEquipeActivity, getString(R.string.erro_generico, e.message ?: ""), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        btnEnviarConvite.setOnClickListener {
            val emailConvidado = edtEmailConvite.text.toString().trim().lowercase()

            if (emailConvidado.isEmpty()) {
                Toast.makeText(this, getString(R.string.gerenciar_selecione_usuario), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (emailConvidado == email.lowercase()) {
                Toast.makeText(this, getString(R.string.gerenciar_nao_convidar_mesmo), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (equipeId == null) {
                Toast.makeText(this, getString(R.string.gerenciar_equipe_nao_carregada), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val usuarioExiste = db.collection("usuarios")
                        .document(emailConvidado)
                        .get()
                        .await()

                    if (!usuarioExiste.exists()) {
                        Toast.makeText(this@GerenciarEquipeActivity, getString(R.string.gerenciar_email_nao_cadastrado), Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    val todosConvites = db.collection("convites_equipe")
                        .whereEqualTo("emailConvidado", emailConvidado)
                        .get()
                        .await()

                    val convitesPendentes = todosConvites.documents.filter {
                        it.getString("status") == "pendente" &&
                                it.getString("equipeId") == equipeId
                    }

                    if (convitesPendentes.isNotEmpty()) {
                        Toast.makeText(this@GerenciarEquipeActivity, getString(R.string.gerenciar_ja_convidado), Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    val convite = hashMapOf(
                        "equipeId" to equipeId,
                        "nomeEquipe" to nomeEquipe,
                        "descricaoEquipe" to descricaoEquipe,
                        "emailConvidado" to emailConvidado,
                        "emailRemetente" to email,
                        "nomeRemetente" to nomeUsuario,
                        "status" to "pendente",
                        "criadoEm" to System.currentTimeMillis()
                    )

                    db.collection("convites_equipe").add(convite).await()

                    NotificacaoHelper.notificarConviteEquipe(
                        destinatario = emailConvidado,
                        remetente = email,
                        nomeRemetente = nomeUsuario,
                        equipeId = equipeId ?: "",
                        nomeEquipe = nomeEquipe
                    )

                    Toast.makeText(this@GerenciarEquipeActivity, getString(R.string.gerenciar_convite_enviado), Toast.LENGTH_SHORT).show()
                    edtEmailConvite.text.clear()

                } catch (e: Exception) {
                    Toast.makeText(this@GerenciarEquipeActivity, getString(R.string.erro_generico, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }

        btnExcluirEquipe.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.gerenciar_excluir_titulo))
                .setMessage(getString(R.string.gerenciar_excluir_msg))
                .setPositiveButton(getString(R.string.gerenciar_excluir_confirmar)) { _, _ ->
                    equipeId?.let { id ->
                        lifecycleScope.launch {
                            try {
                                val trabalhos = db.collection("trabalhos").whereEqualTo("equipeId", id).get().await()
                                trabalhos.documents.forEach { db.collection("trabalhos").document(it.id).delete().await() }

                                val membros = db.collection("membros_equipe").whereEqualTo("equipeId", id).get().await()
                                membros.documents.forEach { db.collection("membros_equipe").document(it.id).delete().await() }

                                val convites = db.collection("convites_equipe").whereEqualTo("equipeId", id).get().await()
                                convites.documents.forEach { db.collection("convites_equipe").document(it.id).delete().await() }

                                val pedidos = db.collection("pedidos_entrada").whereEqualTo("equipeId", id).get().await()
                                pedidos.documents.forEach { db.collection("pedidos_entrada").document(it.id).delete().await() }

                                val msgsEquipe = db.collection("chats_equipe").document(id)
                                    .collection("mensagens").get().await()
                                msgsEquipe.documents.forEach {
                                    db.collection("chats_equipe").document(id)
                                        .collection("mensagens").document(it.id).delete().await()
                                }

                                val grupos = db.collection("grupos").whereEqualTo("equipeId", id).get().await()
                                grupos.documents.forEach { grupoDoc ->
                                    val msgsGrupo = grupoDoc.reference.collection("mensagens").get().await()
                                    msgsGrupo.documents.forEach { it.reference.delete().await() }
                                    grupoDoc.reference.delete().await()
                                }

                                db.collection("chats_equipe").document(id).delete().await()
                                db.collection("equipes").document(id).delete().await()

                                Toast.makeText(this@GerenciarEquipeActivity, getString(R.string.gerenciar_equipe_excluida), Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this@GerenciarEquipeActivity, MainActivity::class.java))
                                finishAffinity()

                            } catch (e: Exception) {
                                Toast.makeText(this@GerenciarEquipeActivity, getString(R.string.erro_generico, e.message ?: ""), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                .setNegativeButton(R.string.cancelar, null)
                .show()
        }

        configurarBottomNavigation(R.id.nav_groups)
    }

    private fun carregarMembros(equipeId: String) {
        val container = findViewById<LinearLayout>(R.id.containerMembros)
        container.removeAllViews()

        lifecycleScope.launch {
            try {
                val membros = db.collection("membros_equipe")
                    .whereEqualTo("equipeId", equipeId)
                    .get()
                    .await()

                val inflater = LayoutInflater.from(this@GerenciarEquipeActivity)

                if (membros.isEmpty) {
                    val txtVazio = TextView(this@GerenciarEquipeActivity).apply {
                        text = getString(R.string.gerenciar_sem_membros)
                        setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                        textSize = 14f
                        setPadding(0, 16, 0, 16)
                    }
                    container.addView(txtVazio)
                    return@launch
                }

                val membrosOrdenados = membros.documents.sortedWith(
                    compareBy(
                        { it.getString("email") != email },
                        { it.getString("funcao") != "administrador" },
                        { it.getString("nome") ?: "" }
                    )
                )

                val equipeDoc = db.collection("equipes").document(equipeId).get().await()
                val emailDono = equipeDoc.getString("criadorEmail") ?: ""

                membrosOrdenados.forEach { doc ->
                    val membroId = doc.id
                    val nome = doc.getString("nome") ?: getString(R.string.usuario_padrao)
                    val emailMembro = doc.getString("email") ?: ""
                    val funcao = doc.getString("funcao") ?: "membro"

                    val view = inflater.inflate(android.R.layout.simple_list_item_2, container, false)
                    val t1 = view.findViewById<TextView>(android.R.id.text1)
                    val t2 = view.findViewById<TextView>(android.R.id.text2)

                    val souEu = emailMembro == email
                    val ehDono = emailMembro == emailDono

                    val nomeExibido = when {
                        ehDono -> getString(R.string.gerenciar_nome_dono, nome)
                        funcao == "administrador" -> getString(R.string.gerenciar_nome_admin, nome)
                        else -> nome
                    }

                    t1.text = if (souEu) getString(R.string.gerenciar_nome_voce, nomeExibido) else nomeExibido
                    t1.setTextColor(android.graphics.Color.WHITE)
                    t2.text = emailMembro
                    t2.setTextColor(android.graphics.Color.GRAY)
                    view.setPadding(0, 24, 0, 24)

                    view.setOnClickListener {
                        val intent = Intent(this@GerenciarEquipeActivity, PerfilUsuarioActivity::class.java)
                        intent.putExtra("emailOutro", emailMembro)
                        startActivity(intent)
                    }

                    view.setOnLongClickListener {
                        val opcoes = mutableListOf<String>()

                        if (funcao != "administrador" && !ehDono) {
                            opcoes.add(getString(R.string.gerenciar_promover))
                        }
                        if (funcao == "administrador" && !ehDono) {
                            opcoes.add(getString(R.string.gerenciar_rebaixar))
                        }
                        if (!souEu && !ehDono) {
                            opcoes.add(getString(R.string.gerenciar_remover))
                        }

                        if (opcoes.isEmpty()) {
                            Toast.makeText(
                                this@GerenciarEquipeActivity,
                                getString(R.string.gerenciar_nenhuma_acao),
                                Toast.LENGTH_SHORT
                            ).show()
                            return@setOnLongClickListener true
                        }

                        val promover = getString(R.string.gerenciar_promover)
                        val rebaixar = getString(R.string.gerenciar_rebaixar)
                        val remover = getString(R.string.gerenciar_remover)

                        AlertDialog.Builder(this@GerenciarEquipeActivity)
                            .setTitle(nomeExibido)
                            .setItems(opcoes.toTypedArray()) { _, which ->
                                when (opcoes[which]) {
                                    promover -> {
                                        lifecycleScope.launch {
                                            try {
                                                db.collection("membros_equipe").document(membroId)
                                                    .update("funcao", "administrador").await()
                                                Toast.makeText(this@GerenciarEquipeActivity, getString(R.string.gerenciar_promovido), Toast.LENGTH_SHORT).show()
                                                carregarMembros(equipeId)
                                            } catch (e: Exception) {
                                                Toast.makeText(this@GerenciarEquipeActivity, getString(R.string.erro_generico, e.message ?: ""), Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                    rebaixar -> {
                                        lifecycleScope.launch {
                                            try {
                                                db.collection("membros_equipe").document(membroId)
                                                    .update("funcao", "membro").await()
                                                Toast.makeText(this@GerenciarEquipeActivity, getString(R.string.gerenciar_rebaixado), Toast.LENGTH_SHORT).show()
                                                carregarMembros(equipeId)
                                            } catch (e: Exception) {
                                                Toast.makeText(this@GerenciarEquipeActivity, getString(R.string.erro_generico, e.message ?: ""), Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                    remover -> {
                                        AlertDialog.Builder(this@GerenciarEquipeActivity)
                                            .setTitle(getString(R.string.gerenciar_remover_titulo))
                                            .setMessage(getString(R.string.gerenciar_remover_msg, nome))
                                            .setPositiveButton(getString(R.string.gerenciar_remover_confirmar)) { _, _ ->
                                                lifecycleScope.launch {
                                                    try {
                                                        db.collection("membros_equipe").document(membroId).delete().await()
                                                        Toast.makeText(this@GerenciarEquipeActivity, getString(R.string.gerenciar_removido), Toast.LENGTH_SHORT).show()
                                                        carregarMembros(equipeId)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(this@GerenciarEquipeActivity, getString(R.string.erro_generico, e.message ?: ""), Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                            .setNegativeButton(R.string.cancelar, null)
                                            .show()
                                    }
                                }
                            }
                            .show()
                        true
                    }

                    container.addView(view)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro membros: ${e.message}")
            }
        }
    }

    private fun carregarPedidos(equipeId: String) {
        val container = findViewById<LinearLayout>(R.id.containerPedidos)
        container.removeAllViews()

        lifecycleScope.launch {
            try {
                val pedidos = db.collection("pedidos_entrada")
                    .whereEqualTo("equipeId", equipeId)
                    .whereEqualTo("status", "pendente")
                    .get()
                    .await()

                val inflater = LayoutInflater.from(this@GerenciarEquipeActivity)

                if (pedidos.isEmpty) {
                    val txtVazio = TextView(this@GerenciarEquipeActivity).apply {
                        text = getString(R.string.gerenciar_sem_pedidos)
                        setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                        textSize = 14f
                        setPadding(0, 16, 0, 16)
                    }
                    container.addView(txtVazio)
                    return@launch
                }

                pedidos.documents.forEach { doc ->
                    val pedidoId = doc.id
                    val nome = doc.getString("nomeSolicitante") ?: getString(R.string.usuario_padrao)
                    val emailSol = doc.getString("emailSolicitante") ?: ""
                    val motivos = doc.getString("motivos") ?: ""
                    val especialidades = doc.getString("especialidades") ?: ""

                    val view = inflater.inflate(android.R.layout.simple_list_item_2, container, false)
                    val t1 = view.findViewById<TextView>(android.R.id.text1)
                    val t2 = view.findViewById<TextView>(android.R.id.text2)

                    // ✅ Placeholder para "Nome (email)"
                    t1.text = getString(R.string.gerenciar_pedido_linha, nome, emailSol)
                    t1.setTextColor(android.graphics.Color.WHITE)
                    t2.text = getString(R.string.gerenciar_pedido_resumo, motivos, especialidades)
                    t2.setTextColor(android.graphics.Color.GRAY)
                    view.setPadding(0, 24, 0, 24)

                    view.setOnClickListener {
                        AlertDialog.Builder(this@GerenciarEquipeActivity)
                            .setTitle(getString(R.string.gerenciar_pedido_titulo, nome))
                            .setMessage(getString(R.string.gerenciar_pedido_detalhe, motivos, especialidades))
                            .setPositiveButton(getString(R.string.gerenciar_aceitar)) { _, _ ->
                                lifecycleScope.launch {
                                    try {
                                        db.collection("pedidos_entrada").document(pedidoId)
                                            .update("status", "aceito").await()

                                        val membro = hashMapOf(
                                            "equipeId" to equipeId,
                                            "email" to emailSol,
                                            "nome" to nome,
                                            "funcao" to "membro",
                                            "entrouEm" to System.currentTimeMillis()
                                        )
                                        val membroId = "${emailSol}_${equipeId}"
                                        db.collection("membros_equipe").document(membroId).set(membro).await()

                                        NotificacaoHelper.notificarPedidoAceito(
                                            destinatario = emailSol,
                                            remetente = email,
                                            nomeRemetente = nomeUsuario,
                                            equipeId = equipeId,
                                            nomeEquipe = nomeEquipe
                                        )

                                        Toast.makeText(this@GerenciarEquipeActivity, getString(R.string.gerenciar_pedido_aceito), Toast.LENGTH_SHORT).show()
                                        carregarPedidos(equipeId)
                                        carregarMembros(equipeId)

                                    } catch (e: Exception) {
                                        Toast.makeText(this@GerenciarEquipeActivity, getString(R.string.erro_generico, e.message ?: ""), Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            .setNegativeButton(getString(R.string.gerenciar_recusar)) { _, _ ->
                                lifecycleScope.launch {
                                    try {
                                        db.collection("pedidos_entrada").document(pedidoId)
                                            .update("status", "recusado").await()

                                        NotificacaoHelper.criar(
                                            destinatario = emailSol,
                                            tipo = "pedido_recusado",
                                            titulo = getString(R.string.gerenciar_notif_recusado_titulo),
                                            mensagem = getString(R.string.gerenciar_notif_recusado_msg, nomeEquipe),
                                            referenciaId = equipeId,
                                            referenciaTipo = "equipe",
                                            remetente = email,
                                            nomeRemetente = nomeUsuario
                                        )

                                        Toast.makeText(this@GerenciarEquipeActivity, getString(R.string.gerenciar_pedido_recusado), Toast.LENGTH_SHORT).show()
                                        carregarPedidos(equipeId)

                                    } catch (e: Exception) {
                                        Toast.makeText(this@GerenciarEquipeActivity, getString(R.string.erro_generico, e.message ?: ""), Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            .setNeutralButton(getString(R.string.gerenciar_depois), null)
                            .show()
                    }

                    container.addView(view)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro pedidos: ${e.message}")
            }
        }
    }

    private fun carregarTrabalhos(equipeId: String) {
        val container = findViewById<LinearLayout>(R.id.containerTrabalhos)
        container.removeAllViews()

        lifecycleScope.launch {
            try {
                val trabalhos = db.collection("trabalhos")
                    .whereEqualTo("equipeId", equipeId)
                    .get()
                    .await()

                val inflater = LayoutInflater.from(this@GerenciarEquipeActivity)

                if (trabalhos.isEmpty) {
                    val txtVazio = TextView(this@GerenciarEquipeActivity).apply {
                        text = getString(R.string.gerenciar_sem_trabalhos)
                        setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                        textSize = 14f
                        setPadding(0, 16, 0, 16)
                    }
                    container.addView(txtVazio)
                    return@launch
                }

                val textoZero = getString(R.string.produtividade_item_zero)

                trabalhos.documents.forEach { doc ->
                    val trabalhoId = doc.id
                    val titulo = doc.getString("titulo") ?: ""
                    val descricao = doc.getString("descricao") ?: ""
                    val categoria = doc.getString("categoria") ?: ""
                    val prazo = doc.getString("prazo") ?: ""

                    val view = inflater.inflate(R.layout.item_trabalho_gerenciar, container, false)

                    view.findViewById<TextView>(R.id.txtTituloGerenciar).text = titulo
                    // ✅ Placeholder "categoria - prazo"
                    view.findViewById<TextView>(R.id.txtInfoGerenciar).text =
                        getString(R.string.item_trabalho_info_formato, categoria, prazo)
                    view.findViewById<TextView>(R.id.txtDescricaoGerenciar).text = descricao

                    val btnComentarios = view.findViewById<Button>(R.id.btnComentariosGerenciar)
                    lifecycleScope.launch {
                        try {
                            val total = ComentarioHelper.contar(trabalhoId)
                            btnComentarios.text = total.toString()
                        } catch (e: Exception) {
                            btnComentarios.text = textoZero
                        }
                    }

                    btnComentarios.setOnClickListener {
                        val intent = Intent(this@GerenciarEquipeActivity, ComentariosTrabalhoActivity::class.java)
                        intent.putExtra("trabalhoId", trabalhoId)
                        intent.putExtra("tituloTrabalho", titulo)
                        intent.putExtra("equipeId", equipeId)
                        startActivity(intent)
                    }

                    val btnAnexos = view.findViewById<Button>(R.id.btnAnexosGerenciar)
                    lifecycleScope.launch {
                        try {
                            val anexos = doc.reference.collection("anexos").get().await()
                            btnAnexos.text = anexos.size().toString()
                        } catch (e: Exception) {
                            btnAnexos.text = textoZero
                        }
                    }

                    btnAnexos.setOnClickListener {
                        val intent = Intent(this@GerenciarEquipeActivity, AnexosTrabalhoActivity::class.java)
                        intent.putExtra("trabalhoId", trabalhoId)
                        intent.putExtra("tituloTrabalho", titulo)
                        startActivity(intent)
                    }

                    view.findViewById<Button>(R.id.btnConvidarGerenciar).setOnClickListener {
                        val intent = Intent(this@GerenciarEquipeActivity, ConvidarTrabalhoActivity::class.java)
                        intent.putExtra("trabalhoId", trabalhoId)
                        intent.putExtra("tituloTrabalho", titulo)
                        startActivity(intent)
                    }

                    view.setOnClickListener {
                        abrirDialogEditarTrabalho(trabalhoId, titulo, descricao, categoria, prazo)
                    }

                    view.setOnLongClickListener {
                        AlertDialog.Builder(this@GerenciarEquipeActivity)
                            .setTitle(getString(R.string.gerenciar_excluir_trabalho_titulo))
                            .setMessage(getString(R.string.gerenciar_excluir_trabalho_msg, titulo))
                            .setPositiveButton(R.string.excluir) { _, _ ->
                                lifecycleScope.launch {
                                    try {
                                        val comentarios = db.collection("trabalhos").document(trabalhoId)
                                            .collection("comentarios").get().await()
                                        comentarios.documents.forEach { it.reference.delete().await() }

                                        val anexos = db.collection("trabalhos").document(trabalhoId)
                                            .collection("anexos").get().await()
                                        anexos.documents.forEach { it.reference.delete().await() }

                                        db.collection("trabalhos").document(trabalhoId).delete().await()

                                        carregarTrabalhos(equipeId)
                                        Toast.makeText(this@GerenciarEquipeActivity, getString(R.string.gerenciar_trabalho_removido), Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(this@GerenciarEquipeActivity, getString(R.string.erro_generico, e.message ?: ""), Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            .setNegativeButton(R.string.cancelar, null)
                            .show()
                        true
                    }

                    container.addView(view)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Erro trabalhos: ${e.message}")
            }
        }
    }

    // ============================================================
    // ABRIR DIALOG EDITAR TRABALHO
    // ============================================================
    private fun abrirDialogEditarTrabalho(
        trabalhoId: String,
        tituloAtual: String,
        descricaoAtual: String,
        categoriaAtual: String,
        prazoAtual: String
    ) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
        }

        val edtTitulo = EditText(this).apply {
            hint = getString(R.string.criar_trabalho_hint_titulo)
            setText(tituloAtual)
            setTextColor(android.graphics.Color.parseColor("#1C1311"))
        }

        val edtDescricao = EditText(this).apply {
            hint = getString(R.string.criar_trabalho_hint_descricao)
            setText(descricaoAtual)
            setTextColor(android.graphics.Color.parseColor("#1C1311"))
        }

        val edtCategoria = EditText(this).apply {
            hint = getString(R.string.criar_trabalho_hint_categoria)
            setText(categoriaAtual)
            setTextColor(android.graphics.Color.parseColor("#1C1311"))
        }

        val edtPrazo = EditText(this).apply {
            hint = getString(R.string.criar_trabalho_hint_prazo)
            setText(prazoAtual)
            setTextColor(android.graphics.Color.parseColor("#1C1311"))
        }

        layout.addView(edtTitulo)
        layout.addView(edtDescricao)
        layout.addView(edtCategoria)
        layout.addView(edtPrazo)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.gerenciar_editar_trabalho))
            .setView(layout)
            .setPositiveButton(R.string.salvar) { _, _ ->
                val novoTitulo = edtTitulo.text.toString().trim()
                val novaDescricao = edtDescricao.text.toString().trim()
                val novaCategoria = edtCategoria.text.toString().trim()
                val novoPrazo = edtPrazo.text.toString().trim()

                if (novoTitulo.isEmpty() || novaDescricao.isEmpty() || novaCategoria.isEmpty() || novoPrazo.isEmpty()) {
                    Toast.makeText(this, getString(R.string.erro_campos_vazios), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    try {
                        db.collection("trabalhos").document(trabalhoId)
                            .update(
                                mapOf(
                                    "titulo" to novoTitulo,
                                    "descricao" to novaDescricao,
                                    "categoria" to novaCategoria,
                                    "prazo" to novoPrazo
                                )
                            ).await()

                        Toast.makeText(this@GerenciarEquipeActivity, getString(R.string.gerenciar_trabalho_atualizado), Toast.LENGTH_SHORT).show()
                        equipeId?.let { carregarTrabalhos(it) }

                    } catch (e: Exception) {
                        Toast.makeText(this@GerenciarEquipeActivity, getString(R.string.erro_generico, e.message ?: ""), Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    companion object {
        private const val TAG = "GERENCIAR"
    }
}