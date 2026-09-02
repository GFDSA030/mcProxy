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
            String json;
            json = Files.readString(Path.of("ban.json"));
            PlayerInfo[] banList = gson.fromJson(json, PlayerInfo[].class);
            for (int i = 0; i < banList.length; i++) {
                if (banList[i].ip() != "")
                    bannedIP.add(banList[i].ip());
                if (banList[i].name() != "")
                    bannedPlayer.add(banList[i].name());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void banPlayer(String name) {

    }

    public static void banIP(String ip) {

    }

    public static void deBanPlayer(String name) {

    }

    public static void deBanIP(String ip) {

    }

    public static boolean checkPlayer(String name) {
        return false;
    }

    public static boolean checkIP(String ip) {
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
