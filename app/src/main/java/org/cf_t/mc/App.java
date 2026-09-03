package org.cf_t.mc;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App {

    // static Setting setting = new Setting();
    // static pluginS plS = new pluginS();

    private static int LISTEN_PORT = 25565;
    private static int infoLISTEN_PORT = 28080;
    private static String infoPIN = "0000";

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

    /*
     * TODO:
     * コマンド入力欄とログエリアの分離
     * 設定のリロード
     * 複数ポートのサポート
     * 
     */
    public static void main(String[] args) throws IOException {
        Player.load();
        System.out.println(Player.getBanPlayer());
        System.out.println(Player.getBanIP());
        /*
         * 設定ロード
         */
        try {
            if (!Files.exists(Path.of("setting.json"))) {
                System.out.println("setting.json not found");
            }
            Setting.Config config = Setting.load("setting.json");

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
                        new Backend(server.remoteHost(), server.port()));
            }
        } catch (IOException e) {
            System.out.println("error with ioException");
            return;
        }

        System.out.println("Minecraft Host Proxy");
        System.out.println("Listening on 0.0.0.0:" + LISTEN_PORT);

        // クライアント情報サーバー
        POOL.execute(() -> {
            try {
                pluginS.serverLoop(infoLISTEN_PORT, infoPIN);
            } catch (IOException ex) {
                System.getLogger(App.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
            }
        });

        try (ServerSocket serverSocket = new ServerSocket(LISTEN_PORT)) {
            while (true) {
                Socket client = serverSocket.accept();
                String IPstr = client.getInetAddress().toString();
                if (Player.checkIP(IPstr)) {
                    closeQuietly(client);
                    System.out.println("banned ip connect: " + IPstr);
                    continue;
                }
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
            packetAnl.Handshake handshake = packetAnl.readHandshake(client.getInputStream());

            System.out.println(
                    "Handshake: host=" + handshake.host()
                            + ", port=" + handshake.port()
                            + ", protocol=" + handshake.protocolVersion()
                            + ", nextState=" + handshake.nextState());

            /*
             * ホスト名でルーティング。
             */
            Backend backend = ROUTES.get(
                    handshake.host().toLowerCase(Locale.ROOT));

            if (backend == null) {
                System.out.println(
                        "Unknown host: " + handshake.host());

                closeQuietly(client);
                return;
            }

            System.out.println(
                    "Routing "
                            + handshake.host()
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

            serverOut.write(handshake.rawPacket());
            serverOut.flush();

            /*
             * 以降は単純なTCPリレー。
             *
             * Client → Server
             */
            POOL.execute(() -> packetRelay.relay(
                    client,
                    server,
                    false));

            /*
             * Server → Client
             */
            POOL.execute(() -> packetRelay.relay(
                    server,
                    client,
                    true));

        } catch (IOException e) {
            System.out.println(
                    "Client error: " + e.getMessage());

            closeQuietly(client);
        }
    }

    private static void closeQuietly(
            Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    public record Backend(
            String host,
            int port) {

    }

}
