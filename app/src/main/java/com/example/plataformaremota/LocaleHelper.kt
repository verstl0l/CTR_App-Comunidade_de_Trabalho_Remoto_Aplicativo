package com.example.plataformaremota

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AlertDialog
import java.util.Locale

object LocaleHelper {

    private const val PREFS = "CTR_PREFS"
    private const val KEY_IDIOMA = "idioma"

    // Idiomas suportados: código ISO → nome do recurso
    private val IDIOMAS = listOf(
        "pt" to "idioma_portugues",
        "en" to "idioma_ingles",
        "es" to "idioma_espanhol",
        "fr" to "idioma_frances",
        "ru" to "idioma_russo",
        "zh" to "idioma_chines"
    )

    /**
     * Retorna o idioma salvo (ou o padrão do sistema).
     */
    fun getIdiomaAtual(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_IDIOMA, "pt") ?: "pt"
    }

    /**
     * Salva o idioma escolhido.
     */
    fun salvarIdioma(context: Context, codigo: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_IDIOMA, codigo).apply()
    }

    /**
     * Aplica o idioma salvo no Context.
     * Chama isso no attachBaseContext da Activity.
     */
    fun aplicarIdioma(context: Context): Context {
        val codigo = getIdiomaAtual(context)
        return aplicarIdiomaEspecifico(context, codigo)
    }

    private fun aplicarIdiomaEspecifico(context: Context, codigo: String): Context {
        val locale = Locale(codigo)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale)
        } else {
            config.locale = locale
        }

        return context.createConfigurationContext(config)
    }

    /**
     * Abre um dialog pra o user escolher o idioma.
     */
    fun mostrarSeletorIdioma(activity: Activity, onEscolheu: () -> Unit) {
        val atual = getIdiomaAtual(activity)

        val nomes = IDIOMAS.map { (codigo, stringRes) ->
            val nome = activity.getString(
                activity.resources.getIdentifier(stringRes, "string", activity.packageName)
            )
            if (codigo == atual) "✓ $nome" else nome
        }.toTypedArray()

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.selecione_idioma))
            .setItems(nomes) { _, which ->
                val codigo = IDIOMAS[which].first
                salvarIdioma(activity, codigo)
                onEscolheu()
            }
            .setNegativeButton(activity.getString(R.string.cancelar), null)
            .show()
    }
}