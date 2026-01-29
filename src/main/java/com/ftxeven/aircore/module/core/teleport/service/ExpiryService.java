package com.ftxeven.aircore.module.core.teleport.service;

import com.ftxeven.aircore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class ExpiryService implements Runnable {
    private final RequestService requests;
    private final int expireSeconds;

    public ExpiryService(RequestService requests, int expireSeconds) {
        this.requests = requests;
        this.expireSeconds = expireSeconds;
    }

    @Override
    public void run() {
        if (expireSeconds <= 0) return;
        if (requests.getAllTargets().isEmpty()) return;

        final long now = System.currentTimeMillis();

        for (UUID targetId : requests.getAllTargets()) {
            Deque<RequestService.TeleportRequest> queue = requests.getAllRequests(targetId);
            if (queue == null || queue.isEmpty()) {
                requests.clearRequestsForTarget(targetId);
                continue;
            }

            Iterator<RequestService.TeleportRequest> it = queue.iterator();
            while (it.hasNext()) {
                RequestService.TeleportRequest req = it.next();
                if (req.expiryTime() <= now) {
                    Player sender = Bukkit.getPlayer(req.sender());
                    if (sender != null) {
                        MessageUtil.send(sender, "teleport.lifecycle.expired-to",
                                Map.of("player", req.targetName()));
                    }

                    Player target = Bukkit.getPlayer(req.target());
                    if (target != null) {
                        MessageUtil.send(target, "teleport.lifecycle.expired-from",
                                Map.of("player", req.senderName()));
                    }

                    it.remove();
                }
            }

            if (queue.isEmpty()) {
                requests.clearRequestsForTarget(targetId);
            }
        }
    }
}
