package de.thecoolcraft11;

import com.google.gson.JsonObject;
import net.minecraft.client.texture.NativeImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static de.thecoolcraft11.ScreenshotUploader.getConfig;


public class ScreenshotUpload {

    private static final Logger logger = LoggerFactory.getLogger(ScreenshotUpload.class);
    private static final JsonObject config = getConfig();

    public static JsonObject uploadScreenshot(NativeImage nativeImage, String jsonData) {
        File tempFile;
        JsonObject result = new JsonObject();

        try {
            tempFile = File.createTempFile("screenshot", ".png");
            nativeImage.writeTo(tempFile);
        } catch (IOException e) {
            logger.error("Failed to create temporary file for screenshot", e);
            result.addProperty("status", "error");
            result.addProperty("message", "Failed to create temporary file.");
            return result;
        }

        try {
            String boundary = Long.toHexString(System.currentTimeMillis());
            String CRLF = "\r\n";

            URI uploadUri = URI.create(config.get("upload_url").getAsString());
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

                // End of the request
                writer.append(String.format("--%s--", boundary)).append(CRLF).flush();
            }

            // Handle response
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
            logger.error("IOException occurred while uploading screenshot", e);
            result.addProperty("status", "error");
            result.addProperty("message", e.getMessage());
        } finally {
            if (tempFile.exists() && !tempFile.delete()) {
                logger.warn("Failed to delete temporary file: {}", tempFile.getAbsolutePath());
            }
        }

        return result;
    }
}
