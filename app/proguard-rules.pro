# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# ── Entire Dock Application (prevents reflection/Compose breakage) ────────────
-keep class com.waigi.dock.** { *; }
-keepclassmembers class com.waigi.dock.** { *; }

# ── Compose Runtime & Material 3 ──────────────────────────────────────────────
-keep class androidx.compose.** { *; }

# ── Preserve attributes for reflection, coroutines, and serialization ─────────
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# ── youtubedl-android and all native dependencies ─────────────────────────────
# Keep all classes in youtubedl_android, youtubedl_common, ffmpeg, aria2c
-keep class com.yausername.** { *; }
-keepclassmembers class com.yausername.** { *; }
-keep class org.apache.commons.** { *; }
-keepclassmembers class org.apache.commons.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepclassmembers class com.fasterxml.jackson.** { *; }
-dontwarn java.beans.**
-dontwarn org.tukaani.xz.**
-dontwarn org.w3c.dom.bootstrap.**

# ── Room Database ─────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.Dao { *; }
-keep class **_Impl { *; }
-keepclassmembers class * {
    @androidx.room.Database *;
    @androidx.room.Dao *;
}
-keep class com.waigi.dock.database.** { *; }
-keepclassmembers class com.waigi.dock.database.** { *; }

# ── Koin Dependency Injection & ViewModels ────────────────────────────────────
-keep class org.koin.** { *; }
-keepclassmembers class org.koin.** { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class com.waigi.dock.di.** { *; }
-keep class com.waigi.dock.repository.** { *; }
-keep class com.waigi.dock.ui.viewmodel.** { *; }
-keepclassmembers class com.waigi.dock.ui.viewmodel.** { *; }

# ── App Data Models, Preferences, and State ───────────────────────────────────
-keep class com.waigi.dock.download.DownloadTask { *; }
-keep class com.waigi.dock.download.TaskState** { *; }
-keep class com.waigi.dock.util.DownloadPreferences { *; }
-keep class com.waigi.dock.util.PreferenceUtil { *; }

# ── MMKV ──────────────────────────────────────────────────────────────────────
-keep class com.tencent.mmkv.** { *; }
-keepclassmembers class com.tencent.mmkv.** { *; }

# ── Media3 / ExoPlayer ────────────────────────────────────────────────────────
-keep class androidx.media3.** { *; }
-keepclassmembers class androidx.media3.** { *; }

# ── OkHttp / Kotlin Serialization ─────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
    companion object;
}

