# the page talks to this class by name through addJavascriptInterface
-keepclassmembers class com.okm.feelingtheway.MainActivity$JsBridge {
   public *;
}
-keepattributes JavascriptInterface
