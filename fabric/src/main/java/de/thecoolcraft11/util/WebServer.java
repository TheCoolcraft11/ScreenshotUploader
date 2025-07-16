package de.thecoolcraft11.util;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import de.thecoolcraft11.config.ConfigManager;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebServer {

    private static final Map<String, String> shortenedUrls = new HashMap<>();
    private static final String CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 6;
    private static final Random RANDOM = new Random();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String SHORTENED_URLS_FILE = "screenshotUploader/shortened_urls.json";
    private static final Logger logger = LoggerFactory.getLogger(WebServer.class);

    public static void startWebServer(String ipAddress, int port, String urlString) throws Exception {
        loadShortenedUrls();

        HttpServer server = HttpServer.create(new InetSocketAddress(ipAddress, port), 0);

        server.createContext("/", new RootHandler());
        server.createContext("/random-screenshot", new RandomScreenshotHandler());
        server.createContext("/delete", new DeleteFileHandler());
        server.createContext("/static", new StaticFileHandler());
        server.createContext("/scr", new ScreenshotFileHandler());
        server.createContext("/screenshots", new ScreenshotFileHandler());
        server.createContext("/screenshot-list", new ScreenshotListHandler(urlString));
        server.createContext("/comments", new GetCommentsHandler());
        server.createContext("/statistics", new StatisticsHandler());
        server.createContext("/shorten", new UrlShortenerHandler(urlString));
        server.createContext("/s", new ShortUrlRedirectHandler());
        server.createContext("/shortener", new ShortenerPageHandler());

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
            String response = GalleryBuilder.buildGallery(files, ConfigManager.getServerConfig().allowDelete, ConfigManager.getServerConfig().deletionPassphrase.isEmpty(), ConfigManager.getServerConfig().useOldCss);
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
                Path jsonFile = gameDir.resolve("./screenshotUploader/screenshots/" + filename.replaceFirst("(?i)\\.(png|jpg|jpeg|gif|bmp|webp)$", ".json"));

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
            String rawPath = exchange.getRequestURI().getPath();
            String path = rawPath.startsWith("/screenshots")
                    ? rawPath.replaceFirst("/screenshots", "")
                    : rawPath.replaceFirst("/scr", "");

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

            if (mimeType.startsWith("image/")) {
                exchange.getResponseHeaders().add("Cache-Control", "public, max-age=31536000, immutable");
            } else {
                exchange.getResponseHeaders().add("Cache-Control", "no-cache, must-revalidate");
            }

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
                mimeType = getMimeType(path);
            }

            byte[] fileContent = Files.readAllBytes(staticFile.toPath());

            exchange.getResponseHeaders().add("Content-Type", mimeType);
            exchange.sendResponseHeaders(200, fileContent.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(fileContent);
            }

        }

        private static String getMimeType(String filename) {
            if (filename.endsWith(".css")) return "text/css";
            if (filename.endsWith(".js")) return "application/javascript";
            if (filename.endsWith(".html")) return "text/html";
            if (filename.endsWith(".png")) return "image/png";
            if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) return "image/jpeg";
            return "application/octet-stream";
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
                Path commentFile = gameDir.resolve("./screenshotUploader/screenshots/" + filename.replaceFirst("(?i)\\.(png|jpg|jpeg|gif|bmp|webp)$", ".json"));

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
        private JsonObject cachedStats = null;
        private long lastCacheTime = 0;
        private static final long CACHE_DURATION = 60 * 1000;

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            try {
                String query = exchange.getRequestURI().getQuery();
                Map<String, String> params = parseQueryParams(query);

                boolean useCache = !params.containsKey("noCache") &&
                        !params.containsKey("from") &&
                        !params.containsKey("to");

                long currentTime = System.currentTimeMillis();

                if (useCache && cachedStats != null && (currentTime - lastCacheTime < CACHE_DURATION)) {
                    sendJsonResponse(exchange, cachedStats.toString());
                    return;
                }

                File screenshotsDir = new File("./screenshotUploader/screenshots/");
                File[] files = screenshotsDir.listFiles((dir, name) -> name.endsWith(".jpg") || name.endsWith(".png"));

                if (files == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }

                files = filterFilesByTime(files, params);

                JsonObject statistics = generateStatistics(files);

                if (useCache) {
                    cachedStats = statistics;
                    lastCacheTime = currentTime;
                }

                sendJsonResponse(exchange, statistics.toString());
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, -1);
            } finally {
                exchange.getResponseBody().close();
            }
        }

        private void sendJsonResponse(HttpExchange exchange, String jsonString) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jsonString.getBytes().length);
            exchange.getResponseBody().write(jsonString.getBytes());
        }

        private Map<String, String> parseQueryParams(String query) {
            Map<String, String> params = new HashMap<>();
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] keyValue = param.split("=", 2);
                    if (keyValue.length > 1) {
                        params.put(keyValue[0], keyValue[1]);
                    }
                }
            }
            return params;
        }

        private File[] filterFilesByTime(File[] files, Map<String, String> params) {
            if (!params.containsKey("from") && !params.containsKey("to")) {
                return files;
            }

            long fromDate = params.containsKey("from") ? Long.parseLong(params.get("from")) : 0;
            long toDate = params.containsKey("to") ? Long.parseLong(params.get("to")) : System.currentTimeMillis();

            return Arrays.stream(files)
                    .filter(file -> {
                        long fileDate = file.lastModified();
                        return fileDate >= fromDate && fileDate <= toDate;
                    })
                    .toArray(File[]::new);
        }

        private JsonObject generateStatistics(File[] files) {
            JsonObject statistics = new JsonObject();
            JsonObject globalStats = new JsonObject();
            JsonObject serverStats = new JsonObject();
            JsonObject fileStats = new JsonObject();
            JsonObject timeStats = new JsonObject();
            JsonObject worldStats = new JsonObject();
            JsonObject userStats = new JsonObject();
            JsonArray recentUploads = new JsonArray();

            collectStandardStatistics(files, statistics, globalStats, serverStats, fileStats,
                    timeStats, worldStats, userStats, recentUploads);

            addPerformanceMetrics(statistics, files);

            JsonObject metadataStats = getMetadataStats(files);
            globalStats.add("metadataStats", metadataStats);

            addLocationHeatMap(worldStats, files);

            return statistics;
        }

        private void collectStandardStatistics(File[] files, JsonObject statistics, JsonObject globalStats,
                                               JsonObject serverStats, JsonObject fileStats, JsonObject timeStats,
                                               JsonObject worldStats, JsonObject userStats, JsonArray recentUploads) {
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
                String jsonFileName = fileName.replaceFirst("(?i)\\.(png|jpg|jpeg|gif|bmp|webp)$", ".json");
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
        }

        private void addPerformanceMetrics(JsonObject statistics, File[] files) {
            JsonObject performanceStats = new JsonObject();

            Map<String, Integer> uploadsPerDay = new HashMap<>();
            for (File file : files) {
                String day = new java.text.SimpleDateFormat("yyyy-MM-dd").format(file.lastModified());
                uploadsPerDay.put(day, uploadsPerDay.getOrDefault(day, 0) + 1);
            }

            JsonObject dailyUploads = new JsonObject();
            for (Map.Entry<String, Integer> entry : uploadsPerDay.entrySet()) {
                dailyUploads.addProperty(entry.getKey(), entry.getValue());
            }

            performanceStats.add("uploadsPerDay", dailyUploads);

            if (files.length > 0) {
                List<Map.Entry<String, Integer>> sortedUploads = new ArrayList<>(uploadsPerDay.entrySet());
                sortedUploads.sort(Map.Entry.comparingByKey());

                JsonObject weeklyAverage = getWeeklyAverageStatistics(sortedUploads);

                performanceStats.add("weeklyMovingAverage", weeklyAverage);
            }

            statistics.add("performanceStats", performanceStats);
        }

        private static @NotNull JsonObject getWeeklyAverageStatistics(List<Map.Entry<String, Integer>> sortedUploads) {
            JsonObject weeklyAverage = new JsonObject();

            if (sortedUploads.size() >= 7) {
                for (int i = 6; i < sortedUploads.size(); i++) {
                    int sum = 0;
                    for (int j = i - 6; j <= i; j++) {
                        sum += sortedUploads.get(j).getValue();
                    }
                    double average = (double) sum / 7;
                    weeklyAverage.addProperty(sortedUploads.get(i).getKey(), average);
                }
            }
            return weeklyAverage;
        }

        private JsonObject getMetadataStats(File[] files) {
            JsonObject metadataStats = new JsonObject();
            JsonObject clientVersionStats = new JsonObject();
            JsonObject renderDistanceStats = new JsonObject();
            JsonObject graphicsSettings = new JsonObject();

            for (File file : files) {
                String jsonFileName = file.getName().replaceFirst("(?i)\\.(png|jpg|jpeg|gif|bmp|webp)$", ".json");
                File jsonFile = new File(file.getParent(), jsonFileName);

                if (jsonFile.exists()) {
                    try (FileReader reader = new FileReader(jsonFile)) {
                        JsonObject metadata = JsonParser.parseReader(reader).getAsJsonObject();

                        if (metadata.has("system_info")) {
                            String sysInfo = metadata.get("system_info").getAsString();
                            if (sysInfo.contains("Version:")) {
                                String version = sysInfo.substring(sysInfo.indexOf("Version:") + 9).split(",")[0].trim();
                                clientVersionStats.addProperty(version,
                                        clientVersionStats.has(version) ? clientVersionStats.get(version).getAsInt() + 1 : 1);
                            }
                        }

                        if (metadata.has("client_settings")) {
                            String settings = metadata.get("client_settings").getAsString();
                            if (settings.contains("Render Distance:")) {
                                String renderDistance = settings.substring(settings.indexOf("Render Distance:") + 16).split(",")[0].trim();
                                renderDistanceStats.addProperty(renderDistance,
                                        renderDistanceStats.has(renderDistance) ? renderDistanceStats.get(renderDistance).getAsInt() + 1 : 1);
                            }

                            if (settings.contains("Graphics:")) {
                                String graphicsSetting = settings.substring(settings.indexOf("Graphics:") + 9).split(",")[0].trim();
                                graphicsSettings.addProperty(graphicsSetting,
                                        graphicsSettings.has(graphicsSetting) ? graphicsSettings.get(graphicsSetting).getAsInt() + 1 : 1);
                            }
                        }
                    } catch (IOException ignored) {
                    }
                }
            }

            metadataStats.add("clientVersions", clientVersionStats);
            metadataStats.add("renderDistances", renderDistanceStats);
            metadataStats.add("graphicsSettings", graphicsSettings);

            return metadataStats;
        }

        private void addLocationHeatMap(JsonObject worldStats, File[] files) {
            JsonArray locationData = new JsonArray();

            Map<String, ClusterPoint> clusters = new HashMap<>();
            int gridSize = 100;

            for (File file : files) {
                String jsonFileName = file.getName().replaceFirst("(?i)\\.(png|jpg|jpeg|gif|bmp|webp)$", ".json");
                File jsonFile = new File(file.getParent(), jsonFileName);

                if (jsonFile.exists()) {
                    try (FileReader reader = new FileReader(jsonFile)) {
                        JsonObject metadata = JsonParser.parseReader(reader).getAsJsonObject();

                        if (metadata.has("coordinates")) {
                            String coords = metadata.get("coordinates").getAsString();
                            Pattern pattern = Pattern.compile("X: (-?\\d+), Y: (-?\\d+), Z: (-?\\d+)");
                            Matcher matcher = pattern.matcher(coords);

                            if (matcher.find()) {
                                int x = Integer.parseInt(matcher.group(1));
                                int y = Integer.parseInt(matcher.group(2));
                                int z = Integer.parseInt(matcher.group(3));

                                JsonObject point = new JsonObject();
                                point.addProperty("x", x);
                                point.addProperty("y", y);
                                point.addProperty("z", z);

                                String dimension = "overworld";
                                if (metadata.has("dimension")) {
                                    dimension = metadata.get("dimension").getAsString();
                                    point.addProperty("dimension", dimension);
                                }

                                locationData.add(point);

                                int gridX = Math.floorDiv(x, gridSize);
                                int gridZ = Math.floorDiv(z, gridSize);
                                String clusterKey = dimension + ":" + gridX + ":" + gridZ;

                                if (clusters.containsKey(clusterKey)) {
                                    ClusterPoint cluster = clusters.get(clusterKey);
                                    cluster.count++;
                                    cluster.totalX += x;
                                    cluster.totalY += y;
                                    cluster.totalZ += z;
                                } else {
                                    clusters.put(clusterKey, new ClusterPoint(x, y, z, dimension));
                                }
                            }
                        }
                    } catch (IOException ignored) {
                    }
                }
            }

            List<ClusterPoint> sortedClusters = new ArrayList<>(clusters.values());
            sortedClusters.sort((c1, c2) -> Integer.compare(c2.count, c1.count));

            JsonArray clusteredData = getClusterStatistics(sortedClusters);

            worldStats.add("locations", locationData);
            worldStats.add("clusteredLocations", clusteredData);
        }

        private @NotNull JsonArray getClusterStatistics(List<ClusterPoint> sortedClusters) {
            JsonArray clusteredData = new JsonArray();
            for (ClusterPoint cluster : sortedClusters) {
                JsonObject clusterPoint = new JsonObject();
                clusterPoint.addProperty("x", cluster.totalX / cluster.count);
                clusterPoint.addProperty("y", cluster.totalY / cluster.count);
                clusterPoint.addProperty("z", cluster.totalZ / cluster.count);
                clusterPoint.addProperty("count", cluster.count);
                clusterPoint.addProperty("dimension", cluster.dimension);
                clusteredData.add(clusterPoint);
            }
            return clusteredData;
        }

        private static class ClusterPoint {
            public int count = 1;
            public int totalX;
            public int totalY;
            public int totalZ;
            public String dimension;

            public ClusterPoint(int x, int y, int z, String dimension) {
                this.totalX = x;
                this.totalY = y;
                this.totalZ = z;
                this.dimension = dimension;
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

    private record UrlShortenerHandler(String baseUrl) implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            if (!ConfigManager.getServerConfig().allowShortenedUrls) {
                exchange.sendResponseHeaders(423, -1);
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String providedPassphrase = exchange.getRequestHeaders().getFirst("X-Shortener-Passphrase");
            if (ConfigManager.getServerConfig().shortenerPassphrase != null &&
                    !ConfigManager.getServerConfig().shortenerPassphrase.isEmpty() &&
                    (providedPassphrase == null || !providedPassphrase.equals(ConfigManager.getServerConfig().shortenerPassphrase))) {
                exchange.sendResponseHeaders(403, -1);
                return;
            }

            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                JsonObject requestJson = JsonParser.parseString(requestBody).getAsJsonObject();

                String originalUrl = requestJson.get("url").getAsString();

                if (originalUrl == null || originalUrl.isEmpty()) {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }

                String shortCode = generateShortCode();
                shortenedUrls.put(shortCode, originalUrl);

                if (ConfigManager.getServerConfig().saveShortenedUrlsToFile) {
                    saveShortenedUrls();
                }

                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("shortUrl", baseUrl + "/s/" + shortCode);

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, responseJson.toString().getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseJson.toString().getBytes());
                }
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, -1);
            } finally {
                exchange.getResponseBody().close();
            }
        }
    }

    private static class ShortUrlRedirectHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod()) && !"HEAD".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String requestURI = exchange.getRequestURI().toString();
            String shortCode = requestURI.substring(requestURI.lastIndexOf('/') + 1);

            String originalUrl = shortenedUrls.get(shortCode);
            if (originalUrl != null) {
                exchange.getResponseHeaders().set("Location", originalUrl);
                exchange.sendResponseHeaders(302, -1);
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        }
    }

    private static class ShortenerPageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            File staticFile = new File("screenshotUploader/static/html", "/shortener.html");

            if (staticFile.exists() && staticFile.isFile()) {
                byte[] fileContent = Files.readAllBytes(staticFile.toPath());
                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, fileContent.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(fileContent);
                }
            }
        }
    }

    private static String generateShortCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private static void loadShortenedUrls() {
        if (!ConfigManager.getServerConfig().saveShortenedUrlsToFile) {
            return;
        }

        File file = new File(SHORTENED_URLS_FILE);
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Map<String, String> loadedUrls = GSON.fromJson(reader, new TypeToken<Map<String, String>>() {
                }.getType());
                if (loadedUrls != null) {
                    shortenedUrls.putAll(loadedUrls);
                }
            } catch (IOException e) {
                logger.error("Failed to load shortened URLs: {}", e.getMessage());
            }
        }
    }

    private static void saveShortenedUrls() {
        if (!ConfigManager.getServerConfig().saveShortenedUrlsToFile) {
            return;
        }

        File file = new File(SHORTENED_URLS_FILE);
        boolean wasCreated = file.getParentFile().mkdirs();
        if (wasCreated) {
            logger.info("Created directory for shortened URLs file: {}", file.getParent());
        }

        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(shortenedUrls, writer);
        } catch (IOException e) {
            logger.error("Failed to save shortened URLs: {}", e.getMessage());
        }
    }
}
