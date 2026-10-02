package com.example.plataformaremota

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.onesignal.OneSignal
import com.example.plataformaremota.BuildConfig

class CTRApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // ✅ Inicializa o Firebase EXPLICITAMENTE antes de tudo
        // (Garante que o FirebaseAuth tenha um FirebaseApp configurado)
        try {
            FirebaseApp.initializeApp(this)
            Log.d("APP", "✅ FirebaseApp inicializado: ${FirebaseApp.getInstance().name}")
        } catch (e: Exception) {
            Log.e("APP", "❌ Erro ao inicializar FirebaseApp: ${e.message}", e)
        }

        // ✅ Inicializa o Cloudinary com chaves do BuildConfig
        CloudinaryConfig.init(this)

        // ✅ Inicializa o OneSignal com App ID do BuildConfig
        OneSignal.initWithContext(this, BuildConfig.ONESIGNAL_APP_ID)

        // ⚠️ NÃO chamar requestPermission aqui:
        // no OneSignal v5, requestPermission() e suspend function
        // e nao pode ser chamada no Application.onCreate()
    }
}