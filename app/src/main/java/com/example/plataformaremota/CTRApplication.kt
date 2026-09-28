package com.example.plataformaremota

import android.app.Application
import com.onesignal.OneSignal
import com.example.plataformaremota.BuildConfig

class CTRApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // ✅ Inicializa o Cloudinary com chaves do BuildConfig
        CloudinaryConfig.init(this)

        // ✅ Inicializa o OneSignal com App ID do BuildConfig
        OneSignal.initWithContext(this, BuildConfig.ONESIGNAL_APP_ID)

        // ⚠️ NÃO chamar requestPermission aqui:
        // no OneSignal v5, requestPermission() e suspend function
        // e nao pode ser chamada no Application.onCreate()
        //
        // A permissao e pedida automaticamente pela MainActivity
        // (ja tem isso configurado na MainActivity.kt)
    }
}