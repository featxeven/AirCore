package com.ftxeven.aircore.database.repository;

import com.ftxeven.aircore.model.Kit;

import java.util.List;
import java.util.Optional;

public interface KitRepository {

    Optional<Kit> find(String name);

    List<Kit> findAll();

    // insert, or overwrite an existing kit's definition
    void save(Kit kit);

    boolean delete(String name);
}