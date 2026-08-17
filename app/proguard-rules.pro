# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# 数据模型
-keep class com.example.simplemusic.SongInfo { *; }
-keep class com.example.simplemusic.SearchResult { *; }
-keep class com.example.simplemusic.SongUrlResult { *; }
-keep class com.example.simplemusic.DeviceInfo { *; }
-keep class com.example.simplemusic.ApiResponse { *; }