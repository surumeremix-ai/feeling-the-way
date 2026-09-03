# the page talks to this class by name through addJavascriptInterface
-keepclassmembers class com.feelingtheway.app.MainActivity$JsBridge {
   public *;
}
-keepattributes JavascriptInterface
