package org.example;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App {

    static Setting setting = new Setting();

    private static int LISTEN_PORT = 25565;

    /*
         * Minecraftのホスト名 → 転送先
         *
         * 例:
         * mc.example.com → 127.0.0.1:25566
         * pvp.example.com → 127.0.0.1:25567
         * creative.example.com → 127.0.0.1:25568
     */
    private static final Map<String, Backend> ROUTES = new HashMap<>();

    private static final ExecutorService POOL = Executors.newCachedThreadPool();

    public static void main(String[] args) throws IOException {
        /*
        設定ロード
         */
        try {
            if (Files.exists(Path.of("setting.json"))) {
                System.out.println("setting.json not found");
            }
            Setting.Config config = setting.load("setting.json");

            System.out.println(config.serverPort());
            LISTEN_PORT = config.serverPort();

            for (Setting.SvConfig server : config.routings()) {
                System.out.println(server.host());
                System.out.println(server.remoteHost());
                System.out.println(server.port());
                ROUTES.put(
                        server.host(),
                        new Backend(server.remoteHost(), server.port())
                );
            }
        } catch (IOException e) {
            System.out.println("error with ioException");
            return;
        }

        System.out.println("Minecraft Host Proxy");
        System.out.println("Listening on 0.0.0.0:" + LISTEN_PORT);

        try (ServerSocket serverSocket = new ServerSocket(LISTEN_PORT)) {
            while (true) {
                Socket client = serverSocket.accept();

                client.setTcpNoDelay(true);

                System.out.println(
                        "Client connected: "
                        + client.getRemoteSocketAddress());

                POOL.execute(() -> handleClient(client));
            }
        }
    }

    private static void handleClient(Socket client) {
        try {
            /*
                         * まずMinecraft Handshakeだけ読む。
                         *
                         * ここではまだBackendには接続しない。
             */
            Handshake handshake = readHandshake(client.getInputStream());

            System.out.println(
                    "Handshake: host=" + handshake.host
                    + ", port=" + handshake.port
                    + ", protocol=" + handshake.protocolVersion
                    + ", nextState=" + handshake.nextState);

            /*
                         * ホスト名でルーティング。
             */
            Backend backend = ROUTES.get(
                    handshake.host.toLowerCase(Locale.ROOT));

            if (backend == null) {
                System.out.println(
                        "Unknown host: " + handshake.host);

                closeQuietly(client);
                return;
            }

            System.out.println(
                    "Routing "
                    + handshake.host
                    + " -> "
                    + backend.host
                    + ":"
                    + backend.port);

            /*
                         * Backendへ接続。
             */
            Socket server = new Socket();

            server.setTcpNoDelay(true);

            server.connect(
                    new InetSocketAddress(
                            backend.host,
                            backend.port),
                    5000);

            /*
                         * ここが重要。
                         *
                         * Handshakeを読んだ時点で、TCPストリームから
                         * Handshakeのバイト列は消費されている。
                         *
                         * BackendにはHandshakeを再構築して送る必要がある。
             */
            OutputStream serverOut = server.getOutputStream();

            serverOut.write(handshake.rawPacket);
            serverOut.flush();

            /*
                         * 以降は単純なTCPリレー。
                         *
                         * Client → Server
             */
            POOL.execute(() -> relay(
                    client,
                    server));

            /*
                         * Server → Client
             */
            POOL.execute(() -> relay(
                    server,
                    client));

        } catch (IOException e) {
            System.out.println(
                    "Client error: " + e.getMessage());

            closeQuietly(client);
        }
    }

    /**
     * Minecraft Handshakeを読み取る。
     */
    private static Handshake readHandshake(
            InputStream in) throws IOException {

        /*
                 * Packet Length
         */
        int packetLength = readVarInt(in);

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
        int packetId = readVarInt(packet);

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
        int protocolVersion = readVarInt(packet);

        /*
                 * Server Address
         */
        String host = readString(packet);

        /*
                 * Server Port
         */
        int port = readUnsignedShort(packet);

        /*
                 * Next State
                 *
                 * 1 = Status
                 * 2 = Login
         */
        int nextState = readVarInt(packet);

        /*
                 * Backendへ送る元のパケットを再構築。
                 *
                 * Packet Lengthも含める。
         */
        byte[] rawPacket = buildRawPacket(packetData);

        return new Handshake(
                protocolVersion,
                host,
                port,
                nextState,
                rawPacket);
    }

    /**
     * Packet Length + Packet Dataを再構築。
     */
    private static byte[] buildRawPacket(
            byte[] packetData) throws IOException {

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        writeVarInt(out, packetData.length);

        out.write(packetData);

        return out.toByteArray();
    }

    /**
     * TCPストリームをそのまま転送。
     */
    private static void relay(
            Socket from,
            Socket to) {

        try {
            InputStream in = from.getInputStream();

            OutputStream out = to.getOutputStream();

            byte[] buffer = new byte[8192];

            int read;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                out.flush();
            }

            /*
         * from側がEOFになったら、
         * to側の出力だけshutdownする。
         *
         * Socket自体をcloseしない。
             */
            try {
                to.shutdownOutput();
            } catch (IOException ignored) {
            }

        } catch (IOException e) {
            System.out.println(
                    "Relay error: "
                    + from.getRemoteSocketAddress()
                    + " -> "
                    + to.getRemoteSocketAddress()
                    + ": "
                    + e.getMessage());

        }
    }

    /**
     * Minecraft VarIntを読む。
     */
    private static int readVarInt(
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
    private static void writeVarInt(
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
    private static String readString(
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
    private static int readUnsignedShort(
            InputStream in) throws IOException {

        int high = in.read();
        int low = in.read();

        if (high == -1 || low == -1) {
            throw new EOFException(
                    "Unexpected end of port");
        }

        return (high << 8) | low;
    }

    private static void closeQuietly(
            Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private record Backend(
            String host,
            int port) {

    }

    private record Handshake(
            int protocolVersion,
            String host,
            int port,
            int nextState,
            byte[] rawPacket) {

    }
}
