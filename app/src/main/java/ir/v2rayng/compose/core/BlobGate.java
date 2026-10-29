package ir.v2rayng.compose.core;

/**
 * The only bridge to libblobgate.so.
 *
 * Declared in Java on purpose: a Java member keeps the exact name the linker looks for
 * (Java_ir_v2rayng_compose_core_BlobGate_nativeStr), while a Kotlin object can mangle member
 * names - and a mangled name means "UnsatisfiedLinkError" at the first sync, which is a build
 * failure that only shows on the phone.
 */
final class BlobGate {

    static final boolean LOADED;

    static {
        boolean ok;
        try {
            System.loadLibrary("blobgate");
            ok = true;
        } catch (Throwable t) {
            ok = false;
        }
        LOADED = ok;
    }

    private BlobGate() {
    }

    /** One entry point, index-selected; NULL/empty when the index is out of range. */
    static native String nativeStr(int idx);
}
