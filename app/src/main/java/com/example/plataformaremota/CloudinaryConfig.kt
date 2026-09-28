package com.example.plataformaremota

import android.content.Context
import com.cloudinary.android.MediaManager
import com.example.plataformaremota.BuildConfig

object CloudinaryConfig {

    /**
     * Inicializa o Cloudinary com as credenciais do BuildConfig.
     * As chaves vem do local.properties (que esta no .gitignore).
     */
    fun init(context: Context) {
        val config = hashMapOf(
            "cloud_name" to BuildConfig.CLOUDINARY_CLOUD_NAME,
            "api_key" to "",
            "api_secret" to ""
        )
        MediaManager.init(context, config)
    }
}