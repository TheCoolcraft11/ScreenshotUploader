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
            String response = GalleryBuilder.buildGallery(files, ConfigManager.getServerConfig().allowDelete);
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

            String requestURI = exchange.getRequestURI().toString();
            Matcher matcher = DELETE_PATTERN.matcher(requestURI);

            if (matcher.matches()) {
                String filename = matcher.group(1);
                Path gameDir = FabricLoader.getInstance().getGameDir();
                Path targetFile = gameDir.resolve("./screenshotUploader/screenshots/" + filename);

                try {
                    if (Files.exists(targetFile)) {
                        Files.delete(targetFile);
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
                Path commentFile = gameDir.resolve("./screenshotUploader/screenshots/" + filename.replace(".png", ".json"));

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

}

