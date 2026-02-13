package com.ftxeven.aircore.core.teleport.service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class RequestService {
    private final Map<UUID, Deque<TeleportRequest>> requests = new ConcurrentHashMap<>();

    public void addRequest(UUID sender, String senderName,
                           UUID target, String targetName,
                           long expiryTime, RequestType type) {
        requests.computeIfAbsent(target, k -> new ArrayDeque<>())
                .addLast(new TeleportRequest(sender, senderName, target, targetName, expiryTime, type));
    }

    public TeleportRequest getRequest(UUID target, UUID sender) {
        Deque<TeleportRequest> queue = requests.get(target);
        if (queue == null || queue.isEmpty()) return null;

        if (sender == null) return queue.getLast();
        return queue.stream()
                .filter(req -> req.sender().equals(sender))
                .reduce((first, second) -> second)
                .orElse(null);
    }

    public Deque<TeleportRequest> getAllRequests(UUID targetId) {
        return requests.getOrDefault(targetId, new ArrayDeque<>());
    }

    public void clearRequests(UUID uuid) {
        requests.remove(uuid);
        requests.values().forEach(queue -> queue.removeIf(req -> req.sender().equals(uuid)));
    }

    public void popLatest(UUID target) {
        Deque<TeleportRequest> queue = requests.get(target);
        if (queue == null || queue.isEmpty()) return;
        queue.pollLast();
        if (queue.isEmpty()) requests.remove(target);
    }

    public void popRequestFrom(UUID target, UUID sender) {
        Deque<TeleportRequest> queue = requests.get(target);
        if (queue == null) return;
        queue.removeIf(req -> req.sender().equals(sender));
        if (queue.isEmpty()) requests.remove(target);
    }

    public boolean hasRequests(UUID targetId) {
        Deque<TeleportRequest> queue = requests.get(targetId);
        return queue != null && !queue.isEmpty();
    }

    public void clearRequestsForTarget(UUID targetId) {
        requests.remove(targetId);
    }

    public Set<UUID> getAllTargets() {
        return requests.keySet();
    }

    public enum RequestType { TPA, TPAHERE }

    public record TeleportRequest(UUID sender, String senderName,
                                  UUID target, String targetName,
                                  long expiryTime,
                                  RequestType type) { }
}
