package de.thecoolcraft11;


import com.google.gson.JsonParser;
import de.thecoolcraft11.config.ConfigManager;
import de.thecoolcraft11.event.KeyInputHandler;
import de.thecoolcraft11.packet.AddressPayload;
import de.thecoolcraft11.packet.ScreenshotResponsePayload;
import de.thecoolcraft11.util.ReceivePackets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;


public class ScreenshotUploaderClient implements ClientModInitializer {
    private final Logger logger = LoggerFactory.getLogger(ScreenshotUploaderClient.class);

    @Override
    public void onInitializeClient() {

        ClientPlayNetworking.registerGlobalReceiver(AddressPayload.ID, (payload, context) ->
                context.client().execute(() ->
                        ReceivePackets.receiveAddress(context.client(), payload.message())
                )
        );

        ClientPlayNetworking.registerGlobalReceiver(ScreenshotResponsePayload.ID, (payload, context) ->
                context.client().execute(() ->
                        ReceivePackets.receiveScreenshotRes(JsonParser.parseString(payload.json()).getAsJsonObject(), context.client())
                )
        );
        ClientPlayConnectionEvents.DISCONNECT.register((playNetworkHandler, minecraftClient) -> {
            ReceivePackets.gallerySiteAddress = null;
            ReceivePackets.serverSiteAddress = null;
            ReceivePackets.homeSiteAddress = null;
        });
        ClientPlayConnectionEvents.JOIN.register((playNetworkHandler, packetSender, minecraftClient) -> {

            Text uploadMessageString = Text.empty();

            Map<String, Map<String, String>> uploadUrls = getStringMapMap();

            int index = 0;
            int totalKeys = uploadUrls.size();
            for (String key : uploadUrls.keySet()) {
                uploadMessageString = uploadMessageString.copy().append(Text.literal(key).styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, uploadUrls.get(key).get("home")))
                        .withColor(Formatting.AQUA)
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable("message.screenshot_uploader.next_upload_server_description", Text.literal(key).styled(style2 -> style2.withColor(Formatting.AQUA)), uploadUrls.get(key).get("upload"), uploadUrls.get(key).get("home"), Text.keybind("key.screenshot_uploader.gallery"))))));
                if (++index < totalKeys) {
                    uploadMessageString = uploadMessageString.copy().append(Text.literal(", ").styled(style -> style.withColor(Formatting.AQUA)));
                }
            }
            minecraftClient.inGameHud.getChatHud().addMessage(Text.translatable("message.screenshot_uploader.next_upload", uploadMessageString));
        });

        File configDir = new File("config/screenshotUploader");
        if (!configDir.exists()) {
            if (configDir.mkdir()) {
                logger.info("Created Config Dir");
            }
        }
        ConfigManager.initialize(configDir, true);

        KeyInputHandler.register();
    }

    private static Map<String, Map<String, String>> getStringMapMap() {
        Map<String, Map<String, String>> uploadUrls = ConfigManager.getClientConfig().upload_urls;
        if (ReceivePackets.gallerySiteAddress != null) {
            Map<String, String> thisServerMap = new HashMap<>();
            thisServerMap.put("home", ReceivePackets.homeSiteAddress);
            thisServerMap.put("upload", ReceivePackets.serverSiteAddress);
            thisServerMap.put("gallery", ReceivePackets.gallerySiteAddress);
            uploadUrls.put("This Server", thisServerMap);
        }
        return uploadUrls;
    }
}
