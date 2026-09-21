package dev.opennotes.nativebridge

/**
 * Thin JNI surface over the Rust `sdocx-bridge` crate.
 *
 * [parse] returns the import model as JSON (see `tools/sdocx-bridge/src/lib.rs`).
 * The native library is optional at build time so the UI still compiles without an
 * NDK cross-compile; [isAvailable] reports whether import can run.
 */
object SdocxBridge {
    @Volatile
    private var loaded = false

    val isAvailable: Boolean
        get() {
            if (loaded) return true
            return runCatching {
                System.loadLibrary("sdocx_bridge")
                loaded = true
            }.isSuccess
        }

    /** Throws [IllegalArgumentException] for unreadable archives and [UnsatisfiedLinkError] without the library. */
    @JvmStatic
    external fun parse(bytes: ByteArray): String
}
