package com.ftxeven.aircore.core.command;

import java.util.Arrays;
import java.util.List;

public final class Shortcuts {

    private Shortcuts() {
    }

    public record Target(String command, String[] args) {}

    public static Target target(Shortcut shortcut) {
        String[] tokens = shortcut.runs().trim().split("\\s+");
        String command = tokens[0];
        String[] virtualArgs = tokens.length <= 1 ? new String[0] : Arrays.copyOfRange(tokens, 1, tokens.length);
        return new Target(command, virtualArgs);
    }

    public static String[] merge(String[] virtualArgs, String[] typedArgs) {
        if (virtualArgs.length == 0) {
            return typedArgs;
        }
        String[] merged = new String[virtualArgs.length + typedArgs.length];
        System.arraycopy(virtualArgs, 0, merged, 0, virtualArgs.length);
        System.arraycopy(typedArgs, 0, merged, virtualArgs.length, typedArgs.length);
        return merged;
    }

    public record Shortcut(String runs, List<String> aliases) {
        public Shortcut {
            aliases = List.copyOf(aliases);
        }
    }
}