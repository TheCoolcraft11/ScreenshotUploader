package de.thecoolcraft11.util.config;

import java.util.ArrayList;
import java.util.List;

public class ClientConfig {
    public boolean enableMod = true;
    public boolean uploadScreenshotsToUrl = true;
    public List<String> upload_urls = new ArrayList<>();
    public boolean requireNoHud = true;
    public boolean limitToServer = false;
    public String limitedServerAddr = "some-fake-minecraft-server-ip.com";

    public ClientConfig() {
        upload_urls.add("https://www.this-should-be-long-enough-so-this-is-a-fake-address.com/upload");
    }
}

