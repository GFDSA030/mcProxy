package org.cf_t.mc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;

public class Setting {

    final private Gson gson = new Gson();

    public record SvConfig(
            String host,
            String remoteHost,
            int port
            ) {

    }

    public record Config(
            String settingVer,
            int serverPort,
            int infoPort,
            SvConfig[] routings) {

    }

    public Config load(String path) throws IOException {
        String json = Files.readString(Path.of(path));
        Config config = gson.fromJson(json, Config.class);
        return config;
    }

}
