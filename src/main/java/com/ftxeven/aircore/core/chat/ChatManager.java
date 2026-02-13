package com.ftxeven.aircore.core.chat;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.chat.service.*;

public final class ChatManager {

    private final AirCore plugin;
    private final CooldownService chatCooldowns = new CooldownService();
    private MentionService mentionService;
    private UrlFormatService urlFormatService;
    private ChatFormatService formatService;
    private MessageService messageService;
    private DisplayTagsService displayTagsService;

    public ChatManager(AirCore plugin) {
        this.plugin = plugin;
        constructServices();
    }

    public void reload() {
        constructServices();
        chatCooldowns.clearAll();
    }

    private void constructServices() {
        this.mentionService = new MentionService(plugin);
        this.urlFormatService = new UrlFormatService(plugin);
        this.formatService = new ChatFormatService(plugin);
        this.messageService = new MessageService(plugin);
        this.displayTagsService = new DisplayTagsService(plugin);
    }

    public CooldownService cooldowns() { return chatCooldowns; }
    public ChatFormatService formats() { return formatService; }
    public MentionService mentions() { return mentionService; }
    public UrlFormatService urls() { return urlFormatService; }
    public MessageService messages() { return messageService; }
    public DisplayTagsService displayTags() { return displayTagsService; }
}
