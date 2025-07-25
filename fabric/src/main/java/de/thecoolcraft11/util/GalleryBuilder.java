package de.thecoolcraft11.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class GalleryBuilder {

    public static String buildGallery(File[] files, boolean allowDelete, boolean allowEmptyPassphrase, boolean useOldStyle) {
        List<Map<String, Object>> imagesWithUsernames = files != null
                ? Arrays.stream(files).map(file -> {
            String filename = file.getName();
            String base = filename.substring(filename.indexOf("-") + 1, filename.lastIndexOf("."));
            int lastUnderscore = base.lastIndexOf("_");
            String username = lastUnderscore > -1 ? base.substring(0, lastUnderscore) : "?";
            Map<String, Object> imageData = new HashMap<>();
            imageData.put("filename", filename);
            imageData.put("username", username);
            imageData.put("playerHeadUrl", "https://mc-heads.net/avatar/" + username + "/50");

            String jsonFileName = filename.replaceFirst("(?i)\\.(png|jpg|jpeg|gif|bmp|webp)$", ".json");
            File jsonFile = new File(file.getParent(), jsonFileName);

            JsonObject metadata;
            if (jsonFile.exists()) {
                imageData.put("hasMetadata", true);
                try (FileReader reader = new FileReader(jsonFile)) {
                    metadata = JsonParser.parseReader(reader).getAsJsonObject();
                    imageData.put("metadata", metadata);
                } catch (IOException e) {
                    System.err.println("Error reading metadata for " + filename + ": " + e.getMessage());
                }
            } else {
                imageData.put("hasMetadata", false);
            }

            imageData.put("timestamp", file.lastModified());

            return imageData;
        }).toList()
                : new ArrayList<>();

        StringBuilder htmlContent = new StringBuilder();
        htmlContent.append("<!DOCTYPE html>")
                .append("<html lang=\"en\">")
                .append("<head>")
                .append("<meta charset=\"UTF-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
                .append(!useOldStyle ? "<link rel=\"stylesheet\" href=\"/static/css/style.css\">" : "<link rel=\"stylesheet\" href=\"/static/css/styleOld.css\">")
                .append("<title>Gallery</title>")
                .append("<script>")
                .append("(function() {")
                .append("  const darkMode = localStorage.getItem('darkMode');")
                .append("  if (darkMode === 'enabled') {")
                .append("    document.documentElement.classList.add('dark-mode');")
                .append("  }")
                .append("})();")
                .append("</script>")
                .append("</head>")
                .append("<body>")
                .append("<button id='darkModeToggle' onclick='toggleDarkMode()'>Toggle Dark Mode</button>")
                .append("<h1 class='header' id='header'>Screenshots</h1>")
                .append("<div class='search-container'>")
                .append("<input type='text' id='searchInput' placeholder='Search by username, tags, filename, or metadata...' aria-label='Search screenshots'>")
                .append("<button id='searchButton' onclick='searchGallery()'>Search</button>")
                .append("<div class='search-info-container'>")
                .append("<button id='searchInfoToggle' onclick='toggleSearchInfo()'>Search Help</button>")
                .append("<button id='shortenerBtn' onclick='window.location.href=\"/shortener\"'>Go to Shortener</button>")
                .append("<div id='searchInfoBox' class='search-info' style='display:none;'>")
                .append("<h3>Search Tips</h3>")
                .append("<p>You can search using the following formats:</p>")
                .append("<ul>")
                .append("<li><strong>Simple search:</strong> Type any text to search across all enabled fields</li>")
                .append("<li><strong>Field search:</strong> Use format <code>field:value</code> (e.g., <code>biome:desert</code>)</li>")
                .append("<li><strong>Operators:</strong> Use <code>&gt;</code>, <code>&lt;</code>, <code>=</code>, <code>&gt;=</code>, <code>&lt;=</code> (e.g., <code>x:&gt;100</code>)</li>")
                .append("</ul>")
                .append("<p><strong>Available fields:</strong> x, y, z, biome, dimension (dim), username, coordinates, date, seed, world, etc.</p>")
                .append("<p><strong>Examples:</strong></p>")
                .append("<ul>")
                .append("<li><code>x:&gt;100</code> - X coordinate greater than 100</li>")
                .append("<li><code>biome:desert</code> - Search for desert biomes</li>")
                .append("<li><code>date:today</code> - Screenshots from today</li>")
                .append("</ul>")
                .append("<button id='closeSearchInfo' onclick='toggleSearchInfo()'>Close</button>")
                .append("</div>")
                .append("</div>")
                .append("<div id='searchFilters'>")
                .append("<label><input type='checkbox' id='filterUsername' checked> Username</label>")
                .append("<label><input type='checkbox' id='filterFilename' checked> Filename</label>")
                .append("<label><input type='checkbox' id='filterTags' checked> Tags</label>")
                .append("<label><input type='checkbox' id='filterMetadata' checked> Other Metadata</label>")
                .append("<button id='advancedSearchToggle' onclick='toggleAdvancedSearch()'>Advanced Search</button>")
                .append("</div>")
                .append("<div id='advancedSearchOptions' style='display:none;'>")
                .append("<div class='advanced-search-grid'>")
                .append("<label><input type='checkbox' id='filterCoordinates' checked> Coordinates</label>")
                .append("<label><input type='checkbox' id='filterDimension' checked> Dimension</label>")
                .append("<label><input type='checkbox' id='filterBiome' checked> Biome</label>")
                .append("<label><input type='checkbox' id='filterSystemInfo' checked> System Info</label>")
                .append("<label><input type='checkbox' id='filterWorldInfo' checked> World Info</label>")
                .append("<label><input type='checkbox' id='filterClientSettings' checked> Client Settings</label>")
                .append("</div>")
                .append("</div>")
                .append("</div>")
                .append("<p id='noResults' style='display:none;'>No matching screenshots found</p>")
                .append("<div class='gallery'>");

        for (Map<String, Object> imageData : imagesWithUsernames) {
            htmlContent.append("<div class='gallery-item'>")
                    .append("<div class='image-container'>");

            if ((boolean) imageData.get("hasMetadata") && imageData.get("metadata") != null) {
                JsonObject metadata = (JsonObject) imageData.get("metadata");
                if (metadata.has("tags") && metadata.get("tags").isJsonArray() && !metadata.getAsJsonArray("tags").isEmpty()) {
                    String firstTag = metadata.getAsJsonArray("tags").get(0).getAsString();
                    htmlContent.append("<div class='tag-badge'>").append(firstTag).append("</div>");
                }
            }

            htmlContent.append("<img src='/scr/").append(imageData.get("filename")).append("' ")
                    .append("alt='").append(imageData.get("filename")).append("' ")
                    .append("class='gallery-image' onclick='showModal(\"/scr/").append(imageData.get("filename")).append("\")' loading='lazy'>")
                    .append("<div class='text-with-head'>")
                    .append("<img src='").append(imageData.get("playerHeadUrl")).append("' ")
                    .append("alt='").append(imageData.get("username")).append("' ")
                    .append("class='player-head'>")
                    .append("<div class='image-username'>").append(imageData.get("username")).append("</div>")
                    .append("</div>")
                    .append("<button class='like-button' onclick='toggleLike(\"/scr/").append(imageData.get("filename")).append("\", event)'></button>")
                    .append("</div></div>");
        }

        htmlContent.append("</div>")
                .append("<div id='modal' onclick='hideModal(event)'>")
                .append("<div id='modal-content-wrapper'>")
                .append("<span>&times;</span>")
                .append("<div id='loading'></div>")
                .append("<div id='modal-left'>")
                .append("<img id='modalImage' src='' alt='Full Image'>")
                .append("<div id='imageCount'></div>")
                .append("<div id='buttonContainer'>")
                .append("<button id='prevBtn' onclick='previousImage()'>Previous</button>")
                .append("<button id='nextBtn' onclick='nextImage()'>Next</button>")
                .append("<button id='fullscreenBtn' onclick='toggleFullScreen()'>Fullscreen</button>")
                .append("<button id='downloadBtn' onclick='downloadImage()'>Download</button>")
                .append("<button id='randomBtn' onclick='randomImage()'>Random Image</button>")
                .append("<button id='likeButton' onclick='toggleLike(currentSrc)'>Like</button>")
                .append("<button id='slideshowToggle' onclick='toggleSlideshow()'>Start Slideshow</button>")
                .append("<input type='number' id='intervalInput' placeholder='Interval (s)' min='1' value='5'>");

        if (allowDelete) {
            htmlContent.append("<div id='deleteControls' style='display:flex;align-items:center;margin-top:10px;'>");
            if (!allowEmptyPassphrase) {
                htmlContent.append("<input type='password' id='deletePassphrase' placeholder='Deletion Passphrase' style='margin-right:10px;'>");
            }
            htmlContent.append("<button id='deleteImage' onclick='deleteImage()'>Delete Image</button>");
            htmlContent.append("</div>");
        }

        htmlContent.append("</div>")
                .append("</div>")
                .append("<div id='modal-right'>")
                .append("<div id='metadataContainer'>")
                .append("<h3>Screenshot Info</h3>")
                .append("<div id='screenshotMetadata'></div>")
                .append("</div>")
                .append("<div id='commentsContainer'>")
                .append("</div>")
                .append("</div>");
        htmlContent.append("</div>")
                .append("</div>");
        htmlContent.append("<script>")
                .append("let currentSrc = '';")
                .append("let currentFilename = '';")
                .append("let currentIndex = 1;")
                .append("let intervalId;")
                .append("let slideshowActive = false;")
                .append("const images = [");

        for (Map<String, Object> imageData : imagesWithUsernames) {
            htmlContent.append("'/scr/").append(imageData.get("filename")).append("',");
        }

        if (!imagesWithUsernames.isEmpty()) {
            htmlContent.deleteCharAt(htmlContent.length() - 1);
        }

        htmlContent.append("];")
                .append("const imageMetadata = {};");

        for (Map<String, Object> imageData : imagesWithUsernames) {
            htmlContent.append("imageMetadata['/scr/")
                    .append(imageData.get("filename"))
                    .append("'] = {")
                    .append("username:'").append(imageData.get("username")).append("',")
                    .append("filename:'").append(imageData.get("filename")).append("',")
                    .append("timestamp:").append(imageData.get("timestamp")).append(",")
                    .append("hasMetadata:").append(imageData.get("hasMetadata")).append(",");

            if ((boolean) imageData.get("hasMetadata") && imageData.get("metadata") != null) {
                JsonObject metadata = (JsonObject) imageData.get("metadata");
                htmlContent.append("metadata:{");

                if (metadata.has("coordinates")) {
                    htmlContent.append("coordinates:\"").append(metadata.get("coordinates").getAsString().replace("\"", "\\\"")).append("\",");
                }

                if (metadata.has("dimension")) {
                    htmlContent.append("dimension:\"").append(metadata.get("dimension").getAsString().replace("\"", "\\\"")).append("\",");
                }

                if (metadata.has("biome")) {
                    htmlContent.append("biome:\"").append(metadata.get("biome").getAsString().replace("\"", "\\\"")).append("\",");
                }

                if (metadata.has("system_info")) {
                    htmlContent.append("system_info:\"").append(metadata.get("system_info").getAsString().replace("\"", "\\\"")).append("\",");
                }

                if (metadata.has("world_info")) {
                    htmlContent.append("world_info:\"").append(metadata.get("world_info").getAsString().replace("\"", "\\\"")).append("\",");
                }

                if (metadata.has("current_time")) {
                    htmlContent.append("current_time:\"").append(metadata.get("current_time").getAsString().replace("\"", "\\\"")).append("\",");
                }

                if (metadata.has("client_settings")) {
                    htmlContent.append("client_settings:\"").append(metadata.get("client_settings").getAsString().replace("\"", "\\\"")).append("\",");
                }

                if (metadata.has("comments")) {
                    htmlContent.append("comments:").append(metadata.get("comments")).append(",");
                }

                if (metadata.has("tags")) {
                    htmlContent.append("tags:").append(metadata.get("tags")).append(",");
                }

                if (htmlContent.charAt(htmlContent.length() - 1) == ',') {
                    htmlContent.deleteCharAt(htmlContent.length() - 1);
                }

                htmlContent.append("}");
            }

            htmlContent.append("};");
        }

        htmlContent.append("</script>")
                .append("<script src=\"/static/js/script.js\"></script>")
                .append("</body>")
                .append("</html>");

        return htmlContent.toString();
    }
}
