# Chaquopy and Full CAS Engine ProGuard Rules
-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Full Activity and Engine
-keep class com.ayoub.baclimits.full.** { *; }
-keep class com.ayoub.baclimits.** { *; }

# Keep Chaquopy reflection and native bindings
-keep class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**
