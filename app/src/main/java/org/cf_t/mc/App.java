package org.cf_t.mc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App {

    static Setting setting = new Setting();
    static pluginS plS = new pluginS();

    private static int LISTEN_PORT = 25565;
    private static int infoLISTEN_PORT = 28080;
    private static int infoPIN = 0000;

    /*
         * Minecraftのホスト名 → 転送先
         *
         * 例:
         * mc.example.com → 127.0.0.1:25566
         * pvp.example.com → 127.0.0.1:25567
         * creative.example.com → 127.0.0.1:25568
     */
    private static final Map<String, Backend> ROUTES = new HashMap<>();
    private static final Map<String, PlayerInfo> PlayerTable = new ConcurrentHashMap<>();

    private static final ExecutorService POOL = Executors.newCachedThreadPool();

    /*
    TODO:
    コマンド入力欄とログエリアの分離
    設定のリロード
    複数ポートのサポート
    
     */
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
            infoLISTEN_PORT = config.infoPort();
            infoPIN = config.pin();

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

        //クライアント情報サーバー
        POOL.execute(() -> {
            try {
                plS.serverLoop(infoLISTEN_PORT, infoPIN);
            } catch (IOException ex) {
                System.getLogger(App.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
            }
        });

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
                    server,
                    false));

            /*
                         * Server → Client
             */
            POOL.execute(() -> relay(
                    server,
                    client,
                    true));

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

        packetAnl.writeVarInt(out, packetData.length);

        out.write(packetData);

        return out.toByteArray();
    }

    /**
     * TCPストリームをそのまま転送。
     */
    private static void relay(
            Socket from,
            Socket to,
            boolean s2c) {

        try {
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();

            /*
             * Client -> Server の場合だけ
             * Minecraftパケットとして解析する。
             *
             * Server -> Client は単純転送。
             */
            if (!s2c) {
                relayClientToServer(in, out, from);
            } else {
                relayRaw(in, out);
            }

            /*
             * from側がEOFになったら、
             * to側の出力だけshutdownする。
             *
             * Socket自体はcloseしない。
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
     * Client -> Server
     *
     * Minecraftのパケットフレームを解析しながら転送する。
     */
    private static void relayClientToServer(
            InputStream in,
            OutputStream out,
            Socket clientSocket) throws IOException {

        while (true) {

            /*
             * Packet Length
             */
            int packetLength;

            try {
                packetLength = packetAnl.readVarInt(in);
            } catch (EOFException e) {
                break;
            }

            if (packetLength < 0) {
                throw new IOException("Invalid packet length: " + packetLength);
            }

            /*
             * Packet Data
             */
            byte[] packetData = packetAnl.readFully(in, packetLength);

            /*
             * 元のパケットをそのまま転送する。
             *
             * length + packetData
             */
            packetAnl.writeVarInt(out, packetLength);
            out.write(packetData);
            out.flush();

            /*
             * Packet IDを確認
             */
            ByteArrayInputStream packetIn
                    = new ByteArrayInputStream(packetData);

            int packetId = packetAnl.readVarInt(packetIn);

            /*
             * Login Start
             *
             * Serverbound hello
             * Packet ID = 0x00
             */
            if (packetId == 0) {
                try {
                    parseLoginStart(packetIn, clientSocket);
                } catch (IOException e) {
                    /*
                     * 解析に失敗しても通信自体は止めない。
                     */
                    System.out.println(
                            "Failed to parse Login Start from "
                            + clientSocket.getRemoteSocketAddress()
                            + ": "
                            + e.getMessage());
                }
            }
        }
    }

    /**
     * Login Startを解析する。
     *
     * Login Start:
     *
     * Packet ID Name String Player UUID UUID
     */
    private static void parseLoginStart(
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
        String ip = getRemoteIp(clientSocket);

        /*
         * UUIDをキーとして保存
         */
        PlayerInfo info = new PlayerInfo(
                name,
                uuid.toString(),
                ip
        );

        PlayerTable.put(uuid.toString(), info);

        System.out.println(
                "Login Start:"
                + " name=" + name
                + " uuid=" + uuid
                + " ip=" + ip
        );
    }

    /**
     * Server -> Clientなど、 パケットを解析せずそのまま転送する。
     */
    private static void relayRaw(
            InputStream in,
            OutputStream out) throws IOException {

        byte[] buffer = new byte[8192];

        int read;

        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            out.flush();
        }
    }

    /**
     * Socketから実際の接続元IPを取得する。
     */
    private static String getRemoteIp(Socket socket) {

        if (socket.getRemoteSocketAddress() instanceof InetSocketAddress address) {

            return address.getAddress()
                    .getHostAddress();
        }

        return String.valueOf(
                socket.getRemoteSocketAddress());
    }

    /**
     * UUIDからPlayerInfoを取得する。
     */
    public static PlayerInfo getPlayerInfo(String uuid) {
        return PlayerTable.get(uuid);
    }

    /**
     * UUIDからPlayerInfoを削除する。
     */
    public static PlayerInfo removePlayerInfo(String uuid) {
        return PlayerTable.remove(uuid);
    }

    /**
     * 現在保持しているPlayerTableを取得する。
     *
     * 読み取り専用として使うことを想定。
     */
    public static Map<String, PlayerInfo> getPlayerTable() {
        return Map.copyOf(PlayerTable);
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

    public record PlayerInfo(
            String name,
            String uuid,
            String ip
            ) {

    }
}
