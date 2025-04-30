package de.thecoolcraft11.config.data;

import de.thecoolcraft11.config.value.Comment;

public class ServerConfig {
    @Comment("Determines if an integrated webserver with a screenshot gallery should be started")
    public boolean screenshotWebserver = true;
    @Comment("Port of the Webserver")
    public int port = 4567;
    @Comment("Allow EVERYONE with the passphrase to delete screenshots")
    public boolean allowDelete = false;
    @Comment("Passphrase for deleting screenshots from, ONLY SHARE WITH TRUSTED PEOPLE, Leave empty to allow everyone to delete screenshots")
    public String deletionPassphrase = "";
    @Comment("The public URL of the webserver !(must be configured for proper functionality)")
    public String websiteURL = "";
    @Comment("if the url with a list of all screenshots should be sent to the client(required for ingame server gallery to work)")
    public boolean sendGalleryUrlToClient = true;
    @Comment(" If the server should send an uploadUrl to the clients")
    public boolean sendUrlToClient = true;
    @Comment("Replace the server's upload URL with a custom one sent to the player")
    public boolean useCustomWebURL = false;
    @Comment("The custom URL to send to players")
    public String customWebURL = "";
    @Comment("If the server should send a Discord Message in a channel (Requires Thread Channel webhookUrl needs to be set in order to work)")
    public boolean sendDiscordWebhook = false;
    @Comment("The Url of the Discord webhook")
    public String webhookUrl = "";
    @Comment("If the server should delete screenshots older than a certain amount of days")
    public boolean deleteOldScreenshots = false;
    @Comment("The amount of days after which the screenshots should be deleted")
    public int deleteAfterDays = 30;
    @Comment("If static files should be replaced on server start, Turn off if you want to use custom js / css, Note: You wont be able to use new features if you turn this off")
    public boolean replaceStaticFilesOnStart = true;
}

