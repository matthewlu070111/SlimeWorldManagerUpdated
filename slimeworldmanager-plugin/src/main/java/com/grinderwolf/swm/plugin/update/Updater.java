package com.grinderwolf.swm.plugin.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grinderwolf.swm.plugin.SWMPlugin;
import com.grinderwolf.swm.plugin.config.ConfigManager;
import com.grinderwolf.swm.plugin.locale.Messages;
import com.grinderwolf.swm.plugin.log.Logging;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class Updater implements Listener {

    private static final String GITHUB_REPO = "matthewlu070111/SlimeWorldManagerUpdated";
    private static final String GITHUB_RELEASES_API =
            "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";
    private static final String GITHUB_RELEASES_PAGE =
            "https://github.com/" + GITHUB_REPO + "/releases";

    private final boolean outdatedVersion;

    public Updater() {
        String currentVersionString = SWMPlugin.getInstance().getDescription().getVersion();

        if (currentVersionString.equals("${project.version}")) {
            Logging.warning(Messages.get("updater.custom-version"));
            outdatedVersion = false;
            return;
        }

        Version currentVersion = new Version(currentVersionString);

        if (currentVersion.getTag().toLowerCase().endsWith("snapshot")) {
            Logging.warning(Messages.get("updater.snapshot"));
            outdatedVersion = false;
            return;
        }

        Logging.info(Messages.get("updater.checking"));
        Version latestVersion;

        try {
            latestVersion = new Version(getLatestVersion());
        } catch (IOException ex) {
            Logging.error(Messages.get("updater.failed"));
            outdatedVersion = false;
            ex.printStackTrace();
            return;
        } catch (IllegalArgumentException ex) {
            Logging.error(Messages.get("updater.failed") + " " + ex.getMessage());
            outdatedVersion = false;
            return;
        }

        int result = latestVersion.compareTo(currentVersion);
        outdatedVersion = result > 0;

        if (result == 0) {
            Logging.info(Messages.get("updater.latest"));
        } else if (outdatedVersion) {
            Logging.warning(Messages.get("updater.outdated"));
        } else {
            Logging.warning(Messages.get("updater.unreleased"));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (outdatedVersion && ConfigManager.getMainConfig().getUpdaterOptions().isMessageEnabled() && player.hasPermission("swm.updater")) {
            player.sendMessage(Logging.COMMAND_PREFIX + Messages.get("updater.outdated-player"));
        }
    }

    /**
     * Fetches the latest release tag from GitHub for this fork.
     * Tags are expected as {@code v2.3.0} or {@code 2.3.0}; a leading {@code v} is stripped.
     */
    private static String getLatestVersion() throws IOException {
        URL url = new URL(GITHUB_RELEASES_API);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.addRequestProperty("User-Agent", "SlimeWorldManager/" + SWMPlugin.getInstance().getDescription().getVersion());
        connection.addRequestProperty("Accept", "application/vnd.github+json");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setUseCaches(false);

        int code = connection.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            throw new IOException("GitHub API returned HTTP " + code + " for " + GITHUB_RELEASES_API
                    + " (see " + GITHUB_RELEASES_PAGE + ")");
        }

        StringBuilder content = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String input;
            while ((input = br.readLine()) != null) {
                content.append(input);
            }
        }

        JsonObject release = new JsonParser().parse(content.toString()).getAsJsonObject();
        if (!release.has("tag_name") || release.get("tag_name").isJsonNull()) {
            throw new IOException("GitHub release response has no tag_name");
        }

        String tag = release.get("tag_name").getAsString().trim();
        if (tag.startsWith("v") || tag.startsWith("V")) {
            tag = tag.substring(1);
        }
        return tag;
    }
}
