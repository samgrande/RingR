# Add project-specific ProGuard rules here.

# Apache Commons Compress — keep all ZipExtraField implementations
# that ExtraFieldUtils discovers and instantiates reflectively.
-keep class org.apache.commons.compress.archivers.zip.ExtraFieldUtils { *; }
-keep class * implements org.apache.commons.compress.archivers.zip.ZipExtraField { *; }

# Suppress warnings for XZ classes (7z codec, not used at runtime)
-dontwarn org.tukaani.xz.**

# Keep youtubedl-android and Chaquopy classes
-keep class io.junkfood.** { *; }
-keep class org.chaquopy.** { *; }

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.junkfood.**$$serializer { *; }
-keepclassmembers class io.junkfood.** {
    *** Companion;
}
-keepclasseswithmembers class io.junkfood.** {
    kotlinx.serialization.KSerializer serializer(...);
}
