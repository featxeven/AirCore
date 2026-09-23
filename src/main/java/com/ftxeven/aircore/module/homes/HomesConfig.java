package com.ftxeven.aircore.module.homes;

import com.ftxeven.aircore.config.BaseConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class HomesConfig extends BaseConfig {

    private volatile General general;
    private volatile Naming naming;
    private volatile Behaviour behaviour;
    private volatile Gui gui;

    public HomesConfig(JavaPlugin plugin) {
        super(plugin, "modules/homes.yml");
    }

    @Override
    protected void read(ConfigurationSection yaml) {
        general = readGeneral(yaml.getConfigurationSection("general"));
        naming = readNaming(yaml.getConfigurationSection("naming"));
        behaviour = readBehaviour(yaml.getConfigurationSection("behaviour"));
        gui = readGui(yaml.getConfigurationSection("gui"));
    }

    public General general() { return general; }
    public Naming naming() { return naming; }
    public Behaviour behaviour() { return behaviour; }
    public Gui gui() { return gui; }

    private General readGeneral(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new General(
                getInt(sec, "max-homes", 3),
                getStringList(sec, "disabled-worlds")
        );
    }

    private Naming readNaming(ConfigurationSection sec) {
        sec = orEmpty(sec);
        NameValidation nv = readNameValidation(sec);
        return new Naming(getBoolean(sec, "enabled", true), nv.maxLength(), nv.validationRegex(), nv.blacklist(), nv.extendProfanityWords());
    }

    private Behaviour readBehaviour(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Behaviour(getBoolean(sec, "overwrite-on-set", true), getBoolean(sec, "teleport-to-bed", false));
    }

    private Gui readGui(ConfigurationSection sec) {
        sec = orEmpty(sec);
        return new Gui(readLabelMap(sec.getConfigurationSection("sort")));
    }

    public record General(int maxHomes, List<String> disabledWorlds) {}

    public record Naming(boolean enabled, int maxLength, Pattern validationRegex, List<Pattern> blacklist, boolean extendProfanityWords) {}

    public record Behaviour(boolean overwriteOnSet, boolean teleportToBed) {}

    public record Gui(Map<String, String> sort) {}
}