package org.cf_t.mc;

import java.util.ArrayList;

import com.google.gson.Gson;

public class Player {

    final static private Gson gson = new Gson();

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
}
