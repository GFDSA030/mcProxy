package org.cf_t.mc;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class pluginS {

    private String pin = "0000";

    public void serverLoop(int port, String _pin) throws IOException {
        pin = _pin;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", infoHandler);
        System.out.println("infoHandler wakes up: port=" + port);
        server.start();
    }

    HttpHandler infoHandler = new HttpHandler() {
        @Override
        public void handle(HttpExchange he) throws IOException {
            Map<String, String> query = getQueryParams(he);

            if (query.get("pin") == null ? pin != null : !query.get("pin").equals(pin)) {

                he.sendResponseHeaders(200, 0);

                try (OutputStream os = he.getResponseBody()) {
                    os.close();
                }
                return;
            }

            App.PlayerInfo info = App.getPlayerInfo(query.get("uuid"));

            if (query.get("quit").equals("true")) {
                App.removePlayerInfo(info.uuid());
            }

            String json = """
            {
                "ip": "%s",
                "name": "%s",
                "uuid": "%s"
            }
        """.formatted(info.ip(), info.name(), info.uuid());

            byte[] response = json.getBytes(StandardCharsets.UTF_8);

            he.getResponseHeaders().set(
                    "Content-Type",
                    "application/json; charset=UTF-8"
            );

            he.sendResponseHeaders(200, response.length);

            try (OutputStream os = he.getResponseBody()) {
                os.write(response);
                os.close();
            }
        }
    };

    private static Map<String, String> getQueryParams(HttpExchange he) {
        Map<String, String> params = new HashMap<>();

        String query = he.getRequestURI().getRawQuery();

        if (query == null || query.isEmpty()) {
            return params;
        }

        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);

            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            String value = pair.length > 1
                    ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8)
                    : "";

            params.put(key, value);
        }

        return params;
    }
}
