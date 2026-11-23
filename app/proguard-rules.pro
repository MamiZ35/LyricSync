-dontobfuscate
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

# App Classes (MainActivity, ViewModels, Models, Remote DTOs, Room Entities/DAOs, Services)
-keep class com.lyricsync.app.** { *; }

# Compose reflection lookup for LocalLifecycleOwner
-keep class androidx.compose.ui.platform.AndroidCompositionLocals_androidKt { *; }

# Lifecycle, SavedState & Room reflection targets
-keep class androidx.lifecycle.** { *; }
-keep class androidx.savedstate.** { *; }
-keep class androidx.room.** { *; }
-keep public class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}
-keep public class * extends androidx.lifecycle.AndroidViewModel {
    public <init>(...);
}

# Gson & Kotlin metadata
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }
-keep class kotlin.Metadata { *; }

# Firebase ComponentDiscovery & Google ML Kit
-keep class com.google.firebase.** { *; }
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
