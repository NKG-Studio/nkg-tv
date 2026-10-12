package studio.nkg.tv;

/** Optional shared Rust inference; no substitute image filter is called AI. */
public final class Core {
    public static final boolean AVAILABLE;
    static { boolean loaded; try { System.loadLibrary("nkg_jni"); loaded = true; } catch (UnsatisfiedLinkError e) { loaded = false; } AVAILABLE = loaded; }
    public static native boolean initModel(String directory);
    public static native int[] upscaleAnime(int[] pixels, int width, int height);
    private Core() {}
    public static native int[] upscale(int[] pixels, int width, int height);
}
