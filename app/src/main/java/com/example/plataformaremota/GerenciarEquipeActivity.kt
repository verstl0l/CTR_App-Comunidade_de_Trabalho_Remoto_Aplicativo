package com.example.plataformaremota

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class GerenciarEquipeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var equipeId: String? = null
    private var nomeEquipe: String = ""
    private var descricaoEquipe: String = ""
    private var equipePrivada: Boolean = false

    private var email: String = ""
    private var nomeUsuario: String = "Usuário"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gerenciar_equipe)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        email = auth.currentUser?.email ?: ""
        nomeUsuario = auth.currentUser?.displayName ?: "Usuário"

        val edtNomeEquipe = findViewById<EditText>(R.id.edtGerenciarNomeEquipe)
        val edtDescEquipe = findViewById<EditText>(R.id.edtGerenciarDescEquipe)
        val switchPrivada = findViewById<Switch>(R.id.switchEquipePrivada)
        val btnSalvarEquipe = findViewById<Button>(R.id.btnSalvarAlteracoesEquipe)
        val btnExcluirEquipe = findViewById<Button>(R.id.btnExcluirEquipe)
        val btnVoltar = findViewById<Button>(R.id.btnVoltarGerenciar)

        val edtEmailConvite = findViewById<EditText>(R.id.edtEmailConviteEquipe)
        val btnEnviarConvite = findViewById<Button>(R.id.btnEnviarConviteEquipe)

        btnVoltar.setOnClickListener { finish() }

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
                Log.e("GERENCIAR", "Erro ao carregar equipe: ${e.message}")
            }
        }

        btnSalvarEquipe.setOnClickListener {
            val novoNome = edtNomeEquipe.text.toString().trim()
            val novaDesc = edtDescEquipe.text.toString().trim()
            val privada = switchPrivada.isChecked

            if (novoNome.isEmpty()) {
                Toast.makeText(this, "O nome da equipe é obrigatório", Toast.LENGTH_SHORT).show()
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

                        Toast.makeText(this@GerenciarEquipeActivity, "Dados atualizados!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@GerenciarEquipeActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        btnEnviarConvite.setOnClickListener {
            val emailConvidado = edtEmailConvite.text.toString().trim().lowercase()

            if (emailConvidado.isEmpty()) {
                Toast.makeText(this, "Digite um email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (emailConvidado == email.lowercase()) {
                Toast.makeText(this, "Você não pode convidar a si mesmo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (equipeId == null) {
                Toast.makeText(this, "Equipe não carregada ainda", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val usuarioExiste = db.collection("usuarios")
                        .document(emailConvidado)
                        .get()
                        .await()

                    if (!usuarioExiste.exists()) {
                        Toast.makeText(this@GerenciarEquipeActivity, "Este email não está cadastrado", Toast.LENGTH_LONG).show()
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
                        Toast.makeText(this@GerenciarEquipeActivity, "Este usuário já foi convidado", Toast.LENGTH_SHORT).show()
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

                    Toast.makeText(this@GerenciarEquipeActivity, "✅ Convite enviado!", Toast.LENGTH_SHORT).show()
                    edtEmailConvite.text.clear()

                } catch (e: Exception) {
                    Toast.makeText(this@GerenciarEquipeActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // ============================================================
        // ✅ BUG CORRIGIDO: deleta subcoleções antes do doc raiz
        // ============================================================
        btnExcluirEquipe.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Excluir Equipe")
                .setMessage("Tem certeza? Todos os trabalhos serão removidos permanentemente.")
                .setPositiveButton("Sim, excluir") { _, _ ->
                    equipeId?.let { id ->
                        lifecycleScope.launch {
                            try {
                                // 1. Trabalhos
                                val trabalhos = db.collection("trabalhos").whereEqualTo("equipeId", id).get().await()
                                trabalhos.documents.forEach { db.collection("trabalhos").document(it.id).delete().await() }

                                // 2. Membros
                                val membros = db.collection("membros_equipe").whereEqualTo("equipeId", id).get().await()
                                membros.documents.forEach { db.collection("membros_equipe").document(it.id).delete().await() }

                                // 3. Convites
                                val convites = db.collection("convites_equipe").whereEqualTo("equipeId", id).get().await()
                                convites.documents.forEach { db.collection("convites_equipe").document(it.id).delete().await() }

                                // 4. Pedidos
                                val pedidos = db.collection("pedidos_entrada").whereEqualTo("equipeId", id).get().await()
                                pedidos.documents.forEach { db.collection("pedidos_entrada").document(it.id).delete().await() }

                                // 5. ✅ Mensagens do chat de equipe ANTES do doc raiz
                                val msgsEquipe = db.collection("chats_equipe").document(id)
                                    .collection("mensagens").get().await()
                                msgsEquipe.documents.forEach {
                                    db.collection("chats_equipe").document(id)
                                        .collection("mensagens").document(it.id).delete().await()
                                }

                                // 6. ✅ Grupos + suas mensagens
                                val grupos = db.collection("grupos").whereEqualTo("equipeId", id).get().await()
                                grupos.documents.forEach { grupoDoc ->
                                    val msgsGrupo = grupoDoc.reference.collection("mensagens").get().await()
                                    msgsGrupo.documents.forEach { it.reference.delete().await() }
                                    grupoDoc.reference.delete().await()
                                }

                                // 7. ✅ Doc raiz do chat de equipe
                                db.collection("chats_equipe").document(id).delete().await()

                                // 8. ✅ Equipe
                                db.collection("equipes").document(id).delete().await()

                                Toast.makeText(this@GerenciarEquipeActivity, "🗑️ Equipe excluída!", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this@GerenciarEquipeActivity, MainActivity::class.java))
                                finishAffinity()

                            } catch (e: Exception) {
                                Toast.makeText(this@GerenciarEquipeActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        configurarBottomNavigation()
    }

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
            hint = "Título"
            setText(tituloAtual)
            setTextColor(android.graphics.Color.parseColor("#1C1311"))
        }

        val edtDescricao = EditText(this).apply {
            hint = "Descrição"
            setText(descricaoAtual)
            setTextColor(android.graphics.Color.parseColor("#1C1311"))
        }

        val edtCategoria = EditText(this).apply {
            hint = "Categoria"
            setText(categoriaAtual)
            setTextColor(android.graphics.Color.parseColor("#1C1311"))
        }

        val edtPrazo = EditText(this).apply {
            hint = "Prazo"
            setText(prazoAtual)
            setTextColor(android.graphics.Color.parseColor("#1C1311"))
        }

        layout.addView(edtTitulo)
        layout.addView(edtDescricao)
        layout.addView(edtCategoria)
        layout.addView(edtPrazo)

        AlertDialog.Builder(this)
            .setTitle("Editar Trabalho")
            .setView(layout)
            .setPositiveButton("Salvar") { _, _ ->
                val novoTitulo = edtTitulo.text.toString().trim()
                val novaDescricao = edtDescricao.text.toString().trim()
                val novaCategoria = edtCategoria.text.toString().trim()
                val novoPrazo = edtPrazo.text.toString().trim()

                if (novoTitulo.isEmpty() || novaDescricao.isEmpty() || novaCategoria.isEmpty() || novoPrazo.isEmpty()) {
                    Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
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

                        Toast.makeText(this@GerenciarEquipeActivity, "✅ Trabalho atualizado!", Toast.LENGTH_SHORT).show()
                        equipeId?.let { carregarTrabalhos(it) }

                    } catch (e: Exception) {
                        Toast.makeText(this@GerenciarEquipeActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun configurarBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_chat -> {
                    startActivity(Intent(this, ListaConversasActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_groups -> {
                    startActivity(Intent(this, MinhasEquipesActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_notifications -> {
                    startActivity(Intent(this, notificacao::class.java))
                    finish()
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, perfil::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
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
                        text = "Nenhum membro ainda"
                        setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                        textSize = 14f
                        setPadding(0, 16, 0, 16)
                    }
                    container.addView(txtVazio)
                    return@launch
                }

                membros.documents.forEach { doc ->
                    val membroId = doc.id
                    val nome = doc.getString("nome") ?: "Usuário"
                    val emailMembro = doc.getString("email") ?: ""
                    val funcao = doc.getString("funcao") ?: "membro"

                    val view = inflater.inflate(android.R.layout.simple_list_item_2, container, false)
                    val t1 = view.findViewById<TextView>(android.R.id.text1)
                    val t2 = view.findViewById<TextView>(android.R.id.text2)

                    t1.text = "$nome ($funcao)"
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
                        AlertDialog.Builder(this@GerenciarEquipeActivity)
                            .setTitle(nome)
                            .setItems(arrayOf("Promover a Administrador", "Remover da Equipe")) { _, which ->
                                lifecycleScope.launch {
                                    try {
                                        when (which) {
                                            0 -> {
                                                db.collection("membros_equipe").document(membroId)
                                                    .update("funcao", "administrador").await()
                                                Toast.makeText(this@GerenciarEquipeActivity, "Promovido!", Toast.LENGTH_SHORT).show()
                                            }
                                            1 -> {
                                                db.collection("membros_equipe").document(membroId).delete().await()
                                                Toast.makeText(this@GerenciarEquipeActivity, "Removido!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        carregarMembros(equipeId)
                                    } catch (e: Exception) {
                                        Toast.makeText(this@GerenciarEquipeActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            .show()
                        true
                    }

                    container.addView(view)
                }
            } catch (e: Exception) {
                Log.e("GERENCIAR", "Erro membros: ${e.message}")
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
                        text = "Nenhum pedido pendente"
                        setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                        textSize = 14f
                        setPadding(0, 16, 0, 16)
                    }
                    container.addView(txtVazio)
                    return@launch
                }

                pedidos.documents.forEach { doc ->
                    val pedidoId = doc.id
                    val nome = doc.getString("nomeSolicitante") ?: "Usuário"
                    val emailSol = doc.getString("emailSolicitante") ?: ""
                    val motivos = doc.getString("motivos") ?: ""
                    val especialidades = doc.getString("especialidades") ?: ""

                    val view = inflater.inflate(android.R.layout.simple_list_item_2, container, false)
                    val t1 = view.findViewById<TextView>(android.R.id.text1)
                    val t2 = view.findViewById<TextView>(android.R.id.text2)

                    t1.text = "$nome ($emailSol)"
                    t1.setTextColor(android.graphics.Color.WHITE)
                    t2.text = "Motivos: $motivos\nEspecialidades: $especialidades"
                    t2.setTextColor(android.graphics.Color.GRAY)
                    view.setPadding(0, 24, 0, 24)

                    view.setOnClickListener {
                        AlertDialog.Builder(this@GerenciarEquipeActivity)
                            .setTitle("Pedido de $nome")
                            .setMessage("Motivos: $motivos\n\nEspecialidades: $especialidades")
                            .setPositiveButton("Aceitar") { _, _ ->
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
                                        db.collection("membros_equipe").add(membro).await()

                                        Toast.makeText(this@GerenciarEquipeActivity, "✅ Pedido aceito!", Toast.LENGTH_SHORT).show()
                                        carregarPedidos(equipeId)
                                        carregarMembros(equipeId)
                                    } catch (e: Exception) {
                                        Toast.makeText(this@GerenciarEquipeActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            .setNegativeButton("Recusar") { _, _ ->
                                lifecycleScope.launch {
                                    try {
                                        db.collection("pedidos_entrada").document(pedidoId)
                                            .update("status", "recusado").await()
                                        Toast.makeText(this@GerenciarEquipeActivity, "❌ Pedido recusado", Toast.LENGTH_SHORT).show()
                                        carregarPedidos(equipeId)
                                    } catch (e: Exception) {
                                        Toast.makeText(this@GerenciarEquipeActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            .show()
                    }

                    container.addView(view)
                }
            } catch (e: Exception) {
                Log.e("GERENCIAR", "Erro pedidos: ${e.message}")
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
                        text = "Nenhum trabalho publicado ainda"
                        setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
                        textSize = 14f
                        setPadding(0, 16, 0, 16)
                    }
                    container.addView(txtVazio)
                    return@launch
                }

                trabalhos.documents.forEach { doc ->
                    val trabalhoId = doc.id
                    val titulo = doc.getString("titulo") ?: ""
                    val descricao = doc.getString("descricao") ?: ""
                    val categoria = doc.getString("categoria") ?: ""
                    val prazo = doc.getString("prazo") ?: ""

                    val view = inflater.inflate(R.layout.item_trabalho_gerenciar, container, false)

                    view.findViewById<TextView>(R.id.txtTituloGerenciar).text = titulo
                    view.findViewById<TextView>(R.id.txtInfoGerenciar).text = "$categoria - $prazo"
                    view.findViewById<TextView>(R.id.txtDescricaoGerenciar).text = descricao

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
                            .setTitle("Excluir trabalho")
                            .setMessage("Tem certeza que deseja excluir '$titulo'?")
                            .setPositiveButton("Excluir") { _, _ ->
                                lifecycleScope.launch {
                                    try {
                                        db.collection("trabalhos").document(trabalhoId).delete().await()
                                        carregarTrabalhos(equipeId)
                                        Toast.makeText(this@GerenciarEquipeActivity, "Trabalho removido!", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(this@GerenciarEquipeActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            .setNegativeButton("Cancelar", null)
                            .show()
                        true
                    }

                    container.addView(view)
                }

            } catch (e: Exception) {
                Log.e("GERENCIAR", "Erro trabalhos: ${e.message}")
            }
        }
    }
}