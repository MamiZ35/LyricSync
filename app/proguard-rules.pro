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

# App Models, Remote DTOs, Room Entities/DAOs, Services, UI & ViewModels
-keep class com.lyricsync.app.MainActivity { *; }
-keep class com.lyricsync.app.ui.MainViewModel { *; }
-keep class com.lyricsync.app.data.remote.** { *; }
-keep class com.lyricsync.app.data.local.** { *; }
-keep class com.lyricsync.app.data.repository.** { *; }
-keep class com.lyricsync.app.domain.** { *; }
-keep class com.lyricsync.app.service.** { *; }

# Lifecycle ViewModel reflection instantiation
-keep class androidx.lifecycle.ViewModel { *; }
-keep class androidx.lifecycle.AndroidViewModel { *; }
-keep public class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}
-keep public class * extends androidx.lifecycle.AndroidViewModel {
    public <init>(...);
}

# Gson & Kotlin metadata
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }
-keep class kotlin.Metadata { *; }

# Google ML Kit
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
