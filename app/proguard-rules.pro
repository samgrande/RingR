-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.youtubedl_android.mapper.** { *; }
-keep class com.yausername.youtubedl_common.** { *; }

-keep class com.fasterxml.jackson.** { *; }

-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.* <fields>;
    @com.fasterxml.jackson.annotation.* <init>(...);
}

-dontwarn java.beans.**
-dontwarn org.w3c.dom.bootstrap.DOMImplementationRegistry

-keep class org.apache.commons.compress.** { *; }
-keep class org.apache.commons.io.** { *; }

-dontwarn org.tukaani.xz.**
