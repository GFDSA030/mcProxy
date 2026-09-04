package org.cf_t.mc;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class packetRelay {

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
     * TCPストリームをそのまま転送。
     */
    public static void relay(
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

            if (packetLength < 0 || packetLength > 2 * 1024 * 1024) {
                throw new IOException(
                        "Invalid packet length: " + packetLength);
            }

            /*
             * Packet Data
             */
            byte[] packetData = packetAnl.readFully(in, packetLength);

            /*
             * 元のパケットをそのまま転送
             */
            packetAnl.writeVarInt(out, packetLength);
            out.write(packetData);
            out.flush();

            /*
             * Packet IDを確認
             */
            ByteArrayInputStream packetIn = new ByteArrayInputStream(packetData);

            int packetId = packetAnl.readVarInt(packetIn);

            System.out.println(
                    "C->S packet: id=0x"
                            + Integer.toHexString(packetId)
                            + " length="
                            + packetLength);

            /*
             * Login Start
             *
             * Login状態の最初のPacket ID 0x00
             */
            if (packetId == 0) {
                try {

                    String uuid = packetAnl.parseLoginStart(
                            packetIn,
                            clientSocket);

                    if (Player.checkUUID(uuid)) {
                        System.out.println("banned player connect:" + Player.getPlayerInfo(uuid).name());
                        break;
                    }

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

                /*
                 * Login Start以降は解析しない。
                 *
                 * Encryption Response以降、
                 * Client -> Serverは暗号化されるため、
                 * Minecraftパケットとして解析すると壊れる。
                 *
                 * ここから完全なTCPリレーに戻す。
                 */
                relayRaw(in, out);

                return;
            }
        }
    }
}
