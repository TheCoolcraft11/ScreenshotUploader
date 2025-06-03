package de.thecoolcraft11.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ServerStatisticsScreen extends Screen {
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

    private String serverUrl;

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

    public ServerStatisticsScreen(Screen parent, String serverUrl) {
        super(Text.translatable("screen.screenshot_uploader.statistics.title"));
        this.serverUrl = serverUrl;
        this.parent = parent;
        fetchStatistics();
    }

    private void fetchStatistics() {
        CompletableFuture.runAsync(() -> {
            try {
                if (!serverUrl.endsWith("/")) {
                    serverUrl = serverUrl.substring(0, serverUrl.lastIndexOf('/') + 1);
                }
                URL url;
                try {
                    url = new URI(serverUrl + "statistics").toURL();
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        statisticsData = JsonParser.parseString(response.toString()).getAsJsonObject();
                        processStatisticsData();
                    }
                } else {
                    errorMessage = "Failed to fetch statistics: HTTP " + responseCode;
                }
            } catch (IOException e) {
                errorMessage = "Error: " + e.getMessage();
            } finally {
                isLoading = false;
            }
        });
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
                    StatisticsSection serverSection = new StatisticsSection("Server Statistics", Text.translatable("gui.screenshot_uploader.server_statistics.server_statistics"));

                    int totalScreenshots = serverStats.has("totalScreenshots") ?
                            serverStats.get("totalScreenshots").getAsInt() : 0;
                    serverSection.addEntry(Text.translatable("gui.screenshot_uploader.server_statistics.total_screenshots"), Text.of(String.valueOf(totalScreenshots)));

                    if (serverStats.has("totalFileSizeBytes")) {
                        long totalSize = serverStats.get("totalFileSizeBytes").getAsLong();
                        serverSection.addEntry(Text.translatable("gui.screenshot_uploader.server_statistics.total_size"), Text.of(formatFileSize(totalSize)));
                    }

                    if (serverStats.has("averageFileSizeBytes")) {
                        double avgSize = serverStats.get("averageFileSizeBytes").getAsDouble();
                        serverSection.addEntry(Text.translatable("gui.screenshot_uploader.server_statistics.average_size"), Text.of(formatFileSize((long) avgSize)));
                    }

                    sections.add(serverSection);
                }

                if (globalStats.has("fileStats")) {
                    JsonObject fileStats = globalStats.getAsJsonObject("fileStats");
                    StatisticsSection fileSection = new StatisticsSection("File Statistics", Text.translatable("gui.screenshot_uploader.server_statistics.file_statistics"));

                    if (fileStats.has("typeDistribution")) {
                        JsonObject typeDistribution = fileStats.getAsJsonObject("typeDistribution");
                        fileSection.addEntry(Text.translatable("gui.screenshot_uploader.server_statistics.file_types"), Text.empty());
                        for (Map.Entry<String, JsonElement> entry : typeDistribution.entrySet()) {
                            fileSection.addEntry(Text.of("  " + entry.getKey()), Text.of(entry.getValue().getAsString()));
                        }
                    }

                    if (fileStats.has("sizeDistribution")) {
                        JsonObject sizeDistribution = fileStats.getAsJsonObject("sizeDistribution");
                        fileSection.addEntry(Text.translatable("gui.screenshot_uploader.server_statistics.file_distribution"), Text.empty());
                        for (Map.Entry<String, JsonElement> entry : sizeDistribution.entrySet()) {
                            fileSection.addEntry(Text.of("  " + entry.getKey()), Text.of(entry.getValue().getAsString()));
                        }
                    }

                    sections.add(fileSection);
                }

                if (globalStats.has("worldStats")) {
                    JsonObject worldStats = globalStats.getAsJsonObject("worldStats");
                    StatisticsSection worldSection = new StatisticsSection("World Statistics", Text.translatable("gui.screenshot_uploader.server_statistics.world_statistics"));

                    if (worldStats.has("dimensions")) {
                        JsonObject dimensions = worldStats.getAsJsonObject("dimensions");
                        worldSection.addEntry(Text.translatable("gui.screenshot_uploader.server_statistics.dimensions"), Text.empty());
                        for (Map.Entry<String, JsonElement> entry : dimensions.entrySet()) {
                            worldSection.addEntry(Text.translatable("  " + entry.getKey()), Text.of(entry.getValue().getAsString()));
                        }
                    }

                    if (worldStats.has("biomes")) {
                        JsonObject biomes = worldStats.getAsJsonObject("biomes");
                        worldSection.addEntry(Text.translatable("gui.screenshot_uploader.server_statistics.biomes"), Text.empty());
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
                StatisticsSection performanceSection = new StatisticsSection("Performance Statistics", Text.translatable("gui.screenshot_uploader.server_statistics.performance_statistics"));

                if (performanceStats.has("uploadsPerDay")) {
                    JsonObject uploadsPerDay = performanceStats.getAsJsonObject("uploadsPerDay");
                    performanceSection.addEntry(Text.translatable("gui.screenshot_uploader.server_statistics.average_daily_upload"), Text.of(calculateAverageUploads(uploadsPerDay)));

                    performanceSection.addEntry(Text.translatable("gui.screenshot_uploader.server_statistics.daily_upload_count"), Text.of(calculateAverageUploads(uploadsPerDay)));

                    List<Map.Entry<String, JsonElement>> sortedUploads = new ArrayList<>(uploadsPerDay.entrySet());
                    sortedUploads.sort(Map.Entry.<String, JsonElement>comparingByKey().reversed());

                    int dayCount = 0;
                    for (Map.Entry<String, JsonElement> entry : sortedUploads) {
                        if (dayCount++ < 7) {
                            performanceSection.addEntry(Text.of("  " + entry.getKey()), Text.translatable("gui.screenshot_uploader.server_statistics.performance_statistics.uploads_per_day", entry.getValue().getAsInt()));
                        }
                    }
                }

                if (performanceStats.has("weeklyMovingAverage")) {
                    JsonObject weeklyAverage = performanceStats.getAsJsonObject("weeklyMovingAverage");
                    performanceSection.addEntry(Text.translatable("gui.screenshot_uploader.server_statistics.weekly_moving_average"), Text.empty());

                    List<Map.Entry<String, JsonElement>> sortedWeekly = new ArrayList<>(weeklyAverage.entrySet());
                    sortedWeekly.sort(Map.Entry.<String, JsonElement>comparingByKey().reversed());

                    int weekCount = 0;
                    for (Map.Entry<String, JsonElement> entry : sortedWeekly) {
                        if (weekCount++ < 5) {
                            performanceSection.addEntry(Text.of("  " + entry.getKey()),
                                    Text.translatable("gui.screenshot_uploader.server_statistics.performance_statistics.weekly_average", entry.getValue().getAsInt()));
                        }
                    }
                }

                sections.add(performanceSection);
            }

            if (statisticsData.has("recentUploads")) {
                StatisticsSection recentSection = new StatisticsSection("gui.screenshot_uploader.server", Text.translatable("gui.screenshot_uploader.server_statistics.recent_uploads"));
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
        StatisticsSection userSection = new StatisticsSection("User Statistics", Text.translatable("gui.screenshot_uploader.server_statistics.users_statistics"));

        userSection.addEntry(Text.translatable("gui.screenshot_uploader.server_statistics.total_users"), Text.of(String.valueOf(userStats.size())));

        int totalUploads = 0;
        for (Map.Entry<String, JsonElement> entry : userStats.entrySet()) {
            JsonObject userData = entry.getValue().getAsJsonObject();
            totalUploads += userData.get("uploadCount").getAsInt();
        }
        userSection.addEntry(Text.translatable("gui.screenshot_uploader.server_statistics.total_uploads"), Text.of(String.valueOf(totalUploads)));

        double avgUploadsPerUser = !userStats.isEmpty() ? (double) totalUploads / userStats.size() : 0;
        userSection.addEntry(Text.translatable("gui.screenshot_uploader.server_statistics.average_upload_per_user"), Text.translatable("gui.screenshot_uploader.server_statistics.user_statistics.average_uploads_per_user", avgUploadsPerUser));

        List<Map.Entry<String, JsonElement>> sortedUsers = new ArrayList<>(userStats.entrySet());
        sortedUsers.sort((u1, u2) -> {
            int count1 = u1.getValue().getAsJsonObject().get("uploadCount").getAsInt();
            int count2 = u2.getValue().getAsJsonObject().get("uploadCount").getAsInt();
            return count2 - count1;
        });

        userSection.addEntry(Text.translatable("gui.screenshot_uploader.server_statistics.top_contributers"), Text.empty());
        int userCount = 0;
        for (Map.Entry<String, JsonElement> entry : sortedUsers) {
            if (userCount++ < 10) {
                JsonObject userData = entry.getValue().getAsJsonObject();
                int uploadCount = userData.get("uploadCount").getAsInt();
                String username = entry.getKey();

                Text text = Text.of(username);

                userSection.addEntry(text, Text.translatable("gui.screenshot_uploader.server_statistics.user_statistics.top_contributors.entry", uploadCount));
                userSection.makeEntryClickable(text, () -> {
                    selectedUser = username;
                    scrollOffset = 0;
                });
            }
        }

        StatisticsSection activitySection = new StatisticsSection("User Activity Patterns", Text.translatable("gui.screenshot_uploader.server_statistics.activity_patterns"));

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

        activitySection.addEntry(Text.translatable("gui.screenshot_uploader.server_statistics.peak_activity_times"), Text.empty());
        int hourCount = 0;
        for (Map.Entry<String, Integer> entry : sortedHours) {
            if (hourCount++ < 5) {
                activitySection.addEntry(Text.of("  " + entry.getKey()), Text.translatable("gui.screenshot_uploader.server_statistics.activity_patterns.entry", entry.getValue()));
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

            StatisticsSection overviewSection = new StatisticsSection(username + "'s Statistics", Text.translatable("gui.screenshot_uploader.server_statistics.user_statistics", username));
            overviewSection.addEntry(Text.translatable("gui.screenshot_uploader.server_statistics.total_uploads"), Text.of(String.valueOf(userData.get("uploadCount").getAsInt())));

            if (userData.has("totalSizeBytes")) {
                long totalSize = userData.get("totalSizeBytes").getAsLong();
                overviewSection.addEntry(Text.translatable("gui.screenshot_uploader.server_statistics.totalSize"), Text.of(formatFileSize(totalSize)));
            }

            if (userData.has("averageFileSizeBytes")) {
                double avgSize = userData.get("averageFileSizeBytes").getAsDouble();
                overviewSection.addEntry(Text.translatable("gui.screenshot_uploader.server_statistics.average_file_size"), Text.of(formatFileSize((long) avgSize)));
            }

            if (userData.has("mostActiveTime")) {
                overviewSection.addEntry(Text.translatable("gui.screenshot_uploader.server_statistics.most_active_time"), Text.of(userData.get("mostActiveTime").getAsString()));
            }

            userSections.add(overviewSection);

            if (userData.has("activityByHour")) {
                StatisticsSection activitySection = new StatisticsSection("Activity By Hour", Text.translatable("gui.screenshot_uploader.server_statistics.activity_by_hour"));

                JsonObject hourData = userData.getAsJsonObject("activityByHour");

                List<Map.Entry<String, JsonElement>> sortedHours = new ArrayList<>(hourData.entrySet());
                sortedHours.sort((h1, h2) -> h2.getValue().getAsInt() - h1.getValue().getAsInt());

                for (Map.Entry<String, JsonElement> hourEntry : sortedHours) {
                    String hour = hourEntry.getKey();
                    int count = hourEntry.getValue().getAsInt();
                    activitySection.addEntry(Text.of(hour), Text.translatable("gui.screenshot_uploader.server_statistics.activity_by_hour.entry", count));
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

                if (dimension.equals(selectedDimension)) {
                    dimensionButton.active = false;
                }

                this.addDrawableChild(dimensionButton);
            }
        }

        this.addDrawableChild(ButtonWidget.builder(Text.of("Refresh"), button -> {
            isLoading = true;
            errorMessage = null;
            fetchStatistics();
        }).dimensions(width - 80, 25, 70, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"), button -> close())
                .dimensions(10, 25, 50, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
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

    private String formatDimensionName(String dimension) {
        return switch (dimension) {
            case "overworld" -> "Overworld";
            case "the_nether" -> "The Nether";
            case "the_end" -> "The End";
            default -> dimension.substring(0, 1).toUpperCase() + dimension.substring(1)
                    .replace('_', ' ');
        };
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
                    if (section.title.contains("Server Statistics") ||
                            section.title.contains("Recent Uploads")) {
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
                if (entry.key.contains(key)) {
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

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
        super.close();
    }
}

