package org.cf_t.mc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class pluginS {

    public void serverLoop(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new infoHandler());
        System.out.println("infoHandler wakes up: port=" + port);
        server.start();
    }

    private static class infoHandler implements HttpHandler {

        // HTTP リクエストを処理する
        @Override
        public void handle(HttpExchange t) throws IOException {

            System.out.println("**************************************************");

            // 開始行を取得
            String startLine
                    = t.getRequestMethod() + " "
                    + t.getRequestURI().toString() + " "
                    + t.getProtocol();
            System.out.println(startLine);

            // リクエストヘッダを取得
            Headers reqHeaders = t.getRequestHeaders();
            for (String name : reqHeaders.keySet()) {
                System.out.println(name + ": " + reqHeaders.getFirst(name));
            }

            // リクエストボディを取得
            InputStream is = t.getRequestBody();
            byte[] b = is.readAllBytes();
            is.close();
            if (b.length != 0) {
                System.out.println(); // 空行
                System.out.println(new String(b, StandardCharsets.UTF_8));
            }

            // レスポンスボディを構築
            // (ここでは Java 14 から正式導入された Switch Expressions と
            //  Java 14 でプレビュー機能として使えるヒアドキュメント的な Text Blocks 機能を使ってみる)
            String resBody = switch (t.getRequestURI().toString()) {
                case "/hello" ->
                    "{\"message\": \"Hello, World!\"}";
                case "/foobar" ->
                    """
          {
            "foo": "bar",
            "ふー": "ばー"
          }""";
                default ->
                    "{}";
            };

            // Content-Length 以外のレスポンスヘッダを設定
            Headers resHeaders = t.getResponseHeaders();
            resHeaders.set("Content-Type", "application/json");
            resHeaders.set("Last-Modified",
                    ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME));
            resHeaders.set("Server",
                    "MyServer ("
                    + System.getProperty("java.vm.name") + " "
                    + System.getProperty("java.vm.vendor") + " "
                    + System.getProperty("java.vm.version") + ")");

            // レスポンスヘッダを送信
            int statusCode = 200;
            long contentLength = resBody.getBytes(StandardCharsets.UTF_8).length;
            t.sendResponseHeaders(statusCode, contentLength);

            // レスポンスボディを送信
            OutputStream os = t.getResponseBody();
            os.write(resBody.getBytes());
            os.close();
        }
    }
}
