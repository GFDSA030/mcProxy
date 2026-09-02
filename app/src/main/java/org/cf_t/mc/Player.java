package org.cf_t.mc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;

public class Player {

    final static private Gson gson = new Gson();

    public static final Map<String, PlayerInfo> PlayerTable = new ConcurrentHashMap<>();
    private static ArrayList<String> bannedPlayer = new ArrayList<>();
    private static ArrayList<String> bannedIP = new ArrayList<>();

    public static void load() {
        try {
            do {
                if (!Files.exists(Path.of("banP.json")))
                    break;

                String json;
                json = Files.readString(Path.of("banP.json"));
                String[] banList = gson.fromJson(json, String[].class);
                for (int i = 0; i < banList.length; i++) {
                    bannedPlayer.add(banList[i]);
                }
            } while (false);
            do {
                if (!Files.exists(Path.of("banI.json")))
                    break;

                String json;
                json = Files.readString(Path.of("banI.json"));
                String[] banList = gson.fromJson(json, String[].class);
                for (int i = 0; i < banList.length; i++) {
                    bannedIP.add(banList[i]);
                }
            } while (false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void banPlayer(String name) {
        bannedPlayer.add(name);
        try {
            Files.write(Path.of("bunP.json"), gson.toJson(bannedPlayer).getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void banIP(String ip) {
        bannedIP.add(ip);
        try {
            Files.write(Path.of("bunI.json"), gson.toJson(bannedIP).getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void deBanPlayer(String name) {
        bannedPlayer.remove(bannedPlayer.indexOf(name));
        try {
            Files.write(Path.of("bunP.json"), gson.toJson(bannedPlayer).getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void deBanIP(String ip) {
        bannedIP.remove(bannedIP.indexOf(ip));
        try {
            Files.write(Path.of("bunI.json"), gson.toJson(bannedIP).getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static ArrayList<String> getBanPlayer() {
        return bannedPlayer;
    }

    public static ArrayList<String> getBanIP() {
        return bannedIP;
    }

    public static boolean checkPlayer(String name) {
        if (bannedPlayer.contains(name)) {
            return true;
        }
        return false;
    }

    public static boolean checkIP(String ip) {
        if (bannedIP.contains(ip)) {
            return true;
        }
        return false;
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

    public record PlayerInfo(
            String name,
            String uuid,
            String ip) {

    }

}
