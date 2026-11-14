# ---- 通用 ----
# 跳过 R8 优化 pass（只保留裁剪 + 混淆）。
# 原因：这台机器 7.9 GB 内存还要跑 IDE/WSL，R8 优化阶段峰值 ~2.5 GB，
# 已经三次在 optimizer 里被系统直接杀掉（无 hs_err，进程消失）。
# 裁剪（tree-shaking）和混淆才是决定 dex 体积的部分，优化 pass 主要省运行时开销，
# 对本地 VM 这种 TCG 慢速场景影响可忽略。等换到内存宽裕的机器可以删掉这行。
-dontoptimize

# 保留行号：CrashHandler 会把堆栈写进 files/crash/last_crash.txt，
# 混淆后没有行号就没法定位。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# 不要动 Compose：各 compose 库自带 consumer rules（META-INF/com.android.tools/r8）。
# 曾经写过 `-keep class androidx.compose.** { *; }`，结果 seeds.txt 涨到 14 MB
# （material-icons-extended 上千个图标全被保留），R8 优化阶段跑 40 分钟还出不了包。

# kotlinx.serialization 生成的序列化器
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
