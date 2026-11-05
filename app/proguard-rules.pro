# Proguard rules for PhotoRoll Galaxy

# Jetpack Compose rules
-keepclassmembers class * extends androidx.compose.ui.Modifier { *; }

# Coil image loading rules
-keep class coil.** { *; }
-dontwarn coil.**

# Gson serialization rules
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class com.photoroll.galaxy.data.model.** { *; }

# Wallpaper Service
-keep class com.photoroll.galaxy.service.PhotoRollWallpaperService { *; }
-keep class com.photoroll.galaxy.service.PhotoRollWallpaperService$PhotoRollEngine { *; }
-keep class com.photoroll.galaxy.scheduler.WallpaperScheduleReceiver { *; }
