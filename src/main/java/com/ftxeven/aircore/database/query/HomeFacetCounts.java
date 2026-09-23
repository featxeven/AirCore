package com.ftxeven.aircore.database.query;

import java.util.Map;

// backs the %count% placeholder in the homes-filter-world / homes-filter-icon cyclers
public record HomeFacetCounts(Map<String, Long> byWorld, Map<String, Long> byIcon) {

    public static HomeFacetCounts of(Map<String, Long> byWorld, Map<String, Long> byIcon) {
        return new HomeFacetCounts(Map.copyOf(byWorld), Map.copyOf(byIcon));
    }
}