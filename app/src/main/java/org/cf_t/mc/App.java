package org.cf_t.mc;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.mojang.brigadier.arguments.StringArgumentType;

public class App {

    private static int LISTEN_PORT = 25565;
    private static int infoLISTEN_PORT = 28080;
    private static String infoPIN = "0000";

    public static boolean continueT = true;

    /*
     * Minecraftのホスト名 → 転送先
     *
     * 例:
     * mc.example.com → 127.0.0.1:25566
     * pvp.example.com → 127.0.0.1:25567
     * creative.example.com → 127.0.0.1:25568
     */
    private static final Map<String, Backend> ROUTES = new HashMap<>();

    public static final ExecutorService POOL = Executors.newCachedThreadPool();

    /*
     * TODO
     * 複数ポートのサポート
     */
    public static void main(String[] args) throws IOException {
        Command.init();

        Command.out(LocalDateTime.now());

        Player.load();
        Command.out(Player.getBanPlayer());
        Command.out(Player.getBanIP());
        /*
         * 設定ロード
         */
        loadSetting();

        Command.out("Minecraft Host Proxy");
        Command.out("Listening on 0.0.0.0:" + LISTEN_PORT);

        /*
         * クライアント管理サーバー
         */
        POOL.execute(() -> {
            try {
                pluginS.serverLoop(infoLISTEN_PORT, infoPIN);
            } catch (IOException ex) {
                System.getLogger(App.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
            }
        });

        /*
         * リレースレッド
         */
        POOL.execute(() -> {
            try (ServerSocket serverSocket = new ServerSocket(LISTEN_PORT)) {
                while (continueT) {
                    Socket client = serverSocket.accept();
                    String rawAddr = client.getRemoteSocketAddress().toString();
                    String IPstr = rawAddr.substring(1, rawAddr.lastIndexOf(':'));
                    // Command.out("client addr:" + IPstr);
                    if (Player.checkIP(IPstr)) {
                        closeQuietly(client);
                        Command.out("banned ip connect: " + IPstr);
                        continue;
                    }
                    client.setTcpNoDelay(true);

                    Command.out(
                            "Client connected: "
                                    + client.getRemoteSocketAddress());

                    POOL.execute(() -> handleClient(client));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        /*
         * コマンド登録
         */
        registerCommands();

        // Command loop
        while (continueT) {
            String c = Command.in();
            if (c == null)
                break;
            if (c.isBlank())
                continue;
            c = c.trim();
            if (c.equals("exit")) {
                Command.out("shutdown");
                POOL.shutdownNow();
                Command.close();
                System.exit(0);
                break;
            }
            Command.execute(c);
        }
        Command.close();

    }

    private static void registerCommands() {
        /*
         * help系
         */
        Command.register(Commands.literal("help").executes(c -> {
            Command.out("Help!");
            return 1;
        }).then(Commands.literal("me").executes(c -> {
            Command.out("Help me!");
            return 1;
        })));
        /*
         * List系
         */
        Command.register(Commands.literal("list").executes(c -> {
            Command.out(Player.getPlayerTable());
            return 1;
        }).then(Commands.literal("ipBan").executes(c -> {
            Command.out(Player.getBanIP());
            return 1;
        })).then(Commands.literal("nameBan").executes(c -> {
            Command.out(Player.getBanPlayer());
            return 1;
        })));
        /*
         * ban
         */
        Command.register(Commands.literal("ban")
                .then(Commands.literal("ip").then(Commands.argument("addr", StringArgumentType.word()).executes(c -> {
                    String addr = StringArgumentType.getString(c, "addr");
                    Command.out("ban: " + addr);
                    Player.banIP(addr);
                    return 1;
                })))
                .then(Commands.literal("name").then(Commands.argument("name", StringArgumentType.word()).executes(c -> {
                    String name = StringArgumentType.getString(c, "name");
                    Command.out("ban: " + name);
                    Player.banPlayer(name);
                    return 1;
                }))));
        /*
         * pardon
         */
        Command.register(Commands.literal("pardon")
                .then(Commands.literal("ip").then(Commands.argument("addr", StringArgumentType.word()).executes(c -> {
                    String addr = StringArgumentType.getString(c, "addr");
                    Command.out("pardon: " + addr);
                    Player.deBanIP(addr);
                    return 1;
                })))
                .then(Commands.literal("name").then(Commands.argument("name", StringArgumentType.word()).executes(c -> {
                    String name = StringArgumentType.getString(c, "name");
                    Command.out("pardon: " + name);
                    Player.deBanPlayer(name);
                    return 1;
                }))));
        /*
         * reload
         */
        Command.register(Commands.literal("reload").executes(c -> {
            Player.load();
            loadSetting();
            return 1;
        }));
        /*
         * route
         */
        Command.register(Commands.literal("route").then(Commands.literal("list").executes(c -> {
            Command.out(ROUTES);
            return 1;
        })));
    }

    private static void loadSetting() {
        try {
            if (!Files.exists(Path.of("setting.json"))) {
                Command.out("setting.json not found");
            }
            Setting.Config config = Setting.load("setting.json");

            Command.out(config.serverPort());
            LISTEN_PORT = config.serverPort();
            infoLISTEN_PORT = config.infoPort();
            infoPIN = config.pin();

            for (Setting.SvConfig server : config.routings()) {
                // Command.out(server.host());
                // Command.out(server.remoteHost());
                // Command.out(server.port());
                Command.out(server);
                ROUTES.put(
                        server.host(),
                        new Backend(server.remoteHost(), server.port()));
            }
        } catch (IOException e) {
            Command.out("error with ioException");
            return;
        }
    }

    private static void handleClient(Socket client) {
        try {
            packetAnl.Handshake handshake = packetAnl.readHandshake(client.getInputStream());

            Command.out(
                    "Handshake: host=" + handshake.host()
                            + ", port=" + handshake.port()
                            + ", protocol=" + handshake.protocolVersion()
                            + ", nextState=" + handshake.nextState());

            /*
             * ホスト名ルーティング
             */
            Backend backend = ROUTES.get(
                    handshake.host().toLowerCase(Locale.ROOT));

            if (backend == null) {
                Command.out(
                        "Unknown host: " + handshake.host());

                closeQuietly(client);
                return;
            }

            Command.out(
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
             * ハンドシェイク再構築
             */
            OutputStream serverOut = server.getOutputStream();

            serverOut.write(handshake.rawPacket());
            serverOut.flush();

            /*
             * Client -> Server
             */
            POOL.execute(() -> packetRelay.relay(
                    client,
                    server,
                    false));

            /*
             * Server -> Client
             */
            POOL.execute(() -> packetRelay.relay(
                    server,
                    client,
                    true));

        } catch (IOException e) {
            Command.out(
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
