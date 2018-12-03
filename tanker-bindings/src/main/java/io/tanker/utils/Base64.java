package io.tanker.utils;

import com.sun.jna.Memory;
import com.sun.jna.ptr.LongByReference;

import io.tanker.bindings.TankerLib;

/**
 * Encodes and decodes base64 strings and byte[]s
 * Conveniently works on both the JVM and all Android versions.
 */
public final class Base64 {
    static private final TankerLib lib = TankerLib.Companion.create();

    public static byte[] encode(byte[] data) {
        Memory inBuf = new Memory(data.length);
        inBuf.write(0, data, 0, data.length);

        long encodedSize = lib.tanker_base64_encoded_size(data.length);
        Memory outBuf = new Memory(encodedSize);

        lib.tanker_base64_encode(outBuf, inBuf, inBuf.size());
        return outBuf.getByteArray(0, (int)encodedSize);
    }

    public static String encodeToString(byte[] data) {
        return new String(encode(data));
    }

    public static byte[] decode(byte[] data) {
        Memory inBuf = new Memory(data.length);
        inBuf.write(0, data, 0, data.length);

        LongByReference decodedSize = new LongByReference(lib.tanker_base64_decoded_max_size(data.length));
        Memory outBuf = new Memory(decodedSize.getValue());

        lib.tanker_base64_decode(outBuf, decodedSize, inBuf, inBuf.size());
        return outBuf.getByteArray(0, (int)decodedSize.getValue());
    }

    public static byte[] decode(String data) {
        return decode(data.getBytes());
    }
}
