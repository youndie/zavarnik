# R8 for a server jar: optimise, do not obfuscate, keep what reflection and service loading
# reach. This is the "sensible server config" the brief's RQ0 asks the baseline to use; what it
# keeps is written down because every keep is a class R8 cannot touch.
-dontobfuscate
-keepattributes *
-keep class bench.MainKt { public static void main(java.lang.String[]); }
# kotlinx.serialization: generated serializers are found by name through Companion.serializer().
-keep class **$$serializer { *; }
-keepclassmembers class * { *** Companion; }
-keepclasseswithmembers class * { kotlinx.serialization.KSerializer serializer(...); }
-keep class kotlinx.serialization.** { *; }
# Ktor and coroutines reach into themselves through ServiceLoader and reflection on names.
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class org.slf4j.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class * { @kotlin.Metadata *; }
# The brief's assumption: Intrinsics checks have no side effects worth keeping.
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNull(...);
    public static void checkNotNullParameter(...);
    public static void checkNotNullExpressionValue(...);
    public static void checkExpressionValueIsNotNull(...);
    public static void checkParameterIsNotNull(...);
    public static void checkReturnedValueIsNotNull(...);
    public static void checkFieldIsNotNull(...);
}
-dontwarn **
-ignorewarnings
