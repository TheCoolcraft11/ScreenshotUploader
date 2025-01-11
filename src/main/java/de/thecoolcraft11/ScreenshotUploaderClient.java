package de.thecoolcraft11;


import com.google.gson.JsonParser;
import de.thecoolcraft11.packet.AddressPayload;
import de.thecoolcraft11.packet.ScreenshotResponsePayload;
import de.thecoolcraft11.util.ReceivePackets;
import de.thecoolcraft11.util.config.ConfigManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.io.File;


public class ScreenshotUploaderClient implements ClientModInitializer {


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
        File configDir = new File("config/screenshotUploader");
        if(!configDir.exists()) {
            configDir.mkdir();
        }
        ConfigManager.initialize(configDir,true);
    }
}
