package de.thecoolcraft11.util;

import com.google.gson.JsonObject;
import de.thecoolcraft11.packet.ScreenshotPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.texture.NativeImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ScreenshotUploadHelper {

    private static final Logger logger = LoggerFactory.getLogger(ScreenshotUploadHelper.class);

    public static List<JsonObject> uploadScreenshot(NativeImage nativeImage, String jsonData, List<String> uploadUrls) {
        File tempFile;
        List<JsonObject> resultList = new ArrayList<>();

        try {
            tempFile = File.createTempFile("screenshot", ".png");
            nativeImage.writeTo(tempFile);
        } catch (IOException e) {
            JsonObject result = new JsonObject();
            logger.error("Failed to create temporary file for screenshot", e);
            result.addProperty("status", "error");
            result.addProperty("message", "Failed to create temporary file.");
            resultList.add(result);
            return resultList;
        }


        for (String uploadUrl : uploadUrls) {
            if (uploadUrl.contains("mcserver://this")) {
                sendScreenshotPacket(tempFile, jsonData);
            } else {
                JsonObject result = uploadToUrl(tempFile, jsonData, uploadUrl.equals("true") ? "http://localhost:4567/upload" : uploadUrl);
                resultList.add(result);
            }

        }

        if (tempFile.exists() && !tempFile.delete()) {
            logger.warn("Failed to delete temporary file: {}", tempFile.getAbsolutePath());
        }

        return resultList;
    }

    private static JsonObject uploadToUrl(File tempFile, String jsonData, String uploadUrl) {
        JsonObject result = new JsonObject();

        try {
            String boundary = Long.toHexString(System.currentTimeMillis());
            String CRLF = "\r\n";

            URI uploadUri = URI.create(uploadUrl);
            HttpURLConnection conn = (HttpURLConnection) uploadUri.toURL().openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            try (OutputStream os = conn.getOutputStream();
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {

                writer.append(String.format("--%s", boundary)).append(CRLF);
                writer.append(String.format("Content-Disposition: form-data; name=\"file\"; filename=\"%s\"", tempFile.getName())).append(CRLF);
                writer.append(String.format("Content-Type: %s", HttpURLConnection.guessContentTypeFromName(tempFile.getName()))).append(CRLF);
                writer.append(CRLF).flush();

                try (FileInputStream fis = new FileInputStream(tempFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                }

                os.flush();
                writer.append(CRLF).flush();

                writer.append(String.format("--%s", boundary)).append(CRLF);
                writer.append("Content-Disposition: form-data; name=\"jsonData\"").append(CRLF);
                writer.append("Content-Type: application/json").append(CRLF);
                writer.append(CRLF).flush();

                writer.append(jsonData).flush();
                writer.append(CRLF).flush();

                writer.append(String.format("--%s--", boundary)).append(CRLF).flush();
            }

            int responseCode = conn.getResponseCode();
            String responseMessage = conn.getResponseMessage();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream responseStream = conn.getInputStream();
                String responseBody = new BufferedReader(new InputStreamReader(responseStream))
                        .lines()
                        .reduce("", (acc, line) -> acc + line);

                result.addProperty("status", "success");
                result.addProperty("responseCode", responseCode);
                result.addProperty("responseBody", responseBody);
            } else {
                result.addProperty("status", "error");
                result.addProperty("responseCode", responseCode);
                result.addProperty("message", responseMessage);
            }

        } catch (IOException e) {
            logger.error("IOException occurred while uploading screenshot to {}: {}", uploadUrl, e.getMessage());
            result.addProperty("status", "error");
            result.addProperty("message", e.getMessage());
        }

        return result;
    }

    public static void sendScreenshotPacket(File tempFile, String json) {
        try {
            BufferedImage originalImage = ImageIO.read(tempFile);

            if (originalImage.getColorModel().hasAlpha()) {
                BufferedImage rgbImage = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = rgbImage.createGraphics();
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, originalImage.getWidth(), originalImage.getHeight());
                graphics.drawImage(originalImage, 0, 0, null);
                graphics.dispose();
                originalImage = rgbImage;
            }

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.7f);

            writer.setOutput(ImageIO.createImageOutputStream(byteArrayOutputStream));
            writer.write(null, new javax.imageio.IIOImage(originalImage, null, null), param);
            writer.dispose();

            byte[] compressedImageBytes = byteArrayOutputStream.toByteArray();

            // Determine if we should use chunking based on packet size
            boolean useChunking = compressedImageBytes.length > 1;//800000; // 800KB threshold

            if (useChunking) {
                sendChunkedScreenshotPacket(compressedImageBytes, json);
            } else {
                // Legacy mode for Fabric servers
                ClientPlayNetworking.send(new ScreenshotPayload(compressedImageBytes, json));
            }

            logger.info("Screenshot sent to server: {} bytes using {} mode",
                    compressedImageBytes.length, useChunking ? "chunked" : "legacy");

            if (!tempFile.delete()) {
                logger.warn("Failed to delete temporary file while sending packet: {}", tempFile.getAbsolutePath());
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to compress and send screenshot", e);
        }
    }

    private static void sendChunkedScreenshotPacket(byte[] imageBytes, String json) {
        // Use a maximum chunk size of 25KB to be safe
        final int MAX_CHUNK_SIZE = 25000;
        String transferId = UUID.randomUUID().toString();

        // Calculate how many chunks we need
        int totalChunks = (int) Math.ceil((double) imageBytes.length / MAX_CHUNK_SIZE);

        // Send initial packet with transfer ID, totalChunks and JSON metadata
        ClientPlayNetworking.send(new ScreenshotPayload(transferId, totalChunks, json));
        logger.debug("Sent init packet for transfer {}: {} chunks", transferId, totalChunks);

        // Send each chunk
        for (int i = 0; i < totalChunks; i++) {
            int start = i * MAX_CHUNK_SIZE;
            int length = Math.min(MAX_CHUNK_SIZE, imageBytes.length - start);

            byte[] chunk = new byte[length];
            System.arraycopy(imageBytes, start, chunk, 0, length);

            ClientPlayNetworking.send(new ScreenshotPayload(transferId, totalChunks, i, chunk));
            logger.info("Sent chunk {}/{} of transfer {}: {} bytes",
                    i + 1, totalChunks, transferId, length);

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Send final packet to indicate completion
        ClientPlayNetworking.send(new ScreenshotPayload(transferId));
        logger.debug("Sent final packet for transfer {}", transferId);
    }

}
