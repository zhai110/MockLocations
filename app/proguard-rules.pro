# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in D:\Android\sdk/tools/proguard/proguard-android.txt

# 高德地图
-keep class com.amap.api.** { *; }
-keep class com.autonavi.** { *; }
-keep class com.amap.ams.** { *; }
-keep class net.jafama.** { *; }
-dontwarn com.amap.ams.**
-dontwarn net.jafama.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
