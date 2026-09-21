package com.example.plataformaremota

import android.content.Context
import com.cloudinary.android.MediaManager

object CloudinaryConfig {

    private var initialized = false

    fun init(context: Context) {
        if (!initialized) {
            val config = HashMap<String, String>()
            config["cloud_name"] = "fqb729sb"
            MediaManager.init(context, config)
            initialized = true
        }
    }
}