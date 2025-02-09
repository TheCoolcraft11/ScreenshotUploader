package de.thecoolcraft11.config.data;

import net.minecraft.item.Items;

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
    public String shareText = "Look at this screenshot: {sharedLink}";
    public int imagesPerRow = 5;
    public int imageGap = 10;
    public int imageTopPadding = 35;
    public String editImageFilePath = "{fileName}_edited";
    public boolean highlightScreenshotSigns = true;
    public boolean hideSign = false;
    public boolean highlightOscillation = true;
    public boolean rotateHighlightSign = false;
    public boolean rotateHighlightItem = true;
    public String highlightItem = Items.PAINTING.toString();
    public float highlightColorA = 0.75f;
    public int highlightColorR = 255;
    public int highlightColorG = 127;
    public int highlightColorB = 0;
    public float highlightRotationSpeed = 0.55f;


    public ClientConfig() {
        Map<String, String> map = new HashMap<>();
        map.put("upload", "https://www.this-should-be-long-enough-so-this-is-a-fake-address.com/upload");
        map.put("gallery", "https://www.this-should-be-long-enough-so-this-is-a-fake-address.com/screenshot-list");
        map.put("home", "https://www.this-should-be-long-enough-so-this-is-a-fake-address.com/");
        upload_urls.put("Server Example", map);
    }
}

