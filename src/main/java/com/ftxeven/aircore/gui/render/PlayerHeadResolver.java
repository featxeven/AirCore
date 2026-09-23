package com.ftxeven.aircore.gui.render;

import com.ftxeven.aircore.core.gui.render.MaterialResolver;
import com.ftxeven.aircore.database.repository.PlayerRepository;
import com.ftxeven.aircore.service.PlayerService;

import java.util.Optional;
import java.util.UUID;

public final class PlayerHeadResolver implements MaterialResolver.HeadResolver {

    private final PlayerService players;

    public PlayerHeadResolver(PlayerService players) {
        this.players = players;
    }

    @Override
    public Optional<MaterialResolver.CachedHead> byUuid(UUID uuid) {
        return players.identity(uuid).map(PlayerHeadResolver::toHead);
    }

    @Override
    public Optional<MaterialResolver.CachedHead> byName(String name) {
        return players.identityByName(name).map(PlayerHeadResolver::toHead);
    }

    private static MaterialResolver.CachedHead toHead(PlayerRepository.Identity identity) {
        var skin = identity.skin();
        return new MaterialResolver.CachedHead(identity.uuid(), identity.name(), skin.value(), skin.signature());
    }
}