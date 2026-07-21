# Keep the Activity class and inner classes
-keep class com.veszelovszki.signboard.MainActivity
-keep class com.veszelovszki.signboard.MainActivity$*

# Keep Android framework entry points
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.widget.ArrayAdapter

# Keep framework-invoked constructors
-keepclasseswithmembers class * {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
}

# Keep enum methods (framework requirement)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep lambdas used in callbacks
-keepclassmembers class com.veszelovszki.signboard.** {
  *** lambda*(...);
}

# Aggressive optimization and obfuscation
-optimizationpasses 5
-allowaccessmodification
-dontpreverify
