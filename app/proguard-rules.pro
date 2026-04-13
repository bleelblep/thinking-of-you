# Add project specific ProGuard rules here.

# Keep Glyph SDK
-keep class com.nothing.ketchum.** { *; }

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Keep Firebase REST client
-keep class com.bleelblep.thinkingofyou.firebase.** { *; }
