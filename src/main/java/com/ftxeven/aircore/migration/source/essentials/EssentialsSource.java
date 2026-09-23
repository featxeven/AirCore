package com.ftxeven.aircore.migration.source.essentials;

import com.ftxeven.aircore.command.player.ToggleKey;
import com.ftxeven.aircore.database.repository.LocationRepository;
import com.ftxeven.aircore.migration.MigrationSource;
import com.ftxeven.aircore.migration.model.LegacyPlayer;
import com.ftxeven.aircore.migration.model.LegacyServerData;
import com.ftxeven.aircore.migration.util.MigrationLocations;
import com.ftxeven.aircore.model.Position;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class EssentialsSource implements MigrationSource {

    @Override
    public String id() {
        return "essentials";
    }

    @Override
    public String displayName() {
        return "EssentialsX";
    }

    @Override
    public String defaultPath() {
        return "plugins/Essentials";
    }

    @Override
    public String pathHint() {
        return "Point this at the EssentialsX plugin folder, e.g. /aircore migrate essentials plugins/Essentials";
    }

    @Override
    public List<String> notMigrated() {
        return List.of("inventories (EssentialsX doesn't store them)", "mail", "bans, mutes and jails",
                "kits (EssentialsX stores them as text, not as items)", "powertools, unlimited blocks, ip addresses");
    }

    @Override
    public Reader open(Path path) throws IOException {
        Path root = path;
        if (Files.isDirectory(path) && path.getFileName().toString().equalsIgnoreCase("userdata")) {
            root = path.getParent();
        }

        Path userdata = root.resolve("userdata");
        if (!Files.isDirectory(userdata)) {
            throw new IOException("No 'userdata' folder inside " + root);
        }
        return new EssentialsReader(root, userdata);
    }

    private static final class EssentialsReader implements Reader {

        private static final Pattern LOGIN = Pattern.compile("\\s*login:\\s*['\"]?(\\d+)['\"]?\\s*");

        private final Path root;
        private final Path userdata;

        private EssentialsReader(Path root, Path userdata) {
            this.root = root;
            this.userdata = userdata;
        }

        @Override
        public List<Candidate> players() throws IOException {
            List<Candidate> candidates = new ArrayList<>();
            try (Stream<Path> files = Files.list(userdata)) {
                for (Path file : files.toList()) {
                    String fileName = file.getFileName().toString();
                    if (!fileName.toLowerCase(Locale.ROOT).endsWith(".yml")) {
                        continue;
                    }
                    UUID uuid = parseUuid(fileName.substring(0, fileName.length() - 4));
                    if (uuid == null) {
                        continue;
                    }
                    candidates.add(new Candidate(uuid, "", loginTimestamp(file), fileName));
                }
            }
            candidates.sort(Comparator.comparingLong(Candidate::order));
            return candidates;
        }

        private long loginTimestamp(Path file) {
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher matcher = LOGIN.matcher(line);
                    if (matcher.matches()) {
                        return Long.parseLong(matcher.group(1));
                    }
                }
            } catch (Exception ignored) {
                // fall through to the file's own timestamp
            }
            try {
                return Files.getLastModifiedTime(file).toMillis();
            } catch (IOException e) {
                return 0L;
            }
        }

        @Override
        public Optional<LegacyPlayer> load(Candidate candidate) {
            Path file = userdata.resolve(candidate.origin());
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());

            if (yaml.getBoolean("npc", false)) {
                return Optional.empty();
            }

            String name = string(yaml, "last-account-name", "lastAccountName");
            if (name == null || name.isBlank()) {
                return Optional.empty();
            }

            LegacyPlayer.Builder builder = LegacyPlayer.builder(candidate.uuid(), name.strip())
                    .balance(money(yaml.get("money")))
                    .nickname(yaml.getString("nickname"))
                    .godMode(bool(yaml, "godmode"))
                    .flightAllowed(bool(yaml, "flymode"))
                    .toggle(ToggleKey.TP, bool(yaml, "teleportenabled"))
                    .toggle(ToggleKey.TP_AUTO_ACCEPT, bool(yaml, "teleportauto"))
                    .toggle(ToggleKey.SOCIAL_SPY, bool(yaml, "socialspy"))
                    .toggle(ToggleKey.PAY, bool(yaml, "accepting-pay", "acceptingPay"))
                    .toggle(ToggleKey.MSG, bool(yaml, "accepting-messages", "acceptingMessages"));

            MigrationLocations.read(yaml.getConfigurationSection("logoutlocation"))
                    .or(() -> MigrationLocations.read(yaml.getConfigurationSection("lastlocation")))
                    .ifPresent(builder::lastLocation);

            ConfigurationSection homes = yaml.getConfigurationSection("homes");
            if (homes != null) {
                for (String key : homes.getKeys(false)) {
                    MigrationLocations.read(homes.getConfigurationSection(key)).ifPresent(position ->
                            builder.home(new LegacyPlayer.Home(key, position, Instant.now())));
                }
            }

            for (String raw : yaml.getStringList("ignore")) {
                UUID blocked = parseUuid(raw);
                if (blocked != null) {
                    builder.blocked(blocked);
                }
            }

            return Optional.of(builder.build());
        }

        @Override
        public LegacyServerData serverData() throws IOException {
            return new LegacyServerData(warps(), spawns(), List.of());
        }

        private List<LegacyServerData.NamedPosition> warps() throws IOException {
            Path warps = root.resolve("warps");
            if (!Files.isDirectory(warps)) {
                return List.of();
            }

            List<LegacyServerData.NamedPosition> result = new ArrayList<>();
            try (Stream<Path> files = Files.list(warps)) {
                for (Path file : files.toList()) {
                    String fileName = file.getFileName().toString();
                    if (!fileName.toLowerCase(Locale.ROOT).endsWith(".yml")) {
                        continue;
                    }
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
                    String key = yaml.getString("name", fileName.substring(0, fileName.length() - 4));
                    MigrationLocations.read(yaml).ifPresent(position ->
                            result.add(new LegacyServerData.NamedPosition(key, position)));
                }
            }
            return result;
        }

        private List<LegacyServerData.NamedPosition> spawns() {
            Path file = root.resolve("spawn.yml");
            if (!Files.isRegularFile(file)) {
                return List.of();
            }

            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
            ConfigurationSection spawns = yaml.getConfigurationSection("spawns");
            if (spawns == null) {
                return List.of();
            }

            List<LegacyServerData.NamedPosition> result = new ArrayList<>();
            for (String group : spawns.getKeys(false)) {
                Optional<Position> position = MigrationLocations.read(spawns.getConfigurationSection(group));
                if (position.isEmpty()) {
                    continue;
                }
                result.add(new LegacyServerData.NamedPosition(spawnKey(group), position.get()));
            }
            return result;
        }

        // essentials names its first-join spawn group "newbies"
        private String spawnKey(String group) {
            if (group.equalsIgnoreCase("default")) {
                return LocationRepository.SPAWN_DEFAULT;
            }
            if (group.equalsIgnoreCase("newbies")) {
                return LocationRepository.SPAWN_FIRST_JOIN;
            }
            return LocationRepository.spawnGroup(group);
        }

        @Override
        public void close() {
            // nothing to release
        }

        private static @Nullable String string(ConfigurationSection section, String... keys) {
            for (String key : keys) {
                String value = section.getString(key);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return null;
        }

        private static @Nullable Boolean bool(ConfigurationSection section, String... keys) {
            for (String key : keys) {
                if (section.isSet(key)) {
                    return section.getBoolean(key);
                }
            }
            return null;
        }

        // essentials writes money as a quoted string
        private static @Nullable Double money(@Nullable Object raw) {
            if (raw == null) {
                return null;
            }
            try {
                return new BigDecimal(String.valueOf(raw).trim()).doubleValue();
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private static @Nullable UUID parseUuid(String raw) {
            try {
                return UUID.fromString(raw.trim());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}