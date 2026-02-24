# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK's proguard-android-optimize.txt.

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# Keep Retrofit interfaces
-keep interface com.tcmpulse.pulseapp.data.api.** { *; }

# Keep data models for Gson
-keep class com.tcmpulse.pulseapp.data.model.** { *; }

# Keep Room entities
-keep @androidx.room.Entity class * { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
