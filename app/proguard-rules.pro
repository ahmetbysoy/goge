# Kalkan Klavye — keep rules for R8 full mode

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# OkHttp / platform
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn javax.annotation.**
-keep class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Keep IME service entry point
-keep class com.example.service.KalkanIME { *; }
-keep class com.example.MainActivity { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Compose (safe defaults)
-keep class androidx.compose.runtime.** { *; }
