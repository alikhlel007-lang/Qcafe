# Keep line numbers for stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep app domain models, Room entities, and developer models intact
-keep class com.example.data.model.** { *; }
-keep class com.example.data.local.** { *; }
-keep class com.example.ui.developer.** { *; }

# Keep Kotlin enums & coroutines
-keepclassmembers class * extends java.lang.Enum {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Android WebView JavaScript support & Print classes
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
