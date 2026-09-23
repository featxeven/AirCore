package com.ftxeven.aircore.util;

import com.google.gson.JsonParser;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class Version {

    private static final String VERSION_URL = "https://api.spiget.org/v2/resources/130425/versions/latest";
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final Plugin PLUGIN = JavaPlugin.getProvidingPlugin(Version.class);

    private static volatile String latestVersion;
    private static volatile boolean outdated;

    private Version() {
    }

    public static void check() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(VERSION_URL))
                .timeout(Duration.ofSeconds(10))
                .build();

        CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(Version::apply)
                .exceptionally(e -> {
                    PLUGIN.getLogger().warning("Couldn't check for updates: " + e.getMessage());
                    return null;
                });
    }

    private static void apply(HttpResponse<String> response) {
        latestVersion = JsonParser.parseString(response.body()).getAsJsonObject().get("name").getAsString();

        String current = PLUGIN.getPluginMeta().getVersion();
        outdated = !current.equals(latestVersion);

        if (outdated) {
            PLUGIN.getLogger().warning("Outdated! Running " + current + ", latest is " + latestVersion);
        } else {
            PLUGIN.getLogger().info("Running the latest version (" + current + ")");
        }
    }

    public static String current() { return PLUGIN.getPluginMeta().getVersion(); }

    public static boolean isOutdated() { return outdated; }

    public static String getLatest() { return latestVersion; }
}