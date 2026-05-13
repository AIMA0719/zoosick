# Default ProGuard rules. Tune when enabling minification.
# https://developer.android.com/build/shrink-code

-keepattributes *Annotation*, InnerClasses
-keepattributes Signature, Exceptions

# kotlinx-serialization
-keepclassmembers class **$$serializer { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
