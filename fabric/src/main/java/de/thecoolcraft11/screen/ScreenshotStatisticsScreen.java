package de.thecoolcraft11.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScreenshotStatisticsScreen extends Screen {
    private JsonObject statisticsData;
    private boolean isLoading = true;
    private String errorMessage = null;
    private final List<StatisticsSection> sections = new ArrayList<>();
    private int scrollOffset = 0;
    private static final int SECTION_SPACING = 15;

    private int currentTab = 0;
    private final String[] tabs = {"Overview", "Users", "Screenshots", "Performance", "Heatmap"};

    private String selectedUser = null;
    private final Map<String, List<StatisticsSection>> userDetailSections = new HashMap<>();

    private final Screen parent;

    private boolean isHeatmapActive = false;
    private final List<LocationPoint> locationPoints = new ArrayList<>();
    private static final String[] DIMENSIONS = {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"};
    private static final int MAX_POINT_SIZE = 30;
    private static final int MIN_POINT_SIZE = 5;

    private int heatmapOffsetX = 0;
    private int heatmapOffsetY = 0;
    private float heatmapScale = 1.0f;
    private String selectedDimension = "minecraft:overworld";

    private boolean isDraggingMap = false;
    private int dragStartX = 0;
    private int dragStartY = 0;
    private int dragStartOffsetX = 0;
    private int dragStartOffsetY = 0;

    public ScreenshotStatisticsScreen(Screen parent) {
        super(Text.translatable("screen.screenshot_uploader.local_statistics.title"));
        this.parent = parent;
        generateStatistics();
    }

    private void generateStatistics() {
        CompletableFuture.runAsync(() -> {
            try {
                File screenshotsDir = new File("screenshots");
                File[] files = screenshotsDir.listFiles((dir, name) -> name.endsWith(".jpg") || name.endsWith(".png"));

                if (files == null || files.length == 0) {
                    errorMessage = "No screenshots found in screenshots folder";
                    isLoading = false;
                    return;
                }

                statisticsData = generateLocalStatistics(files);
                processStatisticsData();
            } catch (Exception e) {
                errorMessage = "Error: " + e.getMessage();
            } finally {
                isLoading = false;
            }
        });
    }

    private JsonObject generateLocalStatistics(File[] files) {
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
            String jsonFileName = fileName.replace(".png", ".json").replace(".jpg", ".json");
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

    private JsonObject getWeeklyAverageStatistics(List<Map.Entry<String, Integer>> sortedUploads) {
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
            String jsonFileName = file.getName().replace(".png", ".json").replace(".jpg", ".json");
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
            String jsonFileName = file.getName().replace(".png", ".json").replace(".jpg", ".json");
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

    private JsonArray getClusterStatistics(List<ClusterPoint> sortedClusters) {
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

    private void processStatisticsData() {
        sections.clear();
        userDetailSections.clear();
        if (statisticsData == null) return;

        try {
            if (statisticsData.has("globalStats")) {
                JsonObject globalStats = statisticsData.getAsJsonObject("globalStats");

                if (globalStats.has("serverStats")) {
                    JsonObject serverStats = globalStats.getAsJsonObject("serverStats");
                    StatisticsSection serverSection = new StatisticsSection("Local Screenshots Statistics",
                            Text.translatable("gui.screenshot_uploader.local_statistics.local_statistics"));

                    int totalScreenshots = serverStats.has("totalScreenshots") ?
                            serverStats.get("totalScreenshots").getAsInt() : 0;
                    serverSection.addEntry(Text.translatable("gui.screenshot_uploader.local_statistics.total_screenshots"),
                            Text.of(String.valueOf(totalScreenshots)));

                    if (serverStats.has("totalFileSizeBytes")) {
                        long totalSize = serverStats.get("totalFileSizeBytes").getAsLong();
                        serverSection.addEntry(Text.translatable("gui.screenshot_uploader.local_statistics.total_size"),
                                Text.of(formatFileSize(totalSize)));
                    }

                    if (serverStats.has("averageFileSizeBytes")) {
                        double avgSize = serverStats.get("averageFileSizeBytes").getAsDouble();
                        serverSection.addEntry(Text.translatable("gui.screenshot_uploader.local_statistics.average_size"),
                                Text.of(formatFileSize((long) avgSize)));
                    }

                    sections.add(serverSection);
                }

                if (globalStats.has("fileStats")) {
                    JsonObject fileStats = globalStats.getAsJsonObject("fileStats");
                    StatisticsSection fileSection = new StatisticsSection("File Statistics",
                            Text.translatable("gui.screenshot_uploader.local_statistics.file_statistics"));

                    if (fileStats.has("typeDistribution")) {
                        JsonObject typeDistribution = fileStats.getAsJsonObject("typeDistribution");
                        fileSection.addEntry(Text.translatable("gui.screenshot_uploader.local_statistics.file_types"), Text.empty());
                        for (Map.Entry<String, JsonElement> entry : typeDistribution.entrySet()) {
                            fileSection.addEntry(Text.of("  " + entry.getKey()), Text.of(entry.getValue().getAsString()));
                        }
                    }

                    if (fileStats.has("sizeDistribution")) {
                        JsonObject sizeDistribution = fileStats.getAsJsonObject("sizeDistribution");
                        fileSection.addEntry(Text.translatable("gui.screenshot_uploader.local_statistics.file_distribution"), Text.empty());
                        for (Map.Entry<String, JsonElement> entry : sizeDistribution.entrySet()) {
                            fileSection.addEntry(Text.of("  " + entry.getKey()), Text.of(entry.getValue().getAsString()));
                        }
                    }

                    sections.add(fileSection);
                }

                if (globalStats.has("worldStats")) {
                    JsonObject worldStats = globalStats.getAsJsonObject("worldStats");
                    StatisticsSection worldSection = new StatisticsSection("World Statistics",
                            Text.translatable("gui.screenshot_uploader.local_statistics.world_statistics"));

                    if (worldStats.has("dimensions")) {
                        JsonObject dimensions = worldStats.getAsJsonObject("dimensions");
                        worldSection.addEntry(Text.translatable("gui.screenshot_uploader.local_statistics.dimensions"), Text.empty());
                        for (Map.Entry<String, JsonElement> entry : dimensions.entrySet()) {
                            worldSection.addEntry(Text.translatable("  " + entry.getKey()), Text.of(entry.getValue().getAsString()));
                        }
                    }

                    if (worldStats.has("biomes")) {
                        JsonObject biomes = worldStats.getAsJsonObject("biomes");
                        worldSection.addEntry(Text.translatable("gui.screenshot_uploader.local_statistics.biomes"), Text.empty());
                        List<Map.Entry<String, JsonElement>> sortedBiomes = new ArrayList<>(biomes.entrySet());
                        sortedBiomes.sort((e1, e2) -> e2.getValue().getAsInt() - e1.getValue().getAsInt());

                        int biomeCount = 0;
                        for (Map.Entry<String, JsonElement> entry : sortedBiomes) {
                            if (biomeCount++ < 5) {
                                worldSection.addEntry(Text.of("  " + entry.getKey()), Text.of(entry.getValue().getAsString()));
                            }
                        }
                    }

                    sections.add(worldSection);
                }
            }

            if (statisticsData.has("userStats")) {
                JsonObject userStats = statisticsData.getAsJsonObject("userStats");

                processUserGeneralStatistics(userStats);

                processUserDetailStatistics(userStats);
            }

            if (statisticsData.has("performanceStats")) {
                JsonObject performanceStats = statisticsData.getAsJsonObject("performanceStats");
                StatisticsSection performanceSection = new StatisticsSection("Performance Statistics",
                        Text.translatable("gui.screenshot_uploader.local_statistics.performance_statistics"));

                if (performanceStats.has("uploadsPerDay")) {
                    JsonObject uploadsPerDay = performanceStats.getAsJsonObject("uploadsPerDay");
                    performanceSection.addEntry(Text.translatable("gui.screenshot_uploader.local_statistics.average_daily_screenshots"),
                            Text.of(calculateAverageUploads(uploadsPerDay)));

                    List<Map.Entry<String, JsonElement>> sortedUploads = new ArrayList<>(uploadsPerDay.entrySet());
                    sortedUploads.sort(Map.Entry.<String, JsonElement>comparingByKey().reversed());

                    int dayCount = 0;
                    for (Map.Entry<String, JsonElement> entry : sortedUploads) {
                        if (dayCount++ < 7) {
                            performanceSection.addEntry(Text.of("  " + entry.getKey()),
                                    Text.translatable("gui.screenshot_uploader.local_statistics.screenshots_per_day",
                                            entry.getValue().getAsInt()));
                        }
                    }
                }

                if (performanceStats.has("weeklyMovingAverage")) {
                    JsonObject weeklyAverage = performanceStats.getAsJsonObject("weeklyMovingAverage");
                    performanceSection.addEntry(Text.translatable("gui.screenshot_uploader.local_statistics.weekly_moving_average"),
                            Text.empty());

                    List<Map.Entry<String, JsonElement>> sortedWeekly = new ArrayList<>(weeklyAverage.entrySet());
                    sortedWeekly.sort(Map.Entry.<String, JsonElement>comparingByKey().reversed());

                    int weekCount = 0;
                    for (Map.Entry<String, JsonElement> entry : sortedWeekly) {
                        if (weekCount++ < 5) {
                            performanceSection.addEntry(Text.of("  " + entry.getKey()),
                                    Text.translatable("gui.screenshot_uploader.local_statistics.weekly_average",
                                            entry.getValue().getAsInt()));
                        }
                    }
                }

                sections.add(performanceSection);
            }

            if (statisticsData.has("recentUploads")) {
                StatisticsSection recentSection = new StatisticsSection("Recent Screenshots",
                        Text.translatable("gui.screenshot_uploader.local_statistics.recent_screenshots"));
                int count = 0;
                for (JsonElement element : statisticsData.getAsJsonArray("recentUploads")) {
                    if (count++ < 5) {
                        JsonObject upload = element.getAsJsonObject();
                        String username = upload.has("username") ? upload.get("username").getAsString() : "Unknown";
                        String date = upload.has("date") ? upload.get("date").getAsString() : "Unknown date";
                        recentSection.addEntry(Text.of(username), Text.of(date));
                    }
                }
                sections.add(recentSection);
            }

            processLocationData();

        } catch (Exception e) {
            errorMessage = "Error processing data: " + e.getMessage();
        }
    }

    private void processUserGeneralStatistics(JsonObject userStats) {
        StatisticsSection userSection = new StatisticsSection("User Statistics",
                Text.translatable("gui.screenshot_uploader.local_statistics.users_statistics"));

        userSection.addEntry(Text.translatable("gui.screenshot_uploader.local_statistics.total_users"),
                Text.of(String.valueOf(userStats.size())));

        int totalUploads = 0;
        for (Map.Entry<String, JsonElement> entry : userStats.entrySet()) {
            JsonObject userData = entry.getValue().getAsJsonObject();
            totalUploads += userData.get("uploadCount").getAsInt();
        }
        userSection.addEntry(Text.translatable("gui.screenshot_uploader.local_statistics.total_screenshots"),
                Text.of(String.valueOf(totalUploads)));

        double avgUploadsPerUser = !userStats.isEmpty() ? (double) totalUploads / userStats.size() : 0;
        userSection.addEntry(Text.translatable("gui.screenshot_uploader.local_statistics.average_screenshots_per_user"),
                Text.translatable("gui.screenshot_uploader.local_statistics.avg_screenshots_per_user", avgUploadsPerUser));

        List<Map.Entry<String, JsonElement>> sortedUsers = new ArrayList<>(userStats.entrySet());
        sortedUsers.sort((u1, u2) -> {
            int count1 = u1.getValue().getAsJsonObject().get("uploadCount").getAsInt();
            int count2 = u2.getValue().getAsJsonObject().get("uploadCount").getAsInt();
            return count2 - count1;
        });

        userSection.addEntry(Text.translatable("gui.screenshot_uploader.local_statistics.top_users"), Text.empty());
        int userCount = 0;
        for (Map.Entry<String, JsonElement> entry : sortedUsers) {
            if (userCount++ < 10) {
                JsonObject userData = entry.getValue().getAsJsonObject();
                int uploadCount = userData.get("uploadCount").getAsInt();
                String username = entry.getKey();

                Text text = Text.of(username);

                userSection.addEntry(text, Text.translatable("gui.screenshot_uploader.local_statistics.user_screenshots",
                        uploadCount));
                userSection.makeEntryClickable(text, () -> {
                    selectedUser = username;
                    scrollOffset = 0;
                });
            }
        }

        StatisticsSection activitySection = new StatisticsSection("User Activity Patterns",
                Text.translatable("gui.screenshot_uploader.local_statistics.activity_patterns"));

        Map<String, Integer> activityByHour = new HashMap<>();
        for (Map.Entry<String, JsonElement> userEntry : userStats.entrySet()) {
            JsonObject userData = userEntry.getValue().getAsJsonObject();
            if (userData.has("activityByHour")) {
                JsonObject hourData = userData.getAsJsonObject("activityByHour");
                for (Map.Entry<String, JsonElement> hourEntry : hourData.entrySet()) {
                    String hour = hourEntry.getKey();
                    int count = hourEntry.getValue().getAsInt();
                    activityByHour.put(hour, activityByHour.getOrDefault(hour, 0) + count);
                }
            }
        }

        List<Map.Entry<String, Integer>> sortedHours = new ArrayList<>(activityByHour.entrySet());
        sortedHours.sort(Map.Entry.<String, Integer>comparingByValue().reversed());

        activitySection.addEntry(Text.translatable("gui.screenshot_uploader.local_statistics.peak_activity_times"), Text.empty());
        int hourCount = 0;
        for (Map.Entry<String, Integer> entry : sortedHours) {
            if (hourCount++ < 5) {
                activitySection.addEntry(Text.of("  " + entry.getKey()),
                        Text.translatable("gui.screenshot_uploader.local_statistics.activity_count", entry.getValue()));
            }
        }

        sections.add(userSection);
        sections.add(activitySection);
    }

    private void processUserDetailStatistics(JsonObject userStats) {
        for (Map.Entry<String, JsonElement> entry : userStats.entrySet()) {
            String username = entry.getKey();
            JsonObject userData = entry.getValue().getAsJsonObject();

            List<StatisticsSection> userSections = new ArrayList<>();

            StatisticsSection overviewSection = new StatisticsSection(username + "'s Statistics",
                    Text.translatable("gui.screenshot_uploader.local_statistics.user_statistics", username));
            overviewSection.addEntry(Text.translatable("gui.screenshot_uploader.local_statistics.total_screenshots"),
                    Text.of(String.valueOf(userData.get("uploadCount").getAsInt())));

            if (userData.has("totalSizeBytes")) {
                long totalSize = userData.get("totalSizeBytes").getAsLong();
                overviewSection.addEntry(Text.translatable("gui.screenshot_uploader.local_statistics.total_size"),
                        Text.of(formatFileSize(totalSize)));
            }

            if (userData.has("averageFileSizeBytes")) {
                double avgSize = userData.get("averageFileSizeBytes").getAsDouble();
                overviewSection.addEntry(Text.translatable("gui.screenshot_uploader.local_statistics.average_file_size"),
                        Text.of(formatFileSize((long) avgSize)));
            }

            if (userData.has("mostActiveTime")) {
                overviewSection.addEntry(Text.translatable("gui.screenshot_uploader.local_statistics.most_active_time"),
                        Text.of(userData.get("mostActiveTime").getAsString()));
            }

            userSections.add(overviewSection);

            if (userData.has("activityByHour")) {
                StatisticsSection activitySection = new StatisticsSection("Activity By Hour",
                        Text.translatable("gui.screenshot_uploader.local_statistics.activity_by_hour"));

                JsonObject hourData = userData.getAsJsonObject("activityByHour");

                List<Map.Entry<String, JsonElement>> sortedHours = new ArrayList<>(hourData.entrySet());
                sortedHours.sort((h1, h2) -> h2.getValue().getAsInt() - h1.getValue().getAsInt());

                for (Map.Entry<String, JsonElement> hourEntry : sortedHours) {
                    String hour = hourEntry.getKey();
                    int count = hourEntry.getValue().getAsInt();
                    activitySection.addEntry(Text.of(hour),
                            Text.translatable("gui.screenshot_uploader.local_statistics.activity_hour_count", count));
                }

                userSections.add(activitySection);
            }

            StatisticsSection navigationSection = new StatisticsSection("", Text.empty());

            userSections.add(navigationSection);

            userDetailSections.put(username, userSections);
        }
    }

    private void processLocationData() {
        locationPoints.clear();

        if (statisticsData == null || !statisticsData.has("globalStats")) {
            return;
        }

        JsonObject globalStats = statisticsData.getAsJsonObject("globalStats");
        if (!globalStats.has("worldStats")) {
            return;
        }

        JsonObject worldStats = globalStats.getAsJsonObject("worldStats");
        if (!worldStats.has("clusteredLocations")) {
            return;
        }

        for (JsonElement element : worldStats.getAsJsonArray("clusteredLocations")) {
            JsonObject point = element.getAsJsonObject();

            if (point.has("x") && point.has("z") && point.has("dimension")) {
                int x = point.get("x").getAsInt();
                int y = point.has("y") ? point.get("y").getAsInt() : 64;
                int z = point.get("z").getAsInt();
                String dimension = point.get("dimension").getAsString();
                int count = point.has("count") ? point.get("count").getAsInt() : 1;

                locationPoints.add(new LocationPoint(x, y, z, dimension, count));
            }
        }

        isHeatmapActive = true;
    }

    private String calculateAverageUploads(JsonObject uploadsPerDay) {
        if (uploadsPerDay.isEmpty()) return "0";

        int total = 0;
        for (Map.Entry<String, JsonElement> entry : uploadsPerDay.entrySet()) {
            total += entry.getValue().getAsInt();
        }

        double average = (double) total / uploadsPerDay.size();
        return String.format("%.1f", average);
    }

    @Override
    protected void init() {
        super.init();
        this.clearChildren();

        if (selectedUser != null) {
            this.addDrawableChild(ButtonWidget.builder(Text.of("← Back to Statistics"), button -> {
                selectedUser = null;
                scrollOffset = 0;
                this.init();
            }).dimensions(10, 25, 120, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"), button -> close())
                    .dimensions(width - 60, 25, 50, 20).build());

            return;
        }

        int buttonWidth = 80;
        int totalTabsWidth = buttonWidth * tabs.length;
        int startX = (width - totalTabsWidth) / 2;

        for (int i = 0; i < tabs.length; i++) {
            final int tabIndex = i;
            ButtonWidget tabButton = ButtonWidget.builder(Text.of(tabs[i]), button -> {
                currentTab = tabIndex;
                scrollOffset = 0;
                this.init();
            }).dimensions(startX + (buttonWidth * i), 25, buttonWidth, 20).build();


            this.addDrawableChild(tabButton);
        }

        String playerName = MinecraftClient.getInstance().getSession().getUsername();
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("My Statistics"),
                button -> {
                    selectedUser = playerName;
                    scrollOffset = 0;
                    this.init();
                }
        ).dimensions(10, height - 30, 100, 20).build());

        if (currentTab == 4) {
            int dimensionButtonWidth = 100;
            int dimensionButtonSpacing = 10;
            int totalDimensionButtonsWidth = DIMENSIONS.length * dimensionButtonWidth + (DIMENSIONS.length - 1) * dimensionButtonSpacing;
            int dimensionStartX = (width - totalDimensionButtonsWidth) / 2;

            for (int i = 0; i < DIMENSIONS.length; i++) {
                final String dimension = DIMENSIONS[i];
                ButtonWidget dimensionButton = ButtonWidget.builder(
                        Text.of(formatDimensionName(dimension)),
                        button -> {
                            selectedDimension = dimension;
                            heatmapOffsetX = 0;
                            heatmapOffsetY = 0;
                        }
                ).dimensions(
                        dimensionStartX + i * (dimensionButtonWidth + dimensionButtonSpacing),
                        60,
                        dimensionButtonWidth,
                        20
                ).build();

                this.addDrawableChild(dimensionButton);
            }
        }

        this.addDrawableChild(ButtonWidget.builder(Text.of("Refresh"), button -> {
            isLoading = true;
            errorMessage = null;
            generateStatistics();
        }).dimensions(width - 80, 25, 70, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"), button -> close())
                .dimensions(10, 25, 50, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, width / 2, 10, 0xFFFFFF);

        if (currentTab == 4 && isHeatmapActive && !isLoading && errorMessage == null) {
            renderHeatmap(context, mouseX, mouseY);
        }

        if (isLoading) {
            String loadingText = "Loading statistics...";
            context.drawCenteredTextWithShadow(this.textRenderer, loadingText, width / 2, height / 2, 0xFFFFFF);
            return;
        }

        if (errorMessage != null) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal(errorMessage).formatted(Formatting.RED),
                    width / 2, height / 2, 0xFFFFFF);
            return;
        }

        if ((sections.isEmpty() && selectedUser == null) ||
                (selectedUser != null && !userDetailSections.containsKey(selectedUser))) {
            context.drawCenteredTextWithShadow(this.textRenderer, "No statistics available", width / 2, height / 2, 0xFFFFFF);
            return;
        }

        if (selectedUser != null) {
            renderUserDetails(context, mouseX, mouseY);
            return;
        }

        List<StatisticsSection> filteredSections = getTabSections();

        int y = 55 - scrollOffset;
        for (StatisticsSection section : filteredSections) {
            if (y + section.getHeight() < 50 || y > height) {
                y += section.getHeight() + SECTION_SPACING;
                continue;
            }

            context.fill(width / 2 - 160, y - 5, width / 2 + 160, y + section.getHeight() + 5, 0x80000000);
            context.fill(width / 2 - 160, y - 5, width / 2 - 159, y + section.getHeight() + 5, 0xFFAAAAAA);
            context.fill(width / 2 + 159, y - 5, width / 2 + 160, y + section.getHeight() + 5, 0xFFAAAAAA);
            context.fill(width / 2 - 160, y - 5, width / 2 + 160, y - 4, 0xFFAAAAAA);
            context.fill(width / 2 - 160, y + section.getHeight() + 4, width / 2 + 160, y + section.getHeight() + 5, 0xFFAAAAAA);

            context.drawTextWithShadow(this.textRenderer, section.translatedTitle, width / 2 - 150, y, 0xFFAA00);
            y += 12;

            for (StatisticsEntry entry : section.entries) {
                if (y >= 50 && y <= height) {
                    boolean isHovering = entry.isClickable &&
                            mouseX >= width / 2 - 145 && mouseX <= width / 2 + 145 &&
                            mouseY >= y - 1 && mouseY <= y + 9;

                    int keyColor = isHovering ? 0xFFFF55 : 0xFFFFFF;
                    context.drawTextWithShadow(this.textRenderer, entry.key, width / 2 - 145, y, keyColor);
                    context.drawTextWithShadow(this.textRenderer, entry.value, width / 2 + 30, y, 0xAAAAAA);

                    if (entry.isClickable) {
                        int textWidth = textRenderer.getWidth(entry.key);
                        context.fill(width / 2 - 145, y + 9, width / 2 - 145 + textWidth, y + 10,
                                isHovering ? 0xFFFFFF55 : 0xFFAAAAAA);
                    }
                }
                y += 10;
            }

            y += SECTION_SPACING;
        }

        int contentHeight = getTotalContentHeight(filteredSections);
        if (contentHeight > height - 80) {
            if (scrollOffset > 0) {
                context.drawCenteredTextWithShadow(this.textRenderer, "▲", width / 2, 50, 0xFFFFFF);
            }
            if (scrollOffset < contentHeight - (height - 80)) {
                context.drawCenteredTextWithShadow(this.textRenderer, "▼", width / 2, height - 10, 0xFFFFFF);
            }
        }
    }

    private void renderHeatmap(DrawContext context, int mouseX, int mouseY) {
        if (locationPoints.isEmpty()) return;

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int maxCount = 0;

        List<LocationPoint> pointsInCurrentDimension = new ArrayList<>();
        for (LocationPoint point : locationPoints) {
            if (point.dimension.equals(selectedDimension)) {
                pointsInCurrentDimension.add(point);
                minX = Math.min(minX, point.x);
                maxX = Math.max(maxX, point.x);
                minZ = Math.min(minZ, point.z);
                maxZ = Math.max(maxZ, point.z);
                maxCount = Math.max(maxCount, point.count);
            }
        }

        if (pointsInCurrentDimension.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    "No data available for dimension: " + formatDimensionName(selectedDimension),
                    width / 2, height / 2, 0xFFFFFF);
            return;
        }

        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        int rangeX = Math.max(1, maxX - minX);
        int rangeZ = Math.max(1, maxZ - minZ);

        float scaleFactor = Math.min(
                (float) (width - 100) / rangeX,
                (float) (height - 150) / rangeZ
        ) * heatmapScale * 0.8f;

        int mapCenterX = width / 2 + heatmapOffsetX;
        int mapCenterY = height / 2 + 30 + heatmapOffsetY;
        int mapWidth = (int) (rangeX * scaleFactor);
        int mapHeight = (int) (rangeZ * scaleFactor);

        context.fill(mapCenterX - mapWidth / 2 - 5, mapCenterY - mapHeight / 2 - 5,
                mapCenterX + mapWidth / 2 + 5, mapCenterY + mapHeight / 2 + 5,
                0x80000000);

        int gridSize = 100;
        int gridColor = 0x40FFFFFF;

        for (int x = (minX / gridSize) * gridSize; x <= maxX; x += gridSize) {
            int screenX = mapCenterX + (int) ((x - centerX) * scaleFactor);
            context.fill(screenX, mapCenterY - mapHeight / 2, screenX + 1, mapCenterY + mapHeight / 2, gridColor);

            if (x % 500 == 0) {
                context.drawTextWithShadow(textRenderer, String.valueOf(x),
                        screenX - textRenderer.getWidth(String.valueOf(x)) / 2,
                        mapCenterY + mapHeight / 2 + 5, 0xFFFFFF);
            }
        }

        for (int z = (minZ / gridSize) * gridSize; z <= maxZ; z += gridSize) {
            int screenY = mapCenterY + (int) ((z - centerZ) * scaleFactor);
            context.fill(mapCenterX - mapWidth / 2, screenY, mapCenterX + mapWidth / 2, screenY + 1, gridColor);

            if (z % 500 == 0) {
                context.drawTextWithShadow(textRenderer, String.valueOf(z),
                        mapCenterX - mapWidth / 2 - 25,
                        screenY - textRenderer.fontHeight / 2, 0xFFFFFF);
            }
        }

        context.drawCenteredTextWithShadow(textRenderer, "X", mapCenterX, mapCenterY + mapHeight / 2 + 20, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, "Z", mapCenterX - mapWidth / 2 - 20, mapCenterY, 0xFFFFFF);

        int spawnX = mapCenterX + (int) ((-centerX) * scaleFactor);
        int spawnZ = mapCenterY + (int) ((-centerZ) * scaleFactor);
        if (spawnX >= mapCenterX - mapWidth / 2 && spawnX <= mapCenterX + mapWidth / 2 &&
                spawnZ >= mapCenterY - mapHeight / 2 && spawnZ <= mapCenterY + mapHeight / 2) {

            context.fill(spawnX - 3, spawnZ - 3, spawnX + 3, spawnZ + 3, 0xFFFFFFFF);
            context.drawTextWithShadow(textRenderer, "Spawn (0,0)", spawnX + 5, spawnZ - 5, 0xFFFFFF);
        }

        LocationPoint hoveredPoint = null;
        int hoveredDistance = Integer.MAX_VALUE;

        for (LocationPoint point : pointsInCurrentDimension) {
            int screenX = mapCenterX + (int) ((point.x - centerX) * scaleFactor);
            int screenY = mapCenterY + (int) ((point.z - centerZ) * scaleFactor);

            if (screenX < mapCenterX - mapWidth / 2 - 15 || screenX > mapCenterX + mapWidth / 2 + 15 ||
                    screenY < mapCenterY - mapHeight / 2 - 15 || screenY > mapCenterY + mapHeight / 2 + 15) {
                continue;
            }

            float normalizedCount = (float) point.count / maxCount;
            int pointSize = (int) (MIN_POINT_SIZE + normalizedCount * (MAX_POINT_SIZE - MIN_POINT_SIZE));

            int color = getHeatmapColor(normalizedCount);

            drawFilledCircle(context, screenX, screenY, pointSize / 2, color);

            int distance = (int) Math.sqrt(Math.pow(mouseX - screenX, 2) + Math.pow(mouseY - screenY, 2));
            if (distance < pointSize / 2 + 2 && distance < hoveredDistance) {
                hoveredPoint = point;
                hoveredDistance = distance;
            }
        }

        if (hoveredPoint != null) {
            List<String> tooltipLines = new ArrayList<>();
            tooltipLines.add("Position: X:" + hoveredPoint.x + " Y:" + hoveredPoint.y + " Z:" + hoveredPoint.z);
            tooltipLines.add("Screenshots: " + hoveredPoint.count);
            tooltipLines.add("Dimension: " + formatDimensionName(hoveredPoint.dimension));

            int tooltipWidth = 0;
            for (String line : tooltipLines) {
                tooltipWidth = Math.max(tooltipWidth, textRenderer.getWidth(line));
            }

            int tooltipX = mouseX + 10;

            if (tooltipX + tooltipWidth + 10 > width) {
                tooltipX = mouseX - tooltipWidth - 10;
            }

            context.fill(tooltipX - 3, mouseY - 3,
                    tooltipX + tooltipWidth + 3, mouseY + tooltipLines.size() * 10 + 3,
                    0xF0100010);
            context.fill(tooltipX - 2, mouseY - 2,
                    tooltipX + tooltipWidth + 2, mouseY + tooltipLines.size() * 10 + 2,
                    0xF0100010);

            for (int i = 0; i < tooltipLines.size(); i++) {
                context.drawTextWithShadow(textRenderer, tooltipLines.get(i), tooltipX, mouseY + i * 10, 0xFFFFFF);
            }
        }

        context.drawCenteredTextWithShadow(textRenderer,
                "Dimension: " + formatDimensionName(selectedDimension) + " | Scale: " + String.format("%.1fx", heatmapScale),
                width / 2, height - 40, 0xFFFFFF);

        context.drawCenteredTextWithShadow(textRenderer,
                "scroll to zoom / move",
                width / 2, height - 25, 0xAAAAAA);
    }

    private void drawFilledCircle(DrawContext context, int centerX, int centerY, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                if (x * x + y * y <= radius * radius) {
                    context.fill(centerX + x, centerY + y, centerX + x + 1, centerY + y + 1, color);
                }
            }
        }
    }

    private int getHeatmapColor(float intensity) {
        int r, g, b;

        float adjIntensity;
        if (intensity < 0.5) {
            adjIntensity = intensity * 2;
            r = (int) (255 * adjIntensity);
            g = 255;
        } else {
            adjIntensity = (intensity - 0.5f) * 2;
            r = 255;
            g = (int) (255 * (1 - adjIntensity));
        }
        b = 0;

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void renderUserDetails(DrawContext context, int mouseX, int mouseY) {
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("User Profile: " + selectedUser).formatted(Formatting.GOLD),
                width / 2, 55, 0xFFFFFF);

        List<StatisticsSection> userSections = userDetailSections.get(selectedUser);

        int y = 75 - scrollOffset;
        for (StatisticsSection section : userSections) {
            if (y + section.getHeight() < 50 || y > height) {
                y += section.getHeight() + SECTION_SPACING;
                continue;
            }

            if (!section.title.isEmpty()) {
                context.drawTextWithShadow(this.textRenderer, section.title, width / 2 - 150, y, 0xFFAA00);
                y += 12;
            }

            for (StatisticsEntry entry : section.entries) {
                if (y >= 50 && y <= height) {
                    boolean isHovering = entry.isClickable &&
                            mouseX >= width / 2 - 145 && mouseX <= width / 2 + 145 &&
                            mouseY >= y - 1 && mouseY <= y + 9;

                    int keyColor = isHovering ? 0xFFFF55 : 0xFFFFFF;
                    context.drawTextWithShadow(this.textRenderer, entry.key, width / 2 - 145, y, keyColor);
                    context.drawTextWithShadow(this.textRenderer, entry.value, width / 2 + 30, y, 0xAAAAAA);

                    if (entry.isClickable) {
                        int textWidth = textRenderer.getWidth(entry.key);
                        context.fill(width / 2 - 145, y + 9, width / 2 - 145 + textWidth, y + 10,
                                isHovering ? 0xFFFFFF55 : 0xFFAAAAAA);
                    }
                }
                y += 10;
            }

            y += SECTION_SPACING;
        }

        int contentHeight = getTotalContentHeight(userSections);
        if (contentHeight > height - 100) {
            if (scrollOffset > 0) {
                context.drawCenteredTextWithShadow(this.textRenderer, "▲", width / 2, 50, 0xFFFFFF);
            }
            if (scrollOffset < contentHeight - (height - 100)) {
                context.drawCenteredTextWithShadow(this.textRenderer, "▼", width / 2, height - 10, 0xFFFFFF);
            }
        }
    }

    private List<StatisticsSection> getTabSections() {
        List<StatisticsSection> filtered = new ArrayList<>();

        switch (currentTab) {
            case 0: // Overview
                for (StatisticsSection section : sections) {
                    if (section.title.contains("Local Screenshots Statistics") ||
                            section.title.contains("Recent Screenshots")) {
                        filtered.add(section);
                    }
                }
                break;
            case 1: // Users
                for (StatisticsSection section : sections) {
                    if (section.title.contains("User Statistics") ||
                            section.title.contains("Activity Patterns")) {
                        filtered.add(section);
                    }
                }
                break;
            case 2: // Screenshots
                for (StatisticsSection section : sections) {
                    if (section.title.contains("File Statistics") ||
                            section.title.contains("World Statistics")) {
                        filtered.add(section);
                    }
                }
                break;
            case 3: // Performance
                for (StatisticsSection section : sections) {
                    if (section.title.contains("Performance Statistics")) {
                        filtered.add(section);
                    }
                }
                break;
        }

        return filtered;
    }

    private int getTotalContentHeight(List<StatisticsSection> sections) {
        int height = 0;
        for (StatisticsSection section : sections) {
            height += section.getHeight() + SECTION_SPACING;
        }
        return height;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount, double verticalAmount) {
        if (currentTab == 4 && isHeatmapActive && !isLoading && errorMessage == null) {
            float oldScale = heatmapScale;

            float zoomFactor = 0.1f;
            heatmapScale += (float) (verticalAmount * zoomFactor);
            heatmapScale = Math.max(0.25f, Math.min(10.0f, heatmapScale));

            int mapCenterX = width / 2 + heatmapOffsetX;
            int mapCenterY = height / 2 + 30 + heatmapOffsetY;

            double relX = mouseX - mapCenterX;
            double relY = mouseY - mapCenterY;

            double scaleRatio = heatmapScale / oldScale;
            heatmapOffsetX -= (int) ((scaleRatio - 1) * relX);
            heatmapOffsetY -= (int) ((scaleRatio - 1) * relY);

            return true;
        }

        List<StatisticsSection> relevantSections = selectedUser != null ?
                userDetailSections.get(selectedUser) : getTabSections();

        int contentHeight = getTotalContentHeight(relevantSections);
        int visibleHeight = selectedUser != null ? height - 100 : height - 80;

        if (contentHeight > visibleHeight) {
            int scrollSpeed = 20;
            scrollOffset -= (int) (verticalAmount * scrollSpeed);
            scrollOffset = Math.max(0, Math.min(scrollOffset, contentHeight - visibleHeight));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (isLoading || errorMessage != null) {
            return false;
        }

        if (currentTab == 4 && isHeatmapActive && button == 0) {
            if (mouseY > 90 && mouseY < height - 50) {
                isDraggingMap = true;
                dragStartX = (int) mouseX;
                dragStartY = (int) mouseY;
                dragStartOffsetX = heatmapOffsetX;
                dragStartOffsetY = heatmapOffsetY;
                return true;
            }
        }

        List<StatisticsSection> sectionsToCheck;
        if (selectedUser != null) {
            sectionsToCheck = userDetailSections.getOrDefault(selectedUser, Collections.emptyList());
        } else {
            sectionsToCheck = getTabSections();
        }

        int y = (selectedUser != null ? 75 : 55) - scrollOffset;
        for (StatisticsSection section : sectionsToCheck) {
            if (y + section.getHeight() < 50 || y > height) {
                y += section.getHeight() + SECTION_SPACING;
                continue;
            }

            y += 12;

            for (StatisticsEntry entry : section.entries) {
                if (y >= 50 && y <= height) {
                    if (entry.isClickable &&
                            mouseX >= (double) width / 2 - 145 && mouseX <= (double) width / 2 + 145 &&
                            mouseY >= y - 1 && mouseY <= y + 9) {
                        entry.onClick.run();
                        if (entry.key.getString().contains("Back to") || entry.key.getString().contains(selectedUser)) {
                            this.init();
                        }
                        return true;
                    }
                }
                y += 10;
            }

            y += SECTION_SPACING;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }

        if (isDraggingMap && button == 0 && currentTab == 4 && isHeatmapActive) {
            heatmapOffsetX = dragStartOffsetX + (int) (mouseX - dragStartX);
            heatmapOffsetY = dragStartOffsetY + (int) (mouseY - dragStartY);
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (super.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }

        if (isDraggingMap && button == 0) {
            isDraggingMap = false;
            return true;
        }

        return false;
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), pre);
    }

    private String formatDimensionName(String dimension) {
        return switch (dimension) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "The Nether";
            case "minecraft:the_end" -> "The End";
            default -> dimension.substring(dimension.indexOf(":") + 1).replace('_', ' ');
        };
    }

    private String getUsername(String name) {
        if (name.split("-").length > 1) {
            if (name.split("-")[1].split("_").length > 1) {
                return name.split("-")[1].split("_")[0];
            }
        }
        return MinecraftClient.getInstance().getSession().getUsername();
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

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
        super.close();
    }

    private static class StatisticsSection {
        private final String title;
        private final List<StatisticsEntry> entries = new ArrayList<>();
        private final Text translatedTitle;

        public StatisticsSection(String title, Text translatedTitle) {
            this.title = title;
            this.translatedTitle = translatedTitle;
        }

        public void addEntry(Text key, Text value) {
            entries.add(new StatisticsEntry(key, value));
        }

        public void makeEntryClickable(Text key, Runnable onClick) {
            for (StatisticsEntry entry : entries) {
                if (entry.key.getString().equals(key.getString())) {
                    entry.isClickable = true;
                    entry.onClick = onClick;
                    break;
                }
            }
        }

        public int getHeight() {
            return 12 + (entries.size() * 10);
        }
    }

    private static class StatisticsEntry {
        private final Text key;
        private final Text value;
        private boolean isClickable = false;
        private Runnable onClick = () -> {
        };

        public StatisticsEntry(Text key, Text value) {
            this.key = key;
            this.value = value;
        }
    }

    private record LocationPoint(int x, int y, int z, String dimension, int count) {
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
}
