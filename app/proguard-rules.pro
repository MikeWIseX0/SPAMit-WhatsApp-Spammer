# ProGuard / R8 rules for SPAMit

# Keep the InputMethodService
-keep public class com.burhanuday.spamit.SPAMit {
    public *;
}

# Keep all Activities
-keep public class com.burhanuday.spamit.MainActivity { *; }
-keep public class com.burhanuday.spamit.Tutorial { *; }
-keep public class com.burhanuday.spamit.Pref { *; }
-keep public class com.burhanuday.spamit.AboutActivity { *; }
-keep public class com.burhanuday.spamit.Constants { *; }

# Keep Accessibility Service
-keep public class * extends android.accessibilityservice.AccessibilityService

# Keep Service classes
-keep public class * extends android.app.Service

# Keep View IDs for accessibility traversal
-keepclassmembers class **.R$id {
    public static <fields>;
}

# Keep BuildConfig
-keep class com.burhanuday.spamit.BuildConfig { *; }

# Keep onClick handler methods (referenced from XML)
-keepclassmembers class * {
    public void htu(android.view.View);
    public void openSt(android.view.View);
    public void selectKeyboard(android.view.View);
    public void con_app(android.view.View);
}

# AndroidX
-dontwarn com.google.android.material.**
-keep class com.google.android.material.** { *; }
-dontwarn androidx.**
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# General Android
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
