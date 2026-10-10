package com.example.plataformaremota

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class perfil : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var email: String = ""
    private lateinit var imgAvatar: ImageView
    private lateinit var txtIniciais: TextView

    private val selecionarImagem = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageUri = result.data?.data
            if (imageUri != null) {
                fazerUploadImagem(imageUri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_perfil)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        email = auth.currentUser?.email ?: prefs.getString(KEY_EMAIL_USUARIO, "") ?: ""

        val txtNomePerfil = findViewById<TextView>(R.id.txtNomePerfil)
        val txtProfissaoPerfil = findViewById<TextView>(R.id.txtProfissaoPerfil)
        val txtEmailPerfil = findViewById<TextView>(R.id.txtEmailPerfil)
        val txtProfissaoCard = findViewById<TextView>(R.id.txtProfissaoCard)
        val txtEquipeCard = findViewById<TextView>(R.id.txtEquipeCard)
        val btnAdicionarLink = findViewById<MaterialButton>(R.id.btnAdicionarLink)

        val cardAvatar = findViewById<MaterialCardView>(R.id.cardAvatar)
        val cardCamera = findViewById<MaterialCardView>(R.id.cardCamera)
        imgAvatar = findViewById(R.id.imgAvatar)
        txtIniciais = findViewById(R.id.txtIniciais)

        txtEmailPerfil.text = email
        txtNomePerfil.text = getString(R.string.perfil_carregando)
        txtProfissaoPerfil.text = ""
        txtProfissaoCard.text = ""
        txtEquipeCard.text = getString(R.string.perfil_carregando)

        cardAvatar.setOnClickListener { abrirGaleria() }
        cardCamera.setOnClickListener { abrirGaleria() }

        btnAdicionarLink.setOnClickListener {
            abrirDialogAdicionarLink()
        }

        val btnVoltar = findViewById<Button>(R.id.btnVoltar)
        btnVoltar.text = getString(R.string.voltar)
        btnVoltar.setOnClickListener { finish() }

        val btnMeusTrabalhos = findViewById<Button>(R.id.btnMeusTrabalhos)
        btnMeusTrabalhos.setOnClickListener {
            startActivity(Intent(this, MeusTrabalhosActivity::class.java))
        }

        val btnFavoritos = findViewById<Button>(R.id.btnMensagensFavoritas)
        btnFavoritos.text = getString(R.string.perfil_favoritos)
        btnFavoritos.setOnClickListener {
            startActivity(Intent(this, MensagensFavoritasActivity::class.java))
        }

        val btnSair = findViewById<Button>(R.id.btnSair)
        btnSair.text = getString(R.string.perfil_sair)
        btnSair.setOnClickListener {
            auth.signOut()
            prefs.edit()
                .putBoolean("logado", false)
                .remove(KEY_EMAIL_USUARIO)
                .remove("nomeUsuario")
                .remove("profissaoUsuario")
                .apply()
            Toast.makeText(
                this,
                getString(R.string.perfil_saindo),
                Toast.LENGTH_SHORT
            ).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
        }

        configurarBottomNavigation(R.id.nav_profile)

        lifecycleScope.launch {
            val emailConfirmado = try {
                SessionHelper.aguardarFirebaseAuth(this@perfil)
            } catch (e: Exception) {
                Log.e(TAG, "SessionHelper falhou: ${e.message}")
                email
            }

            if (emailConfirmado.isNotEmpty()) {
                email = emailConfirmado
                txtEmailPerfil.text = email
            }

            carregarDados(txtNomePerfil, txtProfissaoPerfil, txtProfissaoCard, txtEquipeCard)
            carregarLinksComRetry()
            carregarFotoPerfilComRetry()
        }
    }

    private fun abrirGaleria() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        selecionarImagem.launch(intent)
    }

    private fun fazerUploadImagem(uri: Uri) {
        Toast.makeText(
            this,
            getString(R.string.perfil_enviando_foto),
            Toast.LENGTH_SHORT
        ).show()

        MediaManager.get().upload(uri)
            .unsigned("fqb729sb")
            .option("folder", "avatares/")
            .callback(object : com.cloudinary.android.callback.UploadCallback {
                override fun onStart(requestId: String?) {
                    Log.d(TAG_UPLOAD, "Iniciando upload...")
                }

                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}

                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val url = resultData?.get("secure_url") as? String
                    if (url != null) {
                        runOnUiThread { salvarUrlNoFirestore(url) }
                    } else {
                        runOnUiThread {
                            Toast.makeText(
                                this@perfil,
                                getString(R.string.perfil_erro_url_nao_encontrada),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }

                override fun onError(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {
                    Log.e(TAG_UPLOAD, "Erro: ${error?.description}")
                    runOnUiThread {
                        Toast.makeText(
                            this@perfil,
                            getString(R.string.perfil_erro_upload, error?.description ?: ""),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onReschedule(requestId: String?, error: com.cloudinary.android.callback.ErrorInfo?) {}
            })
            .dispatch()
    }

    private fun salvarUrlNoFirestore(url: String) {
        lifecycleScope.launch {
            try {
                db.collection("usuarios").document(email).update("fotoUrl", url).await()
                Glide.with(this@perfil)
                    .load(url)
                    .signature(com.bumptech.glide.signature.ObjectKey(url))
                    .circleCrop()
                    .into(imgAvatar)
                txtIniciais.visibility = View.GONE
                Toast.makeText(
                    this@perfil,
                    getString(R.string.perfil_foto_atualizada),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@perfil,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun carregarFotoPerfilComRetry() {
        lifecycleScope.launch {
            var tentativas = 0
            val maxTentativas = 10
            val fallbackIniciais = getString(R.string.perfil_iniciais_fallback)

            while (tentativas < maxTentativas) {
                try {
                    val usuarioDoc = db.collection("usuarios").document(email).get().await()
                    val fotoUrl = usuarioDoc.getString("fotoUrl") ?: ""
                    val nome = usuarioDoc.getString("nome") ?: fallbackIniciais

                    if (fotoUrl.isNotEmpty()) {
                        Glide.with(this@perfil)
                            .load(fotoUrl)
                            .signature(com.bumptech.glide.signature.ObjectKey(fotoUrl))
                            .circleCrop()
                            .into(imgAvatar)
                        txtIniciais.visibility = View.GONE
                    } else {
                        val iniciais = nome.split(" ")
                            .take(2)
                            .map { it.firstOrNull()?.uppercase() ?: "" }
                            .joinToString("")
                        txtIniciais.text = iniciais.ifEmpty { fallbackIniciais }
                        txtIniciais.visibility = View.VISIBLE
                        imgAvatar.setImageDrawable(null)
                    }
                    return@launch

                } catch (e: Exception) {
                    tentativas++
                    Log.w(TAG, "Foto tentativa $tentativas falhou: ${e.message}")
                    if (tentativas >= maxTentativas) return@launch
                    delay(2000)
                }
            }
        }
    }

    private fun carregarDados(
        txtNomePerfil: TextView,
        txtProfissaoPerfil: TextView,
        txtProfissaoCard: TextView,
        txtEquipeCard: TextView
    ) {
        lifecycleScope.launch {
            Log.e(TAG, "===== DEBUG AUTH =====")
            Log.e(TAG, "auth.currentUser: ${auth.currentUser?.email}")
            Log.e(TAG, "auth.currentUser?.uid: ${auth.currentUser?.uid}")

            var tentativas = 0
            val maxTentativas = 10

            // ✅ Fallbacks traduzidos capturados UMA VEZ
            val fallbackNome = getString(R.string.perfil_nome_fallback)
            val fallbackProfissao = getString(R.string.perfil_profissao_fallback)
            val fallbackNenhumaEquipe = getString(R.string.perfil_nenhuma_equipe)
            val fallbackErro = getString(R.string.perfil_erro_carregar)

            while (tentativas < maxTentativas) {
                try {
                    val usuarioDoc = db.collection("usuarios").document(email).get().await()
                    val nome = usuarioDoc.getString("nome") ?: fallbackNome
                    val profissao = usuarioDoc.getString("profissao") ?: fallbackProfissao

                    txtNomePerfil.text = nome
                    txtProfissaoPerfil.text = profissao
                    txtProfissaoCard.text = profissao

                    val equipeCriador = db.collection("equipes")
                        .whereEqualTo("criadorEmail", email)
                        .limit(1)
                        .get()
                        .await()

                    if (!equipeCriador.isEmpty) {
                        txtEquipeCard.text = equipeCriador.documents[0].getString("nome")
                    } else {
                        val membro = db.collection("membros_equipe")
                            .whereEqualTo("email", email)
                            .limit(1)
                            .get()
                            .await()

                        if (!membro.isEmpty) {
                            val equipeId = membro.documents[0].getString("equipeId") ?: ""
                            val equipeDoc = db.collection("equipes").document(equipeId).get().await()
                            txtEquipeCard.text = equipeDoc.getString("nome") ?: fallbackNenhumaEquipe
                        } else {
                            txtEquipeCard.text = fallbackNenhumaEquipe
                        }
                    }

                    Log.d(TAG, "Dados carregados na tentativa ${tentativas + 1}")
                    return@launch

                } catch (e: Exception) {
                    tentativas++
                    Log.w(TAG, "Dados tentativa $tentativas falhou: ${e.message}")

                    if (tentativas >= maxTentativas) {
                        txtNomePerfil.text = fallbackErro
                        txtProfissaoPerfil.text = "-"
                        txtEquipeCard.text = fallbackErro
                        return@launch
                    }
                    delay(2000)
                }
            }
        }
    }

    private fun carregarLinksComRetry() {
        lifecycleScope.launch {
            var tentativas = 0
            val maxTentativas = 10

            while (tentativas < maxTentativas) {
                try {
                    renderizarLinks()
                    return@launch
                } catch (e: Exception) {
                    tentativas++
                    Log.w(TAG, "Links tentativa $tentativas falhou: ${e.message}")
                    if (tentativas >= maxTentativas) return@launch
                    delay(2000)
                }
            }
        }
    }

    private suspend fun renderizarLinks() {
        val containerLinks = findViewById<LinearLayout>(R.id.containerLinks)
        val txtSemLinks = findViewById<TextView>(R.id.txtSemLinks)

        containerLinks.removeAllViews()

        val usuarioDoc = db.collection("usuarios").document(email).get().await()

        val links = usuarioDoc.get("links") as? List<Map<String, String>> ?: emptyList()

        val linkedinAntigo = usuarioDoc.getString("linkedin") ?: ""
        val githubAntigo = usuarioDoc.getString("github") ?: ""
        val portfolioAntigo = usuarioDoc.getString("portfolio") ?: ""

        val linksMigrados = mutableListOf<Map<String, String>>()
        linksMigrados.addAll(links)

        if (links.isEmpty()) {
            if (linkedinAntigo.isNotEmpty()) {
                linksMigrados.add(hashMapOf("tipo" to "linkedin", "url" to linkedinAntigo))
            }
            if (githubAntigo.isNotEmpty()) {
                linksMigrados.add(hashMapOf("tipo" to "github", "url" to githubAntigo))
            }
            if (portfolioAntigo.isNotEmpty()) {
                linksMigrados.add(hashMapOf("tipo" to "portfolio", "url" to portfolioAntigo))
            }

            if (linksMigrados.isNotEmpty()) {
                db.collection("usuarios").document(email)
                    .update("links", linksMigrados).await()
            }
        }

        if (linksMigrados.isEmpty()) {
            txtSemLinks.visibility = View.VISIBLE
            return
        }

        txtSemLinks.visibility = View.GONE

        val inflater = LayoutInflater.from(this@perfil)

        // ✅ Strings capturadas UMA VEZ
        val msgLinkInvalido = getString(R.string.perfil_link_invalido)
        val removerTitulo = getString(R.string.perfil_remover_link_titulo)
        val removerMsg = getString(R.string.perfil_remover_link_msg)
        val removerSim = getString(R.string.perfil_remover_link_sim)

        linksMigrados.forEachIndexed { index, link ->
            val tipo = link["tipo"] ?: "outro"
            val url = link["url"] ?: ""

            if (url.isEmpty()) return@forEachIndexed

            val view = inflater.inflate(R.layout.item_link, containerLinks, false)

            val cardLogo = view.findViewById<MaterialCardView>(R.id.cardLogoLink)
            val txtLogo = view.findViewById<TextView>(R.id.txtLogoLink)
            val txtUrl = view.findViewById<TextView>(R.id.txtUrlLink)
            val btnRemover = view.findViewById<Button>(R.id.btnRemoverLink)

            val (logo, corFundo, corTexto) = identificarLogo(url, tipo)
            txtLogo.text = logo
            cardLogo.setCardBackgroundColor(Color.parseColor(corFundo))
            txtLogo.setTextColor(Color.parseColor(corTexto))

            txtUrl.text = encurtarUrl(url)

            view.setOnClickListener {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this@perfil, msgLinkInvalido, Toast.LENGTH_SHORT).show()
                }
            }

            btnRemover.setOnClickListener {
                AlertDialog.Builder(this@perfil)
                    .setTitle(removerTitulo)
                    .setMessage(removerMsg)
                    .setPositiveButton(removerSim) { _, _ ->
                        removerLink(index)
                    }
                    .setNegativeButton(R.string.cancelar, null)
                    .show()
            }

            containerLinks.addView(view)
        }
    }

    private fun identificarLogo(url: String, tipo: String): Triple<String, String, String> {
        val urlLower = url.lowercase()

        return when {
            urlLower.contains("linkedin") || tipo == "linkedin" ->
                Triple("in", "#0A66C2", "#FFFFFF")
            urlLower.contains("github") || tipo == "github" ->
                Triple("gh", "#24292E", "#FFFFFF")
            urlLower.contains("gitlab") ->
                Triple("gl", "#FC6D26", "#FFFFFF")
            urlLower.contains("youtube") || urlLower.contains("youtu.be") ->
                Triple("▶", "#FF0000", "#FFFFFF")
            urlLower.contains("instagram") ->
                Triple("ig", "#E4405F", "#FFFFFF")
            urlLower.contains("twitter") || urlLower.contains("x.com") ->
                Triple("X", "#000000", "#FFFFFF")
            urlLower.contains("facebook") ->
                Triple("f", "#1877F2", "#FFFFFF")
            urlLower.contains("behance") ->
                Triple("Bē", "#1769FF", "#FFFFFF")
            urlLower.contains("dribbble") ->
                Triple("Dr", "#EA4C89", "#FFFFFF")
            tipo == "portfolio" ->
                Triple("web", "#3D2B27", "#F5E6D0")
            else ->
                Triple("link", "#3D2B27", "#F5E6D0")
        }
    }

    private fun encurtarUrl(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val host = uri.host ?: url
            val path = uri.path ?: ""
            val caminho = if (path.length > 20) path.substring(0, 20) + "..." else path
            "$host$caminho"
        } catch (e: Exception) {
            url
        }
    }

    private fun abrirDialogAdicionarLink() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
        }

        val txtTitulo = TextView(this).apply {
            text = getString(R.string.perfil_link_instrucao)
            setTextColor(Color.parseColor("#666666"))
            textSize = 14f
            setPadding(0, 0, 0, 20)
        }

        val edtUrl = EditText(this).apply {
            hint = getString(R.string.perfil_link_hint)
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
        }

        layout.addView(txtTitulo)
        layout.addView(edtUrl)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.perfil_link_titulo))
            .setView(layout)
            .setPositiveButton(R.string.salvar) { _, _ ->
                val url = edtUrl.text.toString().trim()

                if (url.isEmpty()) {
                    Toast.makeText(
                        this,
                        getString(R.string.perfil_link_digite_url),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    Toast.makeText(
                        this,
                        getString(R.string.perfil_link_formato_invalido),
                        Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }

                salvarLink(url)
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun salvarLink(url: String) {
        val urlLower = url.lowercase()

        val tipo = when {
            urlLower.contains("linkedin") -> "linkedin"
            urlLower.contains("github") -> "github"
            urlLower.contains("gitlab") -> "gitlab"
            urlLower.contains("youtube") -> "youtube"
            urlLower.contains("youtu.be") -> "youtube"
            urlLower.contains("instagram") -> "instagram"
            urlLower.contains("twitter") -> "twitter"
            urlLower.contains("x.com") -> "twitter"
            urlLower.contains("facebook") -> "facebook"
            urlLower.contains("behance") -> "behance"
            urlLower.contains("dribbble") -> "dribbble"
            else -> "portfolio"
        }

        lifecycleScope.launch {
            try {
                val usuarioDoc = db.collection("usuarios").document(email).get().await()
                val linksAtuais = usuarioDoc.get("links") as? List<Map<String, String>> ?: emptyList()

                val novosLinks = linksAtuais + mapOf(
                    "tipo" to tipo,
                    "url" to url
                )

                db.collection("usuarios").document(email)
                    .update("links", novosLinks).await()

                Toast.makeText(
                    this@perfil,
                    getString(R.string.perfil_link_adicionado),
                    Toast.LENGTH_SHORT
                ).show()
                carregarLinksComRetry()
            } catch (e: Exception) {
                Toast.makeText(
                    this@perfil,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun removerLink(index: Int) {
        lifecycleScope.launch {
            try {
                val usuarioDoc = db.collection("usuarios").document(email).get().await()
                val linksAtuais = usuarioDoc.get("links") as? List<Map<String, String>> ?: emptyList()

                if (index !in linksAtuais.indices) {
                    Toast.makeText(
                        this@perfil,
                        getString(R.string.perfil_link_nao_encontrado),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val novosLinks = linksAtuais.toMutableList().apply { removeAt(index) }

                db.collection("usuarios").document(email)
                    .update("links", novosLinks).await()

                Toast.makeText(
                    this@perfil,
                    getString(R.string.perfil_link_removido),
                    Toast.LENGTH_SHORT
                ).show()
                carregarLinksComRetry()
            } catch (e: Exception) {
                Toast.makeText(
                    this@perfil,
                    getString(R.string.erro_generico, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        private const val TAG = "PERFIL"
        private const val TAG_UPLOAD = "UPLOAD"
        private const val PREFS_NAME = "CTR_PREFS"
        private const val KEY_EMAIL_USUARIO = "emailUsuario"
    }
}