-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Retrofit & OkHttp
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# App Models, Remote DTOs, Room Entities/DAOs, Services
-keep class com.lyricsync.app.data.remote.** { *; }
-keep class com.lyricsync.app.data.local.** { *; }
-keep class com.lyricsync.app.domain.** { *; }
-keep class com.lyricsync.app.service.** { *; }

# Gson & Kotlin metadata
-keep class * extends com.google.gson.reflect.TypeToken
-keep class kotlin.Metadata { *; }

# Google ML Kit
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
