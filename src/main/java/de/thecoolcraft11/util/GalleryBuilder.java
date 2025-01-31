package de.thecoolcraft11.util;

import java.io.File;
import java.util.*;

public class GalleryBuilder {

    public static String buildGallery(File[] files, boolean allowDelete) {
        // Prepare a list of images with their associated usernames and player head URLs
        List<Map<String, String>> imagesWithUsernames = files != null
                ? Arrays.stream(files).map(file -> {
            String filename = file.getName();
            String username = filename.split("-")[1].split("_")[0];
            Map<String, String> imageData = new HashMap<>();
            imageData.put("filename", filename);
            imageData.put("username", username);
            imageData.put("playerHeadUrl", "https://mc-heads.net/avatar/" + username + "/50");

            return imageData;
        }).toList()
                : new ArrayList<>();

        // Start building the HTML content
        StringBuilder htmlContent = new StringBuilder();
        htmlContent.append("<!DOCTYPE html>")
                .append("<html lang=\"en\">")
                .append("<head>")
                .append("<meta charset=\"UTF-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
                .append("<link rel=\"stylesheet\" href=\"/static/css/style.css\">")
                .append("<title>Gallery</title>")
                .append("</head>")
                .append("<body>")
                .append("<button id='darkModeToggle' onclick='toggleDarkMode()'>Toggle Dark Mode</button>")
                .append("<h1 class='header' id='header'>Screenshots</h1>")
                .append("<div class='gallery'>");

        // Add each image to the gallery
        for (Map<String, String> imageData : imagesWithUsernames) {
            htmlContent.append("<div class='gallery-item'>")
                    .append("<div class='image-container'>")
                    .append("<img src='/screenshots/").append(imageData.get("filename")).append("' ")
                    .append("alt='").append(imageData.get("filename")).append("' ")
                    .append("class='gallery-image' onclick='showModal(\"/screenshots/").append(imageData.get("filename")).append("\")' loading='lazy'>")
                    .append("<div class='text-with-head'>")
                    .append("<img src='").append(imageData.get("playerHeadUrl")).append("' ")
                    .append("alt='").append(imageData.get("username")).append("' ")
                    .append("class='player-head'>")
                    .append("<div class='image-username'>").append(imageData.get("username")).append("</div>")
                    .append("</div></div></div>");
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
                .append("<button id='slideshowToggle' onclick='toggleSlideshow()'>Start Slideshow</button>")
                .append("<input type='number' id='intervalInput' placeholder='Interval (s)' min='1' value='5'>");

        if (allowDelete) {
            htmlContent.append("<button id='deleteImage' onclick='deleteImage()'>Delete Image</button>");
        }

        htmlContent.append("</div>")
                .append("</div>") // Close modal-left

                // Right side of the modal
                .append("<div id='modal-right'>")
                .append("<div id='commentsContainer'>")
                .append("</div>") // Comments will be dynamically added
                .append("<textarea id='newComment' placeholder='Add a comment...'></textarea>")
                .append("<input type='text' id='commentAuthor' placeholder='Your Name'>")
                .append("<button id='submitComment' onclick='submitComment()'>Submit Comment</button>")
                .append("</div>") // Close modal-right

                .append("</div>") // Close modal-content-wrapper
                .append("</div>"); // Close modal

        // Add JavaScript logic
        htmlContent.append("</div>")
                .append("<div id='thumbnails'></div>")
                .append("</div>");
        htmlContent.append("<script>")
                .append("let currentSrc = '';")
                .append("let currentFilename = '';") // Track the filename
                .append("let currentIndex = 1;")
                .append("let intervalId;")
                .append("let slideshowActive = false;")
                .append("const images = [");

        // Add all image filenames to the JS array
        for (Map<String, String> imageData : imagesWithUsernames) {
            htmlContent.append("'/screenshots/").append(imageData.get("filename")).append("',");
        }

        if (!imagesWithUsernames.isEmpty()) {
            htmlContent.deleteCharAt(htmlContent.length() - 1); // Remove trailing comma
        }

        htmlContent.append("];")
                .append("</script>")
                .append("<script src=\"/static/js/script.js\"></script>")
                .append("</body>")
                .append("</html>");

        return htmlContent.toString();
    }
}
