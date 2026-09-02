package org.cf_t.mc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.cf_t.mc.App.PlayerInfo;

public class packetAnl {

    /**
     * Minecraft UUID
     *
     * 8byte MSB 8byte LSB
     */
    public static UUID readUUID(InputStream in)
            throws IOException {

        byte[] bytes = readFully(in, 16);

        DataInputStream data = new DataInputStream(
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
                    length - offset);

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

    /**
     * Login Startを解析する。
     *
     * Login Start:
     *
     * Packet ID Name String Player UUID UUID
     */
    public static String parseLoginStart(
            InputStream in,
            Socket clientSocket) throws IOException {

        /*
         * Name
         */
        String name = packetAnl.readString(in);

        /*
         * UUID
         *
         * Minecraft ProtocolではUUIDは16byte。
         * Java UUIDのmost/least significant bitsとして読む。
         */
        UUID uuid = packetAnl.readUUID(in);

        /*
         * 接続元IP
         */
        String ip = App.getRemoteIp(clientSocket);

        /*
         * UUIDをキーとして保存
         */
        PlayerInfo info = new PlayerInfo(
                name,
                uuid.toString(),
                ip);

        App.PlayerTable.put(uuid.toString(), info);

        System.out.println(
                "Login Start:"
                        + " name=" + name
                        + " uuid=" + uuid
                        + " ip=" + ip);
        return uuid.toString();
    }

    /**
     * Minecraft Handshakeを読み取る。
     */
    public static App.Handshake readHandshake(
            InputStream in) throws IOException {

        /*
         * Packet Length
         */
        int packetLength = packetAnl.readVarInt(in);

        if (packetLength <= 0 || packetLength > 1024) {
            throw new IOException(
                    "Invalid packet length: " + packetLength);
        }

        /*
         * Packet ID + Packet Data
         */
        byte[] packetData = in.readNBytes(packetLength);

        if (packetData.length != packetLength) {
            throw new EOFException(
                    "Incomplete handshake packet");
        }

        ByteArrayInputStream packet = new ByteArrayInputStream(packetData);

        /*
         * Packet ID
         */
        int packetId = packetAnl.readVarInt(packet);

        /*
         * Handshake Packet IDは0x00
         */
        if (packetId != 0x00) {
            throw new IOException(
                    "First packet is not Handshake: 0x"
                            + Integer.toHexString(packetId));
        }

        /*
         * Protocol Version
         */
        int protocolVersion = packetAnl.readVarInt(packet);

        /*
         * Server Address
         */
        String host = packetAnl.readString(packet);

        /*
         * Server Port
         */
        int port = packetAnl.readUnsignedShort(packet);

        /*
         * Next State
         *
         * 1 = Status
         * 2 = Login
         */
        int nextState = packetAnl.readVarInt(packet);

        /*
         * Backendへ送る元のパケットを再構築。
         *
         * Packet Lengthも含める。
         */
        byte[] rawPacket = buildRawPacket(packetData);

        return new App.Handshake(
                protocolVersion,
                host,
                port,
                nextState,
                rawPacket);
    }

    /**
     * Packet Length + Packet Dataを再構築。
     */
    public static byte[] buildRawPacket(
            byte[] packetData) throws IOException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        packetAnl.writeVarInt(out, packetData.length);

        out.write(packetData);

        return out.toByteArray();
    }

}
