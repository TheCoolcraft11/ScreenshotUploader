package de.thecoolcraft11;

import com.google.gson.JsonObject;
import net.minecraft.client.texture.NativeImage;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static de.thecoolcraft11.ScreenshotUploader.getConfig;

public class ScreenshotUpload {

    private static final JsonObject config = getConfig();

    public static void uploadScreenshot(NativeImage nativeImage, String jsonData) {

        File tempFile;
        try {
            tempFile = File.createTempFile("screenshot", ".png");
            nativeImage.writeTo(tempFile);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        try {
            String boundary = Long.toHexString(System.currentTimeMillis());
            String CRLF = "\r\n";

            HttpURLConnection conn = (HttpURLConnection) new URL(config.get("upload_url").getAsString()).openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            try (OutputStream os = conn.getOutputStream();
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {

                writer.append("--" + boundary).append(CRLF);
                writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"" + tempFile.getName() + "\"").append(CRLF);
                writer.append("Content-Type: " + HttpURLConnection.guessContentTypeFromName(tempFile.getName())).append(CRLF);
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


                writer.append("--" + boundary).append(CRLF);
                writer.append("Content-Disposition: form-data; name=\"jsonData\"").append(CRLF);
                writer.append("Content-Type: application/json").append(CRLF);
                writer.append(CRLF).flush();

                writer.append(jsonData).flush();
                writer.append(CRLF).flush();

                writer.append("--" + boundary + "--").append(CRLF).flush();
            }

            int responseCode = conn.getResponseCode();
            String responseMessage = conn.getResponseMessage();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                System.out.println("Screenshot uploaded successfully.");
            } else {
                System.out.println("Failed to upload screenshot. Response code: " + responseCode + ": " + responseMessage);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
}
