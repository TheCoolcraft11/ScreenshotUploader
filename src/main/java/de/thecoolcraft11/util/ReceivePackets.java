package de.thecoolcraft11.util;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;


public class ReceivePackets {
    public static String serverSiteAddress = null;

    public static void receiveAddress(MinecraftClient client, String message) {
        String uploadDir = message.equals("mcserver://this") ? "This Server"  : message;
        if(uploadDir.equals(message)) {
            client.inGameHud.getChatHud().addMessage(Text.literal("Next screenshots will be uploaded to ").append(Text.literal("[" + uploadDir + "]").styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, message))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click To Open Url"))).withColor(Formatting.AQUA))));
        }else {
            client.inGameHud.getChatHud().addMessage(Text.literal("Next screenshots will be uploaded to ").append(Text.literal( "[" + uploadDir + "]").styled(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Your Screenshots will be sent to this server"))).withColor(Formatting.AQUA))));
        }
        serverSiteAddress = message;
    }

    public static void receiveScreenshotRes(JsonObject responseBody, MinecraftClient client) {
        String statusMessage = responseBody.get("status").getAsString();
        if ("success".equals(statusMessage)) {

            String screenshotUrl = responseBody.has("url") && !responseBody.get("url").isJsonNull() ? responseBody.get("url").getAsString() : null;
            String galleryUrl = responseBody.has("gallery") && !responseBody.get("gallery").isJsonNull() ? responseBody.get("gallery").getAsString() : null;

            Text fullMessage = Text.literal("Screenshot uploaded successfully! ");


            if (screenshotUrl != null) {
                String linkText = "[OPEN]";
                Text clickableLink = Text.literal(linkText)
                        .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, screenshotUrl))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click To See Screenshot"))).withColor(Formatting.AQUA));
                fullMessage = fullMessage.copy().append(clickableLink);
            }

            if (galleryUrl != null) {
                String galleryText = "[ALL]";
                Text clickableLink2 = Text.literal(galleryText)
                        .styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, galleryUrl))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click To See All Screenshots"))).withColor(Formatting.YELLOW));
                fullMessage = fullMessage.copy().append(" ").append(clickableLink2);
            }

            if (screenshotUrl == null && galleryUrl == null) {
                fullMessage = Text.literal("Screenshot upload failed: The server did not return valid URLs.");
            }

            client.inGameHud.getChatHud().addMessage(fullMessage);
        } else {
            String errorMessage = responseBody.has("message") ? responseBody.get("message").getAsString() : "Unknown error";
            Text errorText = Text.literal("Screenshot upload failed: " + errorMessage);


            client.inGameHud.getChatHud().addMessage(errorText);
        }
    }
}
