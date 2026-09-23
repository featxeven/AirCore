package com.ftxeven.aircore.database.query;

import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

public record HomeQuery(
        UUID owner,
        @Nullable String world,
        @Nullable Set<String> icons,
        @Nullable String search,
        boolean favoritesOnly,
        HomeSort sort,
        int page,
        int pageSize
) {

    public HomeQuery {
        page = Math.max(1, page);
        pageSize = Math.max(1, pageSize);
        icons = icons != null ? Set.copyOf(icons) : null;
    }

    public static Builder builder(UUID owner, int pageSize) {
        return new Builder(owner, pageSize);
    }

    public HomeQuery withoutWorld() {
        return new HomeQuery(owner, null, icons, search, favoritesOnly, sort, page, pageSize);
    }

    // used when computing icon facet counts, so the icon breakdown isn't narrowed by
    // whichever icon filter is currently selected
    public HomeQuery withoutIcons() {
        return new HomeQuery(owner, world, null, search, favoritesOnly, sort, page, pageSize);
    }

    public static final class Builder {

        private final UUID owner;
        private final int pageSize;
        private String world;
        private Set<String> icons;
        private String search;
        private boolean favoritesOnly;
        private HomeSort sort = HomeSort.ALPHABETICAL;
        private int page = 1;

        private Builder(UUID owner, int pageSize) {
            this.owner = owner;
            this.pageSize = pageSize;
        }

        public Builder world(String world) {
            this.world = world;
            return this;
        }

        // narrows to a single icon id (a standalone filter entry)
        public Builder icon(String icon) {
            this.icons = icon != null ? Set.of(icon) : null;
            return this;
        }

        // narrows to any of several icon ids (a bundle filter entry)
        public Builder icons(Set<String> icons) {
            this.icons = icons;
            return this;
        }

        public Builder search(String search) {
            this.search = search;
            return this;
        }

        public Builder favoritesOnly(boolean favoritesOnly) {
            this.favoritesOnly = favoritesOnly;
            return this;
        }

        public Builder sort(HomeSort sort) {
            this.sort = sort;
            return this;
        }

        public Builder page(int page) {
            this.page = page;
            return this;
        }

        public HomeQuery build() {
            return new HomeQuery(owner, world, icons, search, favoritesOnly, sort, page, pageSize);
        }
    }
}