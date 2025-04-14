# Android Studio Proguard Rules for Blackscreen Overlay app.

# Add project specific ProGuard rules here. By default, the flags in this file are
# appended to flags specified in ${sdk.dir}/tools/proguard/proguard-android-optimize.txt

# Optimization flags are applied by the default file 'proguard-android-optimize.txt'
# Add any project specific keep options here.

# Keep application classes that might be needed for reflection or serialization.
# Adjust the package name to your actual package name.
-keep public class com.werner.black_overlay.** { *; }

# Keep the main Activity and Service
-keep class com.werner.black_overlay.MainActivity { *; }
-keep class com.werner.black_overlay.OverlayService { *; }

# Keep Kotlin Coroutines internals needed at runtime
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# Keep Jetpack Compose internals
-keep public class * extends androidx.compose.runtime.AbstractApplier
-keep public class * implements androidx.compose.runtime.Applier
-keep public class * extends androidx.compose.runtime.Composer
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.material.** { *; } # Adjust if using Material3
-keepclassmembers class androidx.compose.ui.platform.* {*;}
-keepclassmembers class * implements androidx.compose.runtime.snapshots.SnapshotMutableState {*;}
-keepclassmembers class * implements androidx.compose.runtime.DerivedState {*;}
-keepclassmembernames class androidx.compose.runtime.internal.ComposableLambda {*;}
-dontwarn androidx.compose.**

# Keep Lifecycle ViewModel components
-keep class androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keepclassmembers class * implements androidx.lifecycle.ViewModelStoreOwner {
    androidx.lifecycle.ViewModelStore getViewModelStore();
}
-dontwarn androidx.lifecycle.**

# Keep Parcelable implementations (often needed)
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}
-keepclassmembers class **.*Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Add any other necessary rules for libraries you might add later.