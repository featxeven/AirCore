package com.ftxeven.aircore.core.command;

import com.ftxeven.aircore.command.CommandHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CommandRegistry {

    private final List<CommandHandler> commandHandlers = new ArrayList<>();

    public CommandRegistry register(CommandHandler commandHandler) {
        commandHandlers.add(commandHandler);
        return this;
    }

    public Optional<CommandHandler> match(String label) {
        for (CommandHandler commandHandler : commandHandlers) {
            if (commandHandler.enabled() && (commandHandler.name().equalsIgnoreCase(label) || commandHandler.aliases().stream().anyMatch(label::equalsIgnoreCase))) {
                return Optional.of(commandHandler);
            }
        }
        return Optional.empty();
    }

    public List<CommandHandler> all() {
        return commandHandlers;
    }
}