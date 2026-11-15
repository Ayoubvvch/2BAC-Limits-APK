-keepattributes JavascriptInterface,Annotation
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.ayoub.baclimits.MainActivity$AndroidApiBridge { *; }
-keep class com.ayoub.baclimits.MainActivity$AndroidUpdaterBridge { *; }
