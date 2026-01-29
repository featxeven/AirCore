package com.ftxeven.aircore.config;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class LangManager {

    private final AirCore plugin;
    private FileConfiguration lang;

    public LangManager(AirCore plugin) {
        this.plugin = plugin;
        extractAllLanguages();
        load();
    }

    private void extractAllLanguages() {
        File messagesFolder = new File(plugin.getDataFolder(), "lang/messages");

        if (!messagesFolder.exists()) {
            messagesFolder.mkdirs();
        }

        try {
            File pluginFile = new File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());

            if (pluginFile.isFile() && pluginFile.getName().endsWith(".jar")) {
                extractFromJar(pluginFile, messagesFolder);
            } else {
                extractFromIDE(messagesFolder);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to extract language files: " + e.getMessage());
        }
    }

    private void extractFromJar(File jarFile, File targetFolder) {
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                if (entry.getName().startsWith("lang/messages/") && entry.getName().endsWith(".yml")) {
                    String fileName = entry.getName().replace("lang/messages/", "");
                    File targetFile = new File(targetFolder, fileName);

                    if (!targetFile.exists()) {
                        try (InputStream input = jar.getInputStream(entry)) {
                            Files.copy(input, targetFile.toPath());
                        }
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to extract language files from JAR: " + e.getMessage());
        }
    }

    private void extractFromIDE(File targetFolder) {
        try {
            File resourceLangFolder = new File(plugin.getDataFolder().getParentFile().getParentFile(),
                    "src/main/resources/lang/messages");

            if (resourceLangFolder.exists() && resourceLangFolder.isDirectory()) {
                File[] files = resourceLangFolder.listFiles((dir, name) -> name.endsWith(".yml"));

                if (files != null) {
                    for (File file : files) {
                        File targetFile = new File(targetFolder, file.getName());
                        if (!targetFile.exists()) {
                            Files.copy(file.toPath(), targetFile.toPath());
                        }
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to extract language files from IDE: " + e.getMessage());
        }
    }

    private void load() {
        String locale = plugin.config().getLocale();
        if (locale.isBlank()) locale = "en_US";

        File file = new File(plugin.getDataFolder(), "lang/messages/" + locale + ".yml");

        if (!file.exists()) {
            plugin.getLogger().warning("Language file not found: " + locale + ".yml. Falling back to en_US");
            file = new File(plugin.getDataFolder(), "lang/messages/en_US.yml");
        }

        lang = YamlConfiguration.loadConfiguration(file);
    }

    public void reload() {
        load();
        MessageUtil.init(plugin);
    }

    public String get(String key) {
        return lang.getString(key, "<red>Missing lang key: " + key + "</red>");
    }
}
