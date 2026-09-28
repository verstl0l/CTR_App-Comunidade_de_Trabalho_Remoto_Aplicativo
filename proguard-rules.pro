# ============================================================
# CTR - ProGuard Rules
# ============================================================
# Regras para manter classes que usam reflexao (Firebase, Glide,
# Cloudinary, OneSignal, Firestore models). Sem essas regras, o
# ProGuard pode remover classes necessarias em runtime e o app
# crasha em release.

# Manter numeros de linha para debug de stacktrace
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Manter anotacoes e assinaturas genericas
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# ============================================================
# FIREBASE
# ============================================================
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class com.google.firebase.firestore.** { *; }
-keep class com.google.firebase.auth.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firebase Firestore usa reflexao para deserializar
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
}
-keepnames class com.google.firebase.firestore.** { *; }

# ============================================================
# GLIDE
# ============================================================
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.resource.bitmap.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-keep class com.bumptech.glide.** { *; }
-dontwarn com.bumptech.glide.**

# ============================================================
# CLOUDINARY
# ============================================================
-keep class com.cloudinary.** { *; }
-keep class com.cloudinary.android.** { *; }
-dontwarn com.cloudinary.**

# ============================================================
# ONESIGNAL
# ============================================================
-keep class com.onesignal.** { *; }
-keep class com.onesignal.core.** { *; }
-keep class com.onesignal.notifications.** { *; }
-keep class com.onesignal.user.** { *; }
-keep class com.onesignal.session.** { *; }
-keep class com.onesignal.inAppMessages.** { *; }
-keep class com.onesignal.location.** { *; }
-dontwarn com.onesignal.**

# ============================================================
# MPANDROIDCHART (graficos)
# ============================================================
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# ============================================================
# PHOTOVIEW
# ============================================================
-keep class com.github.chrisbanes.photoview.** { *; }
-dontwarn com.github.chrisbanes.photoview.**

# ============================================================
# KOTLIN
# ============================================================
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
}

# ============================================================
# COROUTINES
# ============================================================
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ============================================================
# MODELOS DO APP (se voce usar data class pro Firestore)
# ============================================================
# Se voce tem data classes mapeadas diretamente pelo Firestore,
# mantenha elas aqui. Ajuste o pacote se necessario.
-keep class com.example.plataformaremota.** { *; }

# ============================================================
# WEBVIEW / JAVASCRIPT (necessario pra algumas libs)
# ============================================================
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ============================================================
# PARCELABLE
# ============================================================
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}

# ============================================================
# SERIALIZABLE
# ============================================================
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ============================================================
# NATIVE METHODS
# ============================================================
-keepclasseswithmembernames class * {
    native <methods>;
}

# ============================================================
# ENUMS
# ============================================================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================================
# GOOGLE PLAY SERVICES - AVISOS (nao quebram)
# ============================================================
-dontwarn com.google.android.play.core.**
-dontwarn com.google.android.play.core.splitcompat.**
-dontwarn com.google.android.play.core.splitinstall.**
-dontwarn com.google.android.play.core.tasks.**

# ============================================================
# OKHTTP / OKIO (usadas por Firebase/OneSignal)
# ============================================================
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase