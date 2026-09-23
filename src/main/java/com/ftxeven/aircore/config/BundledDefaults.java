package com.ftxeven.aircore.config;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.stream.Stream;

public final class BundledDefaults {

    public static final Predicate<String> ALWAYS_PROTECTED = id -> true;

    private final JavaPlugin plugin;
    private final Logger logger;

    public BundledDefaults(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void extract(String root, String jarPrefix, String extension, Set<String> reservedSegments, Predicate<String> isProtected) {
        extract(isFresh(root, extension, reservedSegments), jarPrefix, extension, reservedSegments, isProtected);
    }

    public void extract(boolean fresh, String jarPrefix, String extension, Set<String> reservedSegments, Predicate<String> isProtected) {
        File jarFile;
        try {
            jarFile = new File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            logger.warning("Could not locate the plugin jar to extract bundled '" + jarPrefix + "' defaults: " + e.getMessage());
            return;
        }

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.startsWith(jarPrefix) || !name.endsWith(extension)) {
                    continue;
                }
                String relative = name.substring(jarPrefix.length());
                if (hasReservedSegment(relative, reservedSegments) || new File(plugin.getDataFolder(), name).exists()) {
                    continue;
                }
                String id = relative.substring(0, relative.length() - extension.length());
                if (!fresh && isProtected.test(id)) {
                    continue;
                }
                plugin.saveResource(name, false);
            }
        } catch (IOException e) {
            logger.warning("Could not read the plugin jar to extract bundled '" + jarPrefix + "' defaults: " + e.getMessage());
        }
    }

    public boolean isFresh(String root, String extension, Set<String> reservedSegments) {
        File dir = new File(plugin.getDataFolder(), root);
        if (!dir.isDirectory()) {
            return true;
        }
        Path base = dir.toPath();
        try (Stream<Path> walk = Files.walk(base)) {
            return walk.noneMatch(p -> countsTowardsFreshness(base, p, extension, reservedSegments));
        } catch (IOException e) {
            logger.warning("Could not inspect '" + root + "/' to determine freshness: " + e.getMessage());
            return true;
        }
    }

    private boolean countsTowardsFreshness(Path base, Path candidate, String extension, Set<String> reservedSegments) {
        if (!Files.isRegularFile(candidate) || !candidate.toString().endsWith(extension)) {
            return false;
        }
        Path relative = base.relativize(candidate);
        return !hasReservedSegment(relative, reservedSegments);
    }

    public static boolean hasReservedSegment(Path relative, Set<String> reservedSegments) {
        if (reservedSegments.isEmpty()) {
            return false;
        }
        for (Path segment : relative) {
            if (reservedSegments.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasReservedSegment(String relative, Set<String> reservedSegments) {
        if (reservedSegments.isEmpty()) {
            return false;
        }
        for (String segment : relative.split("/")) {
            if (reservedSegments.contains(segment)) {
                return true;
            }
        }
        return false;
    }
}