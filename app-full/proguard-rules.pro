# Chaquopy and Full CAS Engine ProGuard Rules
-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Chaquopy reflection and native bindings
-keep class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**
