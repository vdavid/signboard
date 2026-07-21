# Keep Android framework classes
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep our MainActivity
-keep class com.veszelovszki.signboard.MainActivity { *; }
-keep class com.veszelovszki.signboard.** { *; }
