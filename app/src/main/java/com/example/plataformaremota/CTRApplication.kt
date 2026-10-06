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
        try {
            FirebaseApp.initializeApp(this)
            Log.d("APP", "✅ FirebaseApp inicializado: ${FirebaseApp.getInstance().name}")
        } catch (e: Exception) {
            Log.e("APP", "❌ Erro ao inicializar FirebaseApp: ${e.message}", e)
        }

        // ✅ No Android, o Firebase Auth JÁ persiste por padrão.
        // Não existe setPersistence() no SDK Android — isso é do JS SDK.
        // A persistência é controlada automaticamente pelo SDK e NÃO
        // pode ser desligada via código.

        // ✅ Inicializa o Cloudinary
        CloudinaryConfig.init(this)

        // ✅ Inicializa o OneSignal
        OneSignal.initWithContext(this, BuildConfig.ONESIGNAL_APP_ID)
    }
}