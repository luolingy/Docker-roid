# Compose
-keep class androidx.compose.** { *; }

# kotlinx.serialization 生成的序列化器
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.midsancode.docklet.data.docker.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.midsancode.docklet.data.docker.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Koin
-keep class org.koin.** { *; }
