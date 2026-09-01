package org.cf_t.mc;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class packetAnl {

    /**
     * Minecraft UUID
     *
     * 8byte MSB 8byte LSB
     */
    public static UUID readUUID(InputStream in)
            throws IOException {

        byte[] bytes = readFully(in, 16);

        DataInputStream data
                = new DataInputStream(
                        new ByteArrayInputStream(bytes));

        long most = data.readLong();
        long least = data.readLong();

        return new UUID(most, least);
    }

    /**
     * TCPから指定バイト数を完全に読む。
     */
    public static byte[] readFully(
            InputStream in,
            int length) throws IOException {

        byte[] data = new byte[length];

        int offset = 0;

        while (offset < length) {

            int read = in.read(
                    data,
                    offset,
                    length - offset
            );

            if (read == -1) {
                throw new EOFException(
                        "Unexpected EOF while reading packet");
            }

            offset += read;
        }

        return data;
    }

    /**
     * Minecraft VarIntを読む。
     */
    public static int readVarInt(
            InputStream in) throws IOException {

        int value = 0;
        int position = 0;

        while (true) {

            int current = in.read();

            if (current == -1) {
                throw new EOFException(
                        "Unexpected end of VarInt");
            }

            value |= (current & 0x7F) << position;

            if ((current & 0x80) == 0) {
                return value;
            }

            position += 7;

            if (position >= 35) {
                throw new IOException(
                        "VarInt is too big");
            }
        }
    }

    /**
     * Minecraft VarIntを書く。
     */
    public static void writeVarInt(
            OutputStream out,
            int value) throws IOException {

        while ((value & 0xFFFFFF80) != 0) {

            out.write(
                    (value & 0x7F) | 0x80);

            value >>>= 7;
        }

        out.write(value & 0x7F);
    }

    /**
     * Minecraft Stringを読む。
     */
    public static String readString(
            InputStream in) throws IOException {

        int length = readVarInt(in);

        if (length < 0 || length > 32767) {
            throw new IOException(
                    "Invalid string length: " + length);
        }

        byte[] data = in.readNBytes(length);

        if (data.length != length) {
            throw new EOFException(
                    "Incomplete string");
        }

        return new String(
                data,
                StandardCharsets.UTF_8);
    }

    /**
     * Unsigned Shortを読む。
     */
    public static int readUnsignedShort(
            InputStream in) throws IOException {

        int high = in.read();
        int low = in.read();

        if (high == -1 || low == -1) {
            throw new EOFException(
                    "Unexpected end of port");
        }

        return (high << 8) | low;
    }
}
