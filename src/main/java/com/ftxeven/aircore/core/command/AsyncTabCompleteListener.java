package com.ftxeven.aircore.core.command;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;

public final class AsyncTabCompleteListener implements Listener {

    private final JavaPlugin plugin;
    private final DynamicCommandRegistry commands;

    public AsyncTabCompleteListener(JavaPlugin plugin, DynamicCommandRegistry commands) {
        this.plugin = plugin;
        this.commands = commands;
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onAsyncTabComplete(AsyncTabCompleteEvent event) {
        if (event.isHandled() || !event.isCommand()) {
            return;
        }

        CommandSender sender = event.getSender();
        if (!(sender instanceof Player player)) {
            return;
        }

        ParsedBuffer parsed = ParsedBuffer.parse(event.getBuffer());
        if (parsed == null) {
            return; // still completing the command word itself
        }

        Optional<Command> command = commands.find(parsed.label());
        if (command.isEmpty()) {
            return;
        }

        try {
            List<String> completions = command.get().tabComplete(player, parsed.label(), parsed.args());
            event.setCompletions(completions != null ? completions : List.of());
            event.setHandled(true);
        } catch (Exception e) {
            plugin.getLogger().warning("Async tab-complete failed for '/" + parsed.label()
                    + "', falling back to the synchronous path: " + e.getMessage());
        }
    }

    private record ParsedBuffer(String label, String[] args) {
        static ParsedBuffer parse(String buffer) {
            if (buffer.isEmpty() || buffer.charAt(0) != '/') {
                return null;
            }
            String withoutSlash = buffer.substring(1);
            int firstSpace = withoutSlash.indexOf(' ');
            if (firstSpace < 0) {
                return null;
            }
            String label = withoutSlash.substring(0, firstSpace);
            String[] args = withoutSlash.substring(firstSpace + 1).split(" ", -1);
            return new ParsedBuffer(label, args);
        }
    }
}