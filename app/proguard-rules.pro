# Keep the JNI bridge entry points; they are called from Kotlin by name.
-keepclasseswithmembers,includedescriptorclasses class dev.opennotes.nativebridge.** {
    native <methods>;
}
