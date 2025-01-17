package de.thecoolcraft11.config.data;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ClientConfig {
    public boolean enableMod = true;
    public boolean uploadScreenshotsToUrl = true;
    public Map<String, Map<String, String>> upload_urls = new LinkedHashMap<>();
    public boolean requireNoHud = true;
    public boolean limitToServer = false;
    public String limitedServerAddr = "some-fake-minecraft-server-ip.com";
    public boolean sendWorldData = true;
    public boolean sendSystemInfo = true;

    public ClientConfig() {
        Map<String, String> map = new HashMap<>();
        map.put("upload", "https://www.this-should-be-long-enough-so-this-is-a-fake-address.com/upload");
        map.put("gallery", "https://www.this-should-be-long-enough-so-this-is-a-fake-address.com/screenshot-list");
        map.put("home", "https://www.this-should-be-long-enough-so-this-is-a-fake-address.com/");
        upload_urls.put("Server Example", map);
    }
}

