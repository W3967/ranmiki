# P19：R8 单 dex 路径下必须保留的入口类
-keep class com.lanweixi.client.LanWeiXiApplication { *; }
-keep class com.lanweixi.client.Hilt_LanWeiXiApplication { *; }
-keep class com.lanweixi.client.MainActivity { *; }
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepclassmembers class * { @javax.inject.Inject <init>(...); }
-keepclasseswithmembers class * { @dagger.hilt.android.AndroidEntryPoint <methods>; }

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
