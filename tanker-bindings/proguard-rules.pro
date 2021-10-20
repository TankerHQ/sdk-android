-keepclassmembers class * {
    @io.tanker.api.ProguardKeep <fields>;
}

# Fixes these issues on apps compiled in release mode:
# java.lang.UnsatisfiedLinkError: Can't obtain peer field ID for class com.sun.jna.Pointer
# java.lang.UnsatisfiedLinkError: Can't obtain static method fromNative(Class, Object) from class com.sun.jna.Native
# Taken from:
# https://stackoverflow.com/questions/57005617/java-lang-unsatisfiedlinkerror-cant-obtain-class-com-sun-jna-pointer-neurotec
# https://stackoverflow.com/questions/10557146/how-to-tell-proguard-to-avoid-obfuscating-jna-library-classes
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** {
    <fields>;
    <methods>;
}
