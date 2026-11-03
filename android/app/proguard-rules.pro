# R8 keep rules for the TeeUp release (Play Store) build.
# Libraries such as Firebase, Room, WorkManager, Retrofit and OkHttp ship their own consumer rules;
# these cover the parts of this app that are reached by reflection.

# Gson (via Retrofit's converter) builds the API models reflectively from their field names.
-keep class com.teeup.android.data.** { <fields>; <init>(...); }
-keep class com.teeup.android.data.network.** { *; }
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod

# Retrofit service interfaces: generic return types and suspend functions need their metadata.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# The hole map's WebView calls back into Kotlin through @JavascriptInterface methods.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep line numbers in crash reports (Play Console deobfuscates with the uploaded mapping.txt).
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
