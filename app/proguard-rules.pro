# R8 configuration for release builds.

# kotlinx.serialization generates serializer() companions reflectively looked up
# by name; without these rules R8 removes them and parsing fails at runtime.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class io.github.angad7600123.cambio.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.angad7600123.cambio.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.angad7600123.cambio.**$$serializer { *; }

# OkHttp references optional platform classes that are absent on Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
