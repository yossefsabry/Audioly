# ─── Audioly ProGuard / R8 rules ──────────────────────────────────────────────

# ─── NewPipe Extractor ─────────────────────────────────────────────────────────
# Extractor uses reflection heavily; keep all classes
-keep class org.schabi.newpipe.extractor.** { *; }
-keep interface org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
# Rhino JS engine references JDK java.beans.* not available on Android
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
# Rhino
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**

# ─── OkHttp ────────────────────────────────────────────────────────────────────
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ─── Room entities & DAOs (Audioly) ────────────────────────────────────────────
-keep class com.audioly.app.data.db.entities.** { *; }
-keepclassmembers class com.audioly.app.data.db.entities.** { *; }
-keep interface com.audioly.app.data.db.dao.** { *; }
-keep class com.audioly.app.data.db.AudiolyDatabase { *; }
-keep class com.audioly.app.data.db.AudiolyDatabase_Impl { *; }

# ─── Domain / data models ─────────────────────────────────────────────────────
-keep class com.audioly.app.data.model.** { *; }
-keep class com.audioly.app.extraction.** { *; }
-keep class com.audioly.app.player.PlayerState { *; }
-keep class com.audioly.app.player.SubtitleCue { *; }

# ─── Services ─────────────────────────────────────────────────────────────────
-keep class com.audioly.app.player.AudioService { *; }
-keep class com.audioly.app.player.AudioService$LocalBinder { *; }

# ─── Compose ───────────────────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ─── Media3 / ExoPlayer ───────────────────────────────────────────────────────
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ─── Coil ──────────────────────────────────────────────────────────────────────
-keep class coil.** { *; }
-dontwarn coil.**

# ─── Kotlin ────────────────────────────────────────────────────────────────────
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Metadata { public <methods>; }

# ─── Coroutines ────────────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# ─── Navigation ────────────────────────────────────────────────────────────────
-keep class androidx.navigation.** { *; }
-keepnames class androidx.navigation.fragment.NavHostFragment

# ─── DataStore ─────────────────────────────────────────────────────────────────
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# ─── General Android ──────────────────────────────────────────────────────────

# Keep ViewModels
-keep class * extends androidx.lifecycle.ViewModel { <init>(); }
-keep class * extends androidx.lifecycle.AndroidViewModel { <init>(android.app.Application); }

# Attributes
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep native methods
-keepclasseswithmembernames class * { native <methods>; }

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Suppress warnings for missing optional dependencies
-dontwarn sun.misc.**
-dontwarn javax.annotation.**
