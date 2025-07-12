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

    @Comment("config.screenshot_uploader.solarize_amount")
    public int solarizeThreshold = 128;

    @Comment("config.screenshot_uploader.preserve_image_aspect_ratio")
    public boolean preserveImageAspectRatio = true;

    @Comment("config.screenshot_uploader.image_color_r")
    public int imageColorR = 255;

    @Comment("config.screenshot_uploader.image_color_g")
    public int imageColorG = 255;

    @Comment("config.screenshot_uploader.image_color_b")
    public int imageColorB = 255;

    @Comment("config.screenshot_uploader.image_color_a")
    public int imageColorA = 255;

    @Comment("config.screenshot_uploader.regular_sign_vertical_offset")
    public float regularSignVerticalOffset = -2.0f;

    @Comment("config.screenshot_uploader.hanging_sign_vertical_offset")
    public float hangingSignVerticalOffset = 1.2f;

    @Comment("config.screenshot_uploader.image_z_offset")
    public float imageZOffset = -0.01f;

    @Comment("config.screenshot_uploader.regular_sign_image_width")
    public float regularSignImageWidth = 3.8f;

    @Comment("config.screenshot_uploader.regular_sign_image_height")
    public float regularSignImageHeight = 3.0f;

    @Comment("config.screenshot_uploader.hanging_sign_image_width")
    public float hangingSignImageWidth = 0.85f;

    @Comment("config.screenshot_uploader.hanging_sign_image_height")
    public float hangingSignImageHeight = 0.55f;

    @Comment("config.screenshot_uploader.image_light_boost")
    public int imageLightBoost = 50;

    @Comment("config.screenshot_uploader.enable_screenshot_rendering")
    public boolean enableScreenshotRendering = false;

    @Comment("config.screenshot_uploader.item_vertical_offset")
    public float itemVerticalOffset = 2.0f;

    @Comment("config.screenshot_uploader.item_horizontal_offset")
    public float itemHorizontalOffset = 0.0f;

    @Comment("config.screenshot_uploader.item_depth_offset")
    public float itemDepthOffset = 0.0f;

    @Comment("config.screenshot_uploader.item_scale")
    public float itemScale = 0.66f;

    @Comment("config.screenshot_uploader.contrast_multiplier")
    public float contrastMultiplier = 1.2f;

    @Comment("config.screenshot_uploader.brightness_adjustment")
    public int brightnessAdjustment = 20;

    @Comment("config.screenshot_uploader.blur_kernel_size")
    public int blurKernelSize = 10;

    @Comment("config.screenshot_uploader.blur_iterations")
    public int blurIterations = 10;

    @Comment("config.screenshot_uploader.posterize_levels")
    public int posterizeLevels = 5;

    @Comment("config.screenshot_uploader.hue_shift_amount")
    public float hueShiftAmount = 0.2f;

    @Comment("config.screenshot_uploader.vignette_intensity")
    public float vignetteIntensity = 1.0f;

    @Comment("config.screenshot_uploader.emboss_effect")
    public int embossEffect = 128;

    @Comment("config.screenshot_uploader.noise_intensity")
    public int noiseIntensity = 10;

    @Comment("config.screenshot_uploader.gallery_button_main_menu")
    public boolean addGalleryButtonToMainMenu = true;

    @Comment("config.screenshot_uploader.gallery_button_pause_menu")
    public boolean addGalleryButtonToPauseMenu = true;


    public ClientConfig() {
        Map<String, String> map = new HashMap<>();
        map.put("upload", "https://www.this-should-be-long-enough-so-this-is-a-fake-address.com/upload");
        map.put("gallery", "https://www.this-should-be-long-enough-so-this-is-a-fake-address.com/screenshot-list");
        map.put("home", "https://www.this-should-be-long-enough-so-this-is-a-fake-address.com/");
        upload_urls.put("Server Example", map);
    }
}
