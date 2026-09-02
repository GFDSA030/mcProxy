package org.cf_t.mc;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;

public class Player {

    final static private Gson gson = new Gson();

    public static final Map<String, PlayerInfo> PlayerTable = new ConcurrentHashMap<>();
    public static ArrayList<String> bannedPlayer = new ArrayList<>();
    public static ArrayList<String> bannedIP = new ArrayList<>();

    public static void load() {

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
