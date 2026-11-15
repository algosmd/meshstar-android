package net.meshstar.codec2;

/** One codec state per note. Serialization also protects upstream DSP global state. */
public final class Codec2 {
    public static final int RATE = 8000;
    public static final int MAX_BYTES = 4788;
    public static final int MAX_SAMPLES = MAX_BYTES / 6 * 320;
    static { System.loadLibrary("meshstar_codec2_jni"); }
    private Codec2() {}
    public static synchronized byte[] encode(short[] pcm) { return encodeNative(pcm); }
    public static synchronized short[] decode(byte[] codec) { return decodeNative(codec); }
    private static native byte[] encodeNative(short[] pcm);
    private static native short[] decodeNative(byte[] codec);
}
