package com.ftxeven.aircore.core.teleport;

import com.ftxeven.aircore.AirCore;
import com.ftxeven.aircore.core.teleport.service.CooldownService;
import com.ftxeven.aircore.core.teleport.service.ExpiryService;
import com.ftxeven.aircore.core.teleport.service.RequestService;
import com.ftxeven.aircore.util.SchedulerUtil;

public final class TeleportManager {

    private final AirCore plugin;

    private RequestService requestService;
    private CooldownService cooldownService;
    private ExpiryService expiryService;

    private SchedulerUtil.CancellableTask expiryTask;

    public TeleportManager(AirCore plugin) {
        this.plugin = plugin;
        constructServices();
    }

    public void reload() {
        if (expiryTask != null) {
            expiryTask.cancel();
            expiryTask = null;
        }

        constructServices();
    }

    private void constructServices() {
        this.requestService = new RequestService();
        this.cooldownService = new CooldownService();

        int expireSeconds = plugin.config().teleportRequestExpireTime();
        this.expiryService = new ExpiryService(requestService, expireSeconds);

        if (expireSeconds > 0) {
            expiryTask = plugin.scheduler().runTimer(
                    expiryService,
                    20L,
                    20L
            );
        }
    }

    public RequestService requests() {
        return requestService;
    }

    public CooldownService cooldowns() {
        return cooldownService;
    }

    public ExpiryService expiry() {
        return expiryService;
    }
}
