package com.ftxeven.aircore.module.chat.format;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatTemplateResolver {

    private static final Pattern TAG = Pattern.compile("<temp:([a-zA-Z0-9_-]+)((?::[^:>]*)*)>");
    private static final Pattern ARG_PLACEHOLDER = Pattern.compile("\\{(\\d+)}");

    private final Map<String, String> templates;
    private final BiConsumer<String, String> onWarning;

    public ChatTemplateResolver(Map<String, String> templates, BiConsumer<String, String> onWarning) {
        this.templates = templates;
        this.onWarning = onWarning;
    }

    public String expand(String text) {
        if (templates.isEmpty() || text.indexOf('<') < 0) {
            return text;
        }
        return expand(text, new LinkedHashSet<>());
    }

    private String expand(String text, Set<String> stack) {
        Matcher matcher = TAG.matcher(text);
        if (!matcher.find()) {
            return text;
        }

        StringBuilder result = new StringBuilder();
        int last = 0;
        do {
            result.append(text, last, matcher.start());
            result.append(resolve(matcher.group(1), splitArgs(matcher.group(2)), stack));
            last = matcher.end();
        } while (matcher.find());
        return result.append(text, last, text.length()).toString();
    }

    private String resolve(String name, String[] args, Set<String> stack) {
        String template = templates.get(name);
        if (template == null) {
            onWarning.accept(name, "unknown template '<temp:" + name + ">' referenced");
            return "";
        }
        if (!stack.add(name)) {
            onWarning.accept(name, "template cycle detected involving '" + name + "', stopping expansion there");
            return "";
        }

        String withArgs = substituteArgs(template, args);
        String expanded = expand(withArgs, stack); // templates may reference other templates
        stack.remove(name);
        return expanded;
    }

    private String substituteArgs(String template, String[] args) {
        if (template.indexOf('{') < 0) {
            return template;
        }
        Matcher matcher = ARG_PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1)) - 1;
            String value = (index >= 0 && index < args.length) ? args[index] : matcher.group();
            result.append(template, last, matcher.start()).append(value);
            last = matcher.end();
        }
        return result.append(template, last, template.length()).toString();
    }

    private static String[] splitArgs(String rawTail) {
        return rawTail.isEmpty() ? new String[0] : rawTail.substring(1).split(":", -1);
    }
}