# ExamGuard Android — ProGuard/R8 rules for release builds.

# Keep ML Kit face detection model interfaces.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Keep Compose-related kotlin metadata is handled by AGP defaults.
