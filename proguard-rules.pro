# Mandarine ProGuard混淆规则
# 用于Release构建的代码压缩和混淆

# Copyright 2023 Citra Emulator Project
# Licensed under GPLv2 or any later version
# Refer to the license.txt file included.

# ========== 基础配置 ==========

# 启用代码混淆（Release构建时启用）
# 注意：调试构建时不混淆，以便获得可用堆栈跟踪
# 如果需要调试Release构建的堆栈跟踪，请注释此行
# -dontobfuscate

# 启用代码压缩
-optimizationpasses 5

# 允许修改访问修饰符
-allowaccessmodification

# 重新打包类到单一包
-repackageclasses ''

# 保留行号信息，便于调试
-keepattributes SourceFile,LineNumberTable

# 保留签名信息
-keepattributes Signature

# 保留注解
-keepattributes *Annotation*

# 保留泛型信息
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes Signature
-keepattributes Exceptions

# ========== Wini配置 ==========

# 防止使用Wini时崩溃
-keep class org.ini4j.spi.IniParser
-keep class org.ini4j.spi.IniBuilder
-keep class org.ini4j.spi.IniFormatter
-keep class org.ini4j.**
-keepclassmembers class org.ini4j.** { *; }

# ========== 原生代码相关 ==========

# 保留原生方法名
-keepclasseswithmembernames class * {
    native <methods>;
}

# JNI回调
-keepclasseswithmembernames class * {
    native <fields>;
}

# ========== 第三方库配置 ==========

# Boost库
-keep class boost.** { *; }
-dontwarn boost.**
-keepclassmembers class boost.** { *; }

# CryptoPP库
-keep class CryptoPP.** { *; }
-dontwarn CryptoPP.**
-keepclassmembers class CryptoPP.** { *; }

# fmt库
-keep class fmt.** { *; }
-dontwarn fmt.**

# libpng/lodepng
-keep class lodepng.** { *; }
-dontwarn lodepng.**
-keep class png.** { *; }
-dontwarn png.**

# ========== Kotlin配置 ==========

# Kotlin基础保留
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Kotlin反射
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# Kotlin协程
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ========== Android Jetpack库 ==========

# AndroidX
-keep class androidx.** { *; }
-dontwarn androidx.**
-keep interface androidx.** { *; }

# AppCompat
-keep class androidx.appcompat.** { *; }
-dontwarn androidx.appcompat.**

# Lifecycle
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**
-keepclassmembers class * implements androidx.lifecycle.LifecycleObserver {
    <init>(...);
}

# Navigation
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**
-keepnames class androidx.navigation.fragment.NavHostFragment

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}

# ========== Material Design ==========

-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**
-keep class * extends com.google.android.material.internal.ThemedAbstractAppCompatActivity { *; }

# ========== 数据类序列化 ==========

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.github.mandarine3ds.mandarine.**$$serializer { *; }
-keepclassmembers class com.github.mandarine3ds.mandarine.** {
    *** Companion;
}
-keepclasseswithmembers class com.github.mandarine3ds.mandarine.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# JSON处理
-keep class org.json.** { *; }
-dontwarn org.json.**

# ========== 应用特定配置 ==========

# 保留应用Application类
-keep class io.github.mandarine3ds.mandarine.MandarineApplication { *; }
-keep class io.github.mandarine3ds.mandarine.NativeLibrary { *; }

# 保留Activity
-keep class * extends android.app.Activity { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.content.ContentProvider { *; }

# 保留Parcelable
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}

# 保留枚举
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ========== 模拟器核心 ==========

# 保留核心模拟类
-keep class io.github.mandarine3ds.mandarine.emulation.** { *; }
-dontwarn io.github.mandarine3ds.mandarine.emulation.**

# 保留HLE服务
-keep class io.github.mandarine3ds.mandarine.service.** { *; }
-dontwarn io.github.mandarine3ds.mandarine.service.**

# ========== 性能优化 ==========

# 移除日志调用（Release）
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# ========== 资源压缩配置 ==========

# 保留动画资源
-keep class **/anim/** { *; }
-keep class **/animator/** { *; }

# 保留布局资源
-keep class **/layout/** { *; }

# 保留drawable资源
-keep class **/drawable/** { *; }
-keep class **/mipmap/** { *; }

# 保留values资源
-keep class **/values/** { *; }

# 保留xml资源
-keep class **/xml/** { *; }

# 保留raw资源
-keep class **/raw/** { *; }

# ========== WebView配置 ==========

-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public void *(android.webkit.WebView, java.lang.String);
}
-keepclassmembers class * extends android.webkit.WebChromeClient {
    public void *(android.webkit.WebView, java.lang.String);
}

# ========== Native Crash处理 ==========

-keep class * extends java.lang.Exception {
    <init>(java.lang.String, java.lang.Throwable);
}
-keepclassmembers class java.lang.Throwable {
    java.lang.Throwable getCause();
}

# ========== OpenGL配置 ==========

-keep class * extends android.opengl.GLSurfaceView { *; }

# ========== R8警告抑制 ==========

# 抑制R8相关警告
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
-dontwarn java.beans.Introspector
-dontwarn java.beans.VetoableChangeListener
-dontwarn java.beans.VetoableChangeSupport

# ========== 文件压缩优化 ==========

# 启用zip压缩优化
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# ========== 最终检查 ==========

# 打印未使用的类（可选，调试用）
# -printusage unused.txt

# 打印映射关系（重要，用于反混淆）
# -printmapping mapping.txt
