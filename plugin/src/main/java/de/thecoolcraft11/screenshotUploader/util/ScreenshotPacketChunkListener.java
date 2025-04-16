package de.thecoolcraft11.screenshotUploader.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ScreenshotPacketChunkListener implements PluginMessageListener {
    private static final Logger logger = LoggerFactory.getLogger(ScreenshotPacketChunkListener.class);

    private final Map<String, TransferData> activeTransfers = new ConcurrentHashMap<>();

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, @NotNull byte[] message) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            // Read packet type
            String type = readString(in);
            String transferId = readString(in);
            int totalChunks = readVarInt(in);
            int chunkIndex = readVarInt(in);

            switch (type) {
                case "LEGACY" -> {
                    byte[] imageData = readByteArray(in);
                    String jsonData = readString(in);
                    ReceiveScreenshotPacket.handleReceivedScreenshot(imageData, jsonData, player);
                    logger.info("Processed legacy screenshot from player {}", player.getName());
                }
                case "INIT" -> {
                    String jsonData = readString(in);
                    if (jsonData.isEmpty()) {
                        jsonData = "{}"; //TODO Fix JSON not being read correctly
                        logger.warn("Empty JSON data received in INIT packet, using empty object");
                    }
                    activeTransfers.put(transferId, new TransferData(player, totalChunks, jsonData));
                    logger.info("Started new transfer {} from player {}: {} chunks expected",
                            transferId, player.getName(), totalChunks);
                }
                case "CHUNK" -> {
                    TransferData transferData = activeTransfers.get(transferId);
                    if (transferData == null) {
                        logger.warn("Received chunk for unknown transfer {}", transferId);
                        return;
                    }

                    byte[] chunkData = readByteArray(in);
                    transferData.addChunk(chunkIndex, chunkData);
                    logger.debug("Received chunk {}/{} for transfer {}",
                            chunkIndex + 1, totalChunks, transferId);
                }
                case "FINAL" -> {
                    TransferData transferData = activeTransfers.remove(transferId);
                    if (transferData == null) {
                        logger.warn("Received FINAL for unknown transfer {}", transferId);
                        return;
                    }

                    if (transferData.isComplete()) {
                        byte[] completeImageData = transferData.assembleImage();
                        String jsonData = transferData.jsonData;

                        logger.info("Assembled image data size: {} bytes", completeImageData.length);
                        if (jsonData == null || jsonData.isEmpty()) {
                            jsonData = "{}";
                            logger.warn("Missing JSON data for transfer {}, using empty object", transferId);
                        }
                        if (completeImageData.length == 0) {
                            logger.error("Assembled image data is empty for transfer {}", transferId);
                            return;
                        }

                        try {
                            File screenshotsDir = new File("./screenshotUploader/screenshots/");
                            if (!screenshotsDir.exists()) {
                                screenshotsDir.mkdirs();
                            }

                            File debugFile = new File(screenshotsDir, "screenshot_" + player + "_" + transferId.trim() + ".png");
                            try (FileOutputStream fos = new FileOutputStream(debugFile)) {
                                fos.write(completeImageData);
                            }
                        } catch (IOException e) {
                            logger.error("Failed to save debug image", e);
                        }

                        ReceiveScreenshotPacket.handleReceivedScreenshot(
                                completeImageData, jsonData, transferData.player);

                        logger.info("Completed transfer {} from player {}: {} chunks assembled",
                                transferId, player.getName(), transferData.receivedChunks());
                    } else {
                        logger.warn("Transfer {} marked as complete but only {}/{} chunks received",
                                transferId, transferData.receivedChunks(), transferData.totalChunks);
                    }
                }
                default -> logger.warn("Unknown packet type: {}", type);
            }
        } catch (IOException e) {
            logger.error("Error processing screenshot packet", e);
        }
    }

    private String readString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int position = 0;
        byte currentByte;

        do {
            currentByte = in.readByte();
            value |= (currentByte & 0x7F) << position;
            position += 7;

            if (position >= 32) {
                throw new RuntimeException("VarInt too big");
            }
        } while ((currentByte & 0x80) != 0);

        return value;
    }

    private byte[] readByteArray(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return bytes;
    }

    private static class TransferData {
        private final Player player;
        private final int totalChunks;
        private final String jsonData;
        private final byte[][] chunks;
        private int receivedChunksCount = 0;

        public TransferData(Player player, int totalChunks, String jsonData) {
            this.player = player;
            this.totalChunks = totalChunks;
            this.jsonData = jsonData;
            this.chunks = new byte[totalChunks][];
        }

        public void addChunk(int index, byte[] data) {
            if (chunks[index] == null) {
                chunks[index] = data;
                receivedChunksCount++;
            }
        }

        public boolean isComplete() {
            return receivedChunksCount == totalChunks;
        }

        public int receivedChunks() {
            return receivedChunksCount;
        }

        public byte[] assembleImage() {
            int totalSize = 0;
            for (byte[] chunk : chunks) {
                if (chunk != null) {
                    totalSize += chunk.length;
                }
            }

            byte[] result = new byte[totalSize];
            int position = 0;
            for (byte[] chunk : chunks) {
                if (chunk != null) {
                    System.arraycopy(chunk, 0, result, position, chunk.length);
                    position += chunk.length;
                }
            }

            return result;
        }
    }
}