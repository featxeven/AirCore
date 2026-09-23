package com.ftxeven.aircore.database.migration;

import com.ftxeven.aircore.config.StorageConfig;
import com.ftxeven.aircore.database.migration.MigrationRunner.Migration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class MigrationLoader {

    private static final Pattern FILE_PATTERN = Pattern.compile("V(\\d+)__(.+)\\.(sql|json)");

    private final JavaPlugin plugin;

    public MigrationLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // mariadb reuses mysql's migrations, same SQL dialect
    public static String folderFor(StorageConfig.Driver driver) {
        return switch (driver) {
            case SQLITE -> "sqlite";
            case MYSQL, MARIADB -> "mysql";
            case MONGODB -> "mongodb";
        };
    }

    public List<Migration> load(String folder) throws IOException {
        String prefix = "migrations/" + folder + "/";
        File source = codeSourceFile();

        List<Migration> migrations = source.isDirectory()
                ? loadFromDirectory(source, prefix)
                : loadFromJar(source, prefix);

        migrations.sort(Comparator.comparingInt(Migration::version));
        return migrations;
    }

    private List<Migration> loadFromJar(File jarFile, String prefix) throws IOException {
        List<Migration> migrations = new ArrayList<>();
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().startsWith(prefix)) {
                    continue;
                }
                parse(entry.getName().substring(prefix.length()), () -> jar.getInputStream(entry))
                        .ifPresent(migrations::add);
            }
        }
        return migrations;
    }

    private List<Migration> loadFromDirectory(File classesDir, String prefix) throws IOException {
        Path directory = classesDir.toPath().resolve(prefix);
        if (!Files.isDirectory(directory)) {
            return new ArrayList<>();
        }

        List<Migration> migrations = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.toList()) {
                parse(file.getFileName().toString(), () -> Files.newInputStream(file))
                        .ifPresent(migrations::add);
            }
        }
        return migrations;
    }

    private Optional<Migration> parse(String fileName, InputStreamSupplier opener) throws IOException {
        Matcher matcher = FILE_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            plugin.getLogger().warning("Skipping migration file with invalid name: " + fileName);
            return Optional.empty();
        }

        int version = Integer.parseInt(matcher.group(1));
        String description = matcher.group(2).replace('_', ' ');
        try (InputStream in = opener.open()) {
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return Optional.of(new Migration(version, description, fileName, content));
        }
    }

    private File codeSourceFile() {
        URL location = plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
        try {
            return new File(location.toURI());
        } catch (URISyntaxException e) {
            return new File(location.getPath());
        }
    }

    @FunctionalInterface
    private interface InputStreamSupplier {
        InputStream open() throws IOException;
    }
}