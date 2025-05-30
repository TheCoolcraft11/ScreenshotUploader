package de.thecoolcraft11.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import de.thecoolcraft11.config.ConfigManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebServer {

    public static void startWebServer(String ipAddress, int port, String urlString) throws Exception {


        HttpServer server = HttpServer.create(new InetSocketAddress(ipAddress, port), 0);

        server.createContext("/", new RootHandler());
        server.createContext("/random-screenshot", new RandomScreenshotHandler());
        server.createContext("/delete", new DeleteFileHandler());
        server.createContext("/static", new StaticFileHandler());
        server.createContext("/screenshots", new ScreenshotFileHandler());
        server.createContext("/screenshot-list", new ScreenshotListHandler(urlString));
        server.createContext("/comments", new GetCommentsHandler());
        server.createContext("/statistics", new StatisticsHandler());

        server.start();
    }

    private static class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            File dir = new File("./screenshotUploader/screenshots/");
            File[] files = dir.listFiles((dir1, name) -> name.endsWith(".jpg") || name.endsWith(".png"));
            String response = GalleryBuilder.buildGallery(files, ConfigManager.getServerConfig().allowDelete, ConfigManager.getServerConfig().deletionPassphrase.isEmpty());
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }


    private static class RandomScreenshotHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            File screenshotsDir = new File("screenshotUploader");
            if (!screenshotsDir.exists() || !screenshotsDir.isDirectory()) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            File[] files = screenshotsDir.listFiles();
            if (files == null || files.length == 0) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            Random random = new Random();
            File randomFile = files[random.nextInt(files.length)];
            String response = "{ \"filename\": \"" + randomFile.getName() + "\" }";

            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    private static class DeleteFileHandler implements HttpHandler {
        private static final Pattern DELETE_PATTERN = Pattern.compile("/delete/([^/]+)");

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"DELETE".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            if (!ConfigManager.getServerConfig().allowDelete) {
                exchange.sendResponseHeaders(403, -1);
                return;
            }

            String providedPassphrase = exchange.getRequestHeaders().getFirst("X-Delete-Passphrase");
            String configuredPassphrase = ConfigManager.getServerConfig().deletionPassphrase;

            if (configuredPassphrase != null && !configuredPassphrase.isEmpty() &&
                    (providedPassphrase == null || !providedPassphrase.equals(configuredPassphrase))) {
                exchange.sendResponseHeaders(401, -1);
                return;
            }

            String requestURI = exchange.getRequestURI().toString();
            Matcher matcher = DELETE_PATTERN.matcher(requestURI);

            if (matcher.matches()) {
                String filename = matcher.group(1);
                Path gameDir = FabricLoader.getInstance().getGameDir();
                Path targetFile = gameDir.resolve("./screenshotUploader/screenshots/" + filename);
                Path jsonFile = gameDir.resolve("./screenshotUploader/screenshots/" + filename.replaceFirst("\\.png$", ".json"));

                try {
                    if (Files.exists(targetFile)) {
                        Files.delete(targetFile);
                    }
                    if (Files.exists(jsonFile)) {
                        Files.delete(jsonFile);
                        exchange.sendResponseHeaders(200, -1);
                    } else {
                        exchange.sendResponseHeaders(404, -1);
                    }
                } catch (IOException e) {
                    exchange.sendResponseHeaders(500, -1);
                } finally {
                    exchange.getResponseBody().close();
                }
            } else {
                exchange.sendResponseHeaders(400, -1);
            }
        }
    }

    private static class ScreenshotFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath().replace("/screenshots", "");
            File staticFile = new File("screenshotUploader/screenshots", path);

            if (!staticFile.exists() || !staticFile.isFile()) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            String mimeType = Files.probeContentType(Paths.get(staticFile.getAbsolutePath()));
            if (mimeType == null) {
                mimeType = "application/octet-stream";
            }

            byte[] fileContent = Files.readAllBytes(staticFile.toPath());

            exchange.getResponseHeaders().add("Content-Type", mimeType);
            exchange.sendResponseHeaders(200, fileContent.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(fileContent);
            }

        }
    }

    private static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath().replace("/static", "");
            File staticFile = new File("screenshotUploader/static", path);

            if (!staticFile.exists() || !staticFile.isFile()) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            String mimeType = Files.probeContentType(Paths.get(staticFile.getAbsolutePath()));
            if (mimeType == null) {
                mimeType = "application/octet-stream";
            }

            byte[] fileContent = Files.readAllBytes(staticFile.toPath());

            exchange.getResponseHeaders().add("Content-Type", mimeType);
            exchange.sendResponseHeaders(200, fileContent.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(fileContent);
            }

        }
    }

    private static class ScreenshotListHandler implements HttpHandler {
        private static String urlString;

        public ScreenshotListHandler(String urlString) {
            ScreenshotListHandler.urlString = urlString;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            File dir = new File("./screenshotUploader/screenshots/");
            File[] files = dir.listFiles((dir1, name) -> name.endsWith(".jpg") || name.endsWith(".png"));

            String jsonResponse = getString(files);

            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, jsonResponse != null ? jsonResponse.getBytes().length : 0);

            try (OutputStream os = exchange.getResponseBody()) {
                if (jsonResponse != null) {
                    os.write(jsonResponse.getBytes());
                }
            }
        }

        private String getString(File[] files) {
            JsonArray fileArray = new JsonArray();
            if (files != null) {
                for (File file : files) {
                    JsonObject fileObject = new JsonObject();
                    fileObject.addProperty("filename", file.getName());
                    fileObject.addProperty("url", urlString + "/screenshots/" + file.getName());
                    fileObject.addProperty("username", getUsername(file.getName()));
                    fileObject.addProperty("date", file.lastModified());

                    String fileName = file.getName();
                    String jsonFileName = fileName.contains(".")
                            ? fileName.substring(0, fileName.lastIndexOf('.')) + ".json"
                            : fileName + ".json";
                    File jsonFile = new File(file.getParent(), jsonFileName);

                    if (jsonFile.exists() && jsonFile.isFile()) {
                        try (FileReader reader = new FileReader(jsonFile)) {
                            JsonObject metaData = JsonParser.parseReader(reader).getAsJsonObject();
                            fileObject.add("metaData", metaData);
                        } catch (IOException e) {
                            fileObject.add("metaData", null);
                        }
                    } else {
                        fileObject.add("metaData", null);
                    }

                    fileArray.add(fileObject);
                }
            }

            return fileArray.toString();
        }

        private String getUsername(String name) {
            if (name.split("-").length > 1) {
                if (name.split("-")[1].split("_").length > 1) {
                    return name.split("-")[1].split("_")[0];
                }
            }
            return "Unknown";
        }
    }

    private static class GetCommentsHandler implements HttpHandler {
        private static final Pattern COMMENT_PATTERN = Pattern.compile("/comments/([^/]+)");

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String requestURI = exchange.getRequestURI().toString();
            Matcher matcher = COMMENT_PATTERN.matcher(requestURI);

            if (matcher.matches()) {
                String filename = matcher.group(1);
                Path gameDir = FabricLoader.getInstance().getGameDir();
                Path commentFile = gameDir.resolve("./screenshotUploader/screenshots/" + filename.replaceFirst("\\.png$", ".json"));

                try {
                    if (Files.exists(commentFile)) {
                        String existingContent = new String(Files.readAllBytes(commentFile));
                        JsonObject existingJson = JsonParser.parseString(existingContent).getAsJsonObject();

                        JsonArray commentsArray = existingJson.has("comments") ? existingJson.getAsJsonArray("comments") : new JsonArray();

                        String jsonResponse = commentsArray.toString();
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, jsonResponse.getBytes().length);
                        exchange.getResponseBody().write(jsonResponse.getBytes());
                    } else {
                        exchange.sendResponseHeaders(404, -1);
                    }
                } catch (IOException e) {
                    exchange.sendResponseHeaders(500, -1);
                } finally {
                    exchange.getResponseBody().close();
                }
            } else {
                exchange.sendResponseHeaders(400, -1);
            }
        }
    }

    private static class StatisticsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            try {
                File screenshotsDir = new File("./screenshotUploader/screenshots/");
                File[] files = screenshotsDir.listFiles((dir, name) -> name.endsWith(".jpg") || name.endsWith(".png"));

                if (files == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }

                JsonObject statistics = new JsonObject();

                JsonObject globalStats = new JsonObject();

                JsonObject serverStats = new JsonObject();
                JsonObject fileStats = new JsonObject();
                JsonObject timeStats = new JsonObject();
                JsonObject worldStats = new JsonObject();

                JsonObject userStats = new JsonObject();

                JsonArray recentUploads = new JsonArray();

                JsonObject globalBiomeStats = new JsonObject();
                JsonObject globalDimensionStats = new JsonObject();
                JsonObject fileSizeStats = new JsonObject();

                long[] sizeCategories = {50_000, 100_000, 500_000, 1_000_000, 5_000_000};
                String[] sizeCategoryNames = {"0-50KB", "50-100KB", "100-500KB", "500KB-1MB", "1-5MB", "5MB+"};
                int[] sizeCategoryCounts = new int[sizeCategories.length + 1];

                JsonObject fileTypeStats = new JsonObject();
                String[] supportedExtensions = {".jpg", ".jpeg", ".png"};
                for (String ext : supportedExtensions) {
                    fileTypeStats.addProperty(ext, 0);
                }

                long totalSize = 0;

                java.util.PriorityQueue<File> mostRecentFiles = new java.util.PriorityQueue<>(10,
                        (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

                serverStats.addProperty("totalScreenshots", files.length);

                for (File file : files) {
                    String username = getUsername(file.getName());

                    if (!userStats.has(username)) {
                        JsonObject newUserObject = new JsonObject();
                        newUserObject.addProperty("uploadCount", 0);
                        newUserObject.addProperty("totalSizeBytes", 0L);
                        newUserObject.addProperty("totalSizeMB", 0.0);
                        userStats.add(username, newUserObject);
                    }

                    JsonObject userObject = userStats.getAsJsonObject(username);
                    int currentCount = userObject.get("uploadCount").getAsInt();
                    userObject.addProperty("uploadCount", currentCount + 1);

                    long fileSize = file.length();
                    long currentUserSize = userObject.get("totalSizeBytes").getAsLong();
                    userObject.addProperty("totalSizeBytes", currentUserSize + fileSize);

                    String fileName = file.getName();
                    String jsonFileName = fileName.replace(".png", ".json");
                    File jsonFile = new File(file.getParent(), jsonFileName);

                    long timestamp = file.lastModified();
                    JsonObject metaData;

                    if (jsonFile.exists() && jsonFile.isFile()) {
                        try (FileReader reader = new FileReader(jsonFile)) {
                            metaData = JsonParser.parseReader(reader).getAsJsonObject();

                            if (metaData.has("current_time")) {
                                try {
                                    timestamp = Long.parseLong(metaData.get("current_time").getAsString());
                                } catch (NumberFormatException ignored) {
                                }
                            }

                            if (metaData.has("biome")) {
                                String biome = metaData.get("biome").getAsString();
                                if (globalBiomeStats.has(biome)) {
                                    globalBiomeStats.addProperty(biome,
                                            globalBiomeStats.get(biome).getAsInt() + 1);
                                } else {
                                    globalBiomeStats.addProperty(biome, 1);
                                }
                            }

                            if (metaData.has("dimension")) {
                                String dimension = metaData.get("dimension").getAsString();
                                if (globalDimensionStats.has(dimension)) {
                                    globalDimensionStats.addProperty(dimension,
                                            globalDimensionStats.get(dimension).getAsInt() + 1);
                                } else {
                                    globalDimensionStats.addProperty(dimension, 1);
                                }
                            }
                        } catch (IOException ignored) {
                        }
                    }

                    String month = new java.text.SimpleDateFormat("yyyy-MM").format(new java.util.Date(timestamp));
                    if (timeStats.has(month)) {
                        timeStats.addProperty(month, timeStats.get(month).getAsInt() + 1);
                    } else {
                        timeStats.addProperty(month, 1);
                    }

                    java.util.Calendar calendar = java.util.Calendar.getInstance();
                    calendar.setTimeInMillis(timestamp);
                    int hour = calendar.get(java.util.Calendar.HOUR_OF_DAY);

                    if (!userObject.has("activityByHour")) {
                        userObject.add("activityByHour", new JsonObject());
                    }
                    JsonObject hourStats = userObject.getAsJsonObject("activityByHour");
                    String hourKey = String.format("%02d:00", hour);

                    if (hourStats.has(hourKey)) {
                        hourStats.addProperty(hourKey, hourStats.get(hourKey).getAsInt() + 1);
                    } else {
                        hourStats.addProperty(hourKey, 1);
                    }

                    String extension = file.getName().substring(file.getName().lastIndexOf(".")).toLowerCase();
                    if (fileTypeStats.has(extension)) {
                        fileTypeStats.addProperty(extension, fileTypeStats.get(extension).getAsInt() + 1);
                    }

                    totalSize += fileSize;

                    int categoryIndex = sizeCategories.length;
                    for (int i = 0; i < sizeCategories.length; i++) {
                        if (fileSize < sizeCategories[i]) {
                            categoryIndex = i;
                            break;
                        }
                    }
                    sizeCategoryCounts[categoryIndex]++;

                    mostRecentFiles.offer(file);
                    if (mostRecentFiles.size() > 10) {
                        mostRecentFiles.poll();
                    }
                }


                for (String username : userStats.keySet()) {
                    JsonObject userObject = userStats.getAsJsonObject(username);
                    int uploadCount = userObject.get("uploadCount").getAsInt();
                    long totalUserSize = userObject.get("totalSizeBytes").getAsLong();

                    if (uploadCount > 0) {
                        userObject.addProperty("averageFileSizeBytes", (double) totalUserSize / uploadCount);
                    } else {
                        userObject.addProperty("averageFileSizeBytes", 0);
                    }
                    if (userObject.has("activityByHour")) {
                        JsonObject hourStats = userObject.getAsJsonObject("activityByHour");
                        String mostActiveHour = getMostFrequentHour(hourStats);
                        if (mostActiveHour != null) {
                            int hour = Integer.parseInt(mostActiveHour.substring(0, 2));
                            userObject.addProperty("mostActiveTime", String.format("%02d:00-%02d:59", hour, hour));
                        }
                    }
                }

                for (int i = 0; i < sizeCategoryNames.length; i++) {
                    fileSizeStats.addProperty(sizeCategoryNames[i], i < sizeCategoryCounts.length ? sizeCategoryCounts[i] : 0);
                }


                double averageSize = files.length > 0 ? (double) totalSize / files.length : 0;
                serverStats.addProperty("averageFileSizeBytes", averageSize);
                serverStats.addProperty("totalFileSizeBytes", totalSize);

                java.util.ArrayList<File> sortedRecentFiles = new java.util.ArrayList<>(mostRecentFiles);
                sortedRecentFiles.sort((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

                for (File file : sortedRecentFiles) {
                    JsonObject fileObj = new JsonObject();
                    fileObj.addProperty("filename", file.getName());
                    fileObj.addProperty("username", getUsername(file.getName()));
                    fileObj.addProperty("timestamp", file.lastModified());
                    fileObj.addProperty("date", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(file.lastModified())));
                    fileObj.addProperty("sizeBytes", file.length());
                    recentUploads.add(fileObj);
                }

                fileStats.add("typeDistribution", fileTypeStats);
                fileStats.add("sizeDistribution", fileSizeStats);

                worldStats.add("biomes", globalBiomeStats);
                worldStats.add("dimensions", globalDimensionStats);

                globalStats.add("serverStats", serverStats);
                globalStats.add("fileStats", fileStats);
                globalStats.add("timeStats", timeStats);
                globalStats.add("worldStats", worldStats);

                statistics.add("globalStats", globalStats);
                statistics.add("userStats", userStats);
                statistics.add("recentUploads", recentUploads);

                String jsonResponse = statistics.toString();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jsonResponse.getBytes().length);
                exchange.getResponseBody().write(jsonResponse.getBytes());
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, -1);
            } finally {
                exchange.getResponseBody().close();
            }
        }

        private String getUsername(String name) {
            if (name.split("-").length > 1) {
                if (name.split("-")[1].split("_").length > 1) {
                    return name.split("-")[1].split("_")[0];
                }
            }
            return "Unknown";
        }

        private String getMostFrequentHour(JsonObject hourStats) {
            String mostFrequentHour = null;
            int maxCount = 0;

            for (String hour : hourStats.keySet()) {
                int count = hourStats.get(hour).getAsInt();
                if (count > maxCount) {
                    maxCount = count;
                    mostFrequentHour = hour;
                }
            }

            return mostFrequentHour;
        }
    }
}
