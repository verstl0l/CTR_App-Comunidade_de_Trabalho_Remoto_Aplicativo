package com.example.plataformaremota

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel

class CTRApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // ✅ MODO OFFLINE — habilita cache persistente do Firestore
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(
                PersistentCacheSettings.newBuilder()
                    .setSizeBytes(100L * 1024 * 1024) // 100 MB
                    .build()
            )
            .build()
        FirebaseFirestore.getInstance().firestoreSettings = settings

        // Cloudinary
        CloudinaryConfig.init(this)

        // OneSignal
        OneSignal.Debug.logLevel = LogLevel.VERBOSE
        OneSignal.initWithContext(this, "fe7a7010-113d-4045-8eb5-2398cb4d7572")
    }
}