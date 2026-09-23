package com.ftxeven.aircore.database.repository;

import com.ftxeven.aircore.model.Home;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HomeRepository {

    Optional<Home> find(UUID owner, String name);

    List<Home> findAll(UUID owner);

    int count(UUID owner);

    Home save(Home home);

    boolean setFavorite(UUID owner, String name, boolean favorite);

    boolean setIcon(UUID owner, String name, @Nullable String icon);

    boolean delete(UUID owner, String name);

    int deleteAll(UUID owner);

    int deleteAll(Collection<UUID> owners);
}