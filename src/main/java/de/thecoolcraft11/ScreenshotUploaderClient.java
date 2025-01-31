package de.thecoolcraft11;


import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import de.thecoolcraft11.config.ConfigManager;
import de.thecoolcraft11.event.KeyInputHandler;
import de.thecoolcraft11.packet.AddressPayload;
import de.thecoolcraft11.packet.ScreenshotResponsePayload;
import de.thecoolcraft11.screen.WebGalleryScreen;
import de.thecoolcraft11.util.ReceivePackets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ScreenshotUploaderClient implements ClientModInitializer {
    private final Logger logger = LoggerFactory.getLogger(ScreenshotUploaderClient.class);

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(AddressPayload.ID, this::registerAddressReceiver);
        ClientPlayNetworking.registerGlobalReceiver(ScreenshotResponsePayload.ID, this::registerScreenshotReceiver);
        ClientPlayConnectionEvents.DISCONNECT.register((this::registerDisconnectEvent));
        ClientPlayConnectionEvents.JOIN.register(this::registerJoinEvent);
        createConfig();
        KeyInputHandler.register();
        ClientReceiveMessageEvents.CHAT.register(this::regsiterChatEvent);
        ClientCommandRegistrationCallback.EVENT.register(this::regsiterCommands);
    }

    private void createConfig() {
        File configDir = new File("config/screenshotUploader");
        if (!configDir.exists()) {
            if (configDir.mkdir()) {
                logger.info("Created Config Dir");
            }
        }
        ConfigManager.initialize(configDir, true);
    }

    private void registerScreenshotReceiver(ScreenshotResponsePayload payload, ClientPlayNetworking.Context context) {
        context.client().execute(() ->
                ReceivePackets.receiveScreenshotRes(JsonParser.parseString(payload.json()).getAsJsonObject(), context.client())
        );
    }

    private void registerAddressReceiver(AddressPayload payload, ClientPlayNetworking.Context context) {
        context.client().execute(() ->
                ReceivePackets.receiveAddress(context.client(), payload.message())
        );
    }

    private void regsiterCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess commandRegistryAccess) {
        dispatcher.register(ClientCommandManager.literal("open-gallery")
                .then(ClientCommandManager.argument("server", StringArgumentType.string())
                        .then(ClientCommandManager.argument("image", StringArgumentType.string())
                                .executes(context -> {
                                    final String value = StringArgumentType.getString(context, "server");
                                    final String value2 = StringArgumentType.getString(context, "image");

                                    MinecraftClient client = context.getSource().getClient();

                                    client.send(() -> client.setScreen(new WebGalleryScreen(null, value, value2)));

                                    return 1;
                                })
                        )
                )
        );
    }

    private void regsiterChatEvent(Text message, @Nullable SignedMessage signedMessage, @Nullable GameProfile gameProfile, MessageType.Parameters parameters, Instant instant) {
        MinecraftClient client = MinecraftClient.getInstance();

        boolean hasServerSaved = false;
        String serverName = "";

        if (extractUrl(message.getString()) != null) {
            try {
                if (ReceivePackets.homeSiteAddress != null) {
                    URI savedEntry = new URI(ReceivePackets.homeSiteAddress);
                    URI messageEntry = new URI(Objects.requireNonNull(extractUrl(message.getString())));

                    if (savedEntry.getHost().equals(messageEntry.getHost())) {
                        serverName = ReceivePackets.gallerySiteAddress;
                        hasServerSaved = true;
                    }
                }
            } catch (URISyntaxException ignored) {
            }

            if (!hasServerSaved) {
                for (Map<String, String> value : ConfigManager.getClientConfig().upload_urls.values()) {
                    if (!value.containsKey("home")) continue;
                    try {
                        URI savedEntry = new URI(value.get("home"));
                        URI messageEntry = new URI(Objects.requireNonNull(extractUrl(message.getString())));

                        if (savedEntry.getHost().equals(messageEntry.getHost())) {
                            serverName = value.get("gallery");
                            hasServerSaved = true;
                        }
                    } catch (URISyntaxException ignored) {
                    }
                }
            }
        }


        if (hasServerSaved) {
            String finalServerName = serverName;
            Text newMessage = Text.translatable("message.screenshot_uploader.shared_saved").styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/open-gallery \"" + finalServerName + "\" \"" + extractUrl(message.getString()) + "\"")).withUnderline(true).withColor(Formatting.AQUA));

            client.inGameHud.getChatHud().addMessage(newMessage);
        }
    }

    private void registerDisconnectEvent(ClientPlayNetworkHandler playNetworkHandler, MinecraftClient minecraftClient) {
        ReceivePackets.gallerySiteAddress = null;
        ReceivePackets.serverSiteAddress = null;
        ReceivePackets.homeSiteAddress = null;
    }

    private void registerJoinEvent(ClientPlayNetworkHandler playNetworkHandler, PacketSender packetSender, MinecraftClient minecraftClient) {

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

    private static String extractUrl(String message) {
        String urlPattern = "https?://[\\w.-]+(:\\d+)?(/[\\w.-]*)*";
        Pattern pattern = Pattern.compile(urlPattern);
        Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }
}
