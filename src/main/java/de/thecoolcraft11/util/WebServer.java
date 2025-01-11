package de.thecoolcraft11.util;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import de.thecoolcraft11.util.config.ConfigManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebServer {

    public static void startWebServer(String ipAddress, int port) throws Exception {



        HttpServer server = HttpServer.create(new InetSocketAddress(ipAddress, port), 0);

        server.createContext("/", new RootHandler());
        server.createContext("/random-screenshot", new RandomScreenshotHandler());
        server.createContext("/delete", new DeleteFileHandler());
        server.createContext("/static", new StaticFileHandler());
        server.createContext("/screenshots", new ScreenshotFileHandler());

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
}

