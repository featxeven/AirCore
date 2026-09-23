package com.ftxeven.aircore.module.announcements;

import com.ftxeven.aircore.config.BaseFolderConfig;
import com.ftxeven.aircore.config.MessageComponents;
import com.ftxeven.aircore.config.YamlMaps;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AnnouncementsConfig extends BaseFolderConfig {

    private volatile Snapshot snapshot = new Snapshot(Map.of(), Set.of());

    private record Snapshot(Map<String, Announcement> announcements, Set<String> disabledByFile) {}

    public AnnouncementsConfig(JavaPlugin plugin) {
        super(plugin, "modules/announcements");
    }

    @Override
    protected void read(List<Source> sources) {
        Merged<Announcement> merged = mergeTracked(sources, "announcements", this::readAnnouncement);
        snapshot = new Snapshot(merged.values(), merged.disabledByFile());
    }

    public Map<String, Announcement> announcements() {
        return snapshot.announcements();
    }

    public Set<String> disabledByFile() {
        return snapshot.disabledByFile();
    }

    public Set<String> keySet() {
        Snapshot s = snapshot;
        Set<String> keys = new LinkedHashSet<>(s.announcements().keySet());
        keys.addAll(s.disabledByFile());
        return keys;
    }

    public boolean isActive(String key) {
        return announcementOf(snapshot, key) != null;
    }

    public boolean hasActivePersistentBossbar(String key) {
        Announcement announcement = announcementOf(snapshot, key);
        if (announcement == null) {
            return false;
        }
        for (AnnouncementEntry entry : announcement.entries()) {
            MessageComponents.Bundle message = entry.message();
            if (message != null && message.bossbar() instanceof MessageComponents.BossbarAction.Show show && show.persist()) {
                return true;
            }
        }
        return false;
    }

    // returns the announcement only if it exists AND is enabled, from a single snapshot read
    private Announcement announcementOf(Snapshot s, String key) {
        Announcement announcement = s.announcements().get(key);
        return (announcement != null && announcement.enabled()) ? announcement : null;
    }

    // Section readers

    private Announcement readAnnouncement(ConfigurationSection sec, String key) {
        sec = orEmpty(sec);
        return new Announcement(
                bool(sec, "enabled", true),
                bool(sec, "force", false),
                readSchedule(sec.getConfigurationSection("schedule")),
                stringList(sec, "conditions"),
                enumOr(sec, "sequence", Sequence.class, Sequence.ORDERED),
                readEntries(sec.getList("entries"))
        );
    }

    private Schedule readSchedule(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Schedule(
                enumOr(sec, "type", ScheduleType.class, ScheduleType.MANUAL),
                integer(sec, "interval", 0),
                integer(sec, "delay", 0),
                stringList(sec, "times"),
                stringList(sec, "dates")
        );
    }

    private List<AnnouncementEntry> readEntries(List<?> raw) {
        if (raw == null) {
            return List.of();
        }
        List<AnnouncementEntry> entries = new ArrayList<>();
        for (Object item : raw) {
            entries.add(readEntry(YamlMaps.toSection(item)));
        }
        return List.copyOf(entries);
    }

    private AnnouncementEntry readEntry(ConfigurationSection sec) {
        return new AnnouncementEntry(
                integer(sec, "weight", 1),
                integer(sec, "offset", 0),
                MessageComponents.read(sec),
                MessageComponents.readCommands(sec, "command")
        );
    }

    // Section types

    public enum ScheduleType { INTERVAL, CRON, STARTUP, MANUAL }

    public enum Sequence { ORDERED, RANDOM, CHAIN }

    public record Schedule(ScheduleType type, int interval, int delay, List<String> times, List<String> dates) {}

    public record AnnouncementEntry(int weight, int offset, MessageComponents.Bundle message, List<MessageComponents.CommandRun> command) {}

    public record Announcement(boolean enabled, boolean force, Schedule schedule, List<String> conditions, Sequence sequence, List<AnnouncementEntry> entries) {}
}