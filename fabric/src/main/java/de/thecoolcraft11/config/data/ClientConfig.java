package de.thecoolcraft11.config.data;

import de.thecoolcraft11.config.value.Comment;
import net.minecraft.item.Items;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;


public class ClientConfig {
    @Comment("config.screenshot_uploader.enable_mod")
    public boolean enableMod = true;

    @Comment("config.screenshot_uploader.upload_screenshots_to_url")
    public boolean uploadScreenshotsToUrl = true;

    @Comment("config.screenshot_uploader.upload_urls")
    public Map<String, Map<String, String>> upload_urls = new LinkedHashMap<>();

    @Comment("config.screenshot_uploader.require_no_hud")
    public boolean requireNoHud = true;

    @Comment("config.screenshot_uploader.limit_to_server")
    public boolean limitToServer = false;

    @Comment("config.screenshot_uploader.limited_server_addr")
    public String limitedServerAddr = "some-fake-minecraft-server-ip.com";

    @Comment("config.screenshot_uploader.send_world_data")
    public boolean sendWorldData = true;

    @Comment("config.screenshot_uploader.send_system_info")
    public boolean sendSystemInfo = true;

    @Comment("config.screenshot_uploader.share_text")
    public String shareText = "Look at this screenshot: {sharedLink}";

    @Comment("config.screenshot_uploader.images_per_row")
    public int imagesPerRow = 5;

    @Comment("config.screenshot_uploader.image_gap")
    public int imageGap = 10;

    @Comment("config.screenshot_uploader.image_top_padding")
    public int imageTopPadding = 35;

    @Comment("config.screenshot_uploader.edit_image_file_path")
    public String editImageFilePath = "{fileName}_edited.png";

    @Comment("config.screenshot_uploader.highlight_screenshot_signs")
    public boolean highlightScreenshotSigns = true;

    @Comment("config.screenshot_uploader.hide_sign")
    public boolean hideSign = false;

    @Comment("config.screenshot_uploader.use_custom_sign")
    public boolean useCustomSign = false;

    @Comment("config.screenshot_uploader.highlight_oscillation")
    public boolean highlightOscillation = true;

    @Comment("config.screenshot_uploader.rotate_highlight_sign")
    public boolean rotateHighlightSign = false;

    @Comment("config.screenshot_uploader.rotate_highlight_item")
    public boolean rotateHighlightItem = true;

    @Comment("config.screenshot_uploader.highlight_item")
    public String highlightItem = Items.PAINTING.toString();

    @Comment("config.screenshot_uploader.highlight_color_a")
    public float highlightColorA = 0.75f;

    @Comment("config.screenshot_uploader.highlight_color_r")
    public int highlightColorR = 255;

    @Comment("config.screenshot_uploader.highlight_color_g")
    public int highlightColorG = 127;

    @Comment("config.screenshot_uploader.highlight_color_b")
    public int highlightColorB = 0;

    @Comment("config.screenshot_uploader.highlight_rotation_speed")
    public float highlightRotationSpeed = 0.55f;

    @Comment("config.screenshot_uploader.delete_old_screenshots")
    public boolean deleteOldScreenshots = false;
    @Comment("config.screenshot_uploader.delete_after_days")
    public int deleteAfterDays = 30;
    @Comment("config.screenshot_uploader.save_json_data")
    public boolean saveJsonData = false;
    @Comment("config.screenshot_uploader.ask_before_upload")
    public boolean askBeforeUpload = false;

    public ClientConfig() {
        Map<String, String> map = new HashMap<>();
        map.put("upload", "https://www.this-should-be-long-enough-so-this-is-a-fake-address.com/upload");
        map.put("gallery", "https://www.this-should-be-long-enough-so-this-is-a-fake-address.com/screenshot-list");
        map.put("home", "https://www.this-should-be-long-enough-so-this-is-a-fake-address.com/");
        upload_urls.put("Server Example", map);
    }
}

