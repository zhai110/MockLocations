# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in D:\Android\sdk/tools/proguard/proguard-android.txt

# ============================================
# 高德地图 SDK 混淆规则
# ============================================
# 保留所有高德地图相关类和方法，防止混淆导致的崩溃或功能异常
-keep class com.amap.api.** { *; }
-keep class com.autonavi.** { *; }
-keep class com.amap.ams.** { *; }
-keep class net.jafama.** { *; }

# 保留 JSON 相关类（高德内部使用）
-keep class org.json.** { *; }

# 保留 Native 方法（高德底层使用 C++ 实现）
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保留枚举类型
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 忽略警告
-dontwarn com.amap.ams.**
-dontwarn net.jafama.**
-dontwarn com.autonavi.**

# ============================================
# Room Database 混淆规则
# ============================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ============================================
# Kotlin 协程和反射
# ============================================
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }

# ============================================
# Gson JSON 序列化
# ============================================
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.** { *; }

# ============================================
# Timber 日志库
# ============================================
-assumenosideeffects class timber.log.Timber {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ============================================
# OkHttp 网络库
# ============================================
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ============================================
# Material Design 和 AndroidX
# ============================================
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# ============================================
# 自定义 View 和 Activity（防止被混淆）
# ============================================
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ============================================
# 数据模型类（防止字段名被混淆）
# ============================================
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
