package com.ftxeven.aircore.migration.model;

import com.ftxeven.aircore.command.player.ToggleKey;
import com.ftxeven.aircore.model.PlayerProfile;
import com.ftxeven.aircore.model.Position;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record LegacyPlayer(
        UUID uuid,
        String name,
        @Nullable PlayerProfile.Skin skin,
        @Nullable Double balance,
        LegacyToggles toggles,
        @Nullable String nickname, // raw legacy text, converted by the writer
        @Nullable Boolean godMode,
        @Nullable Boolean flightAllowed,
        @Nullable Float walkSpeed,
        @Nullable Float flySpeed,
        @Nullable Integer playerTime,
        @Nullable PlayerProfile.PersonalWeather playerWeather,
        @Nullable Position lastLocation,
        List<Home> homes,
        Set<UUID> blocked,
        @Nullable Inventory inventory,
        List<KitClaim> kitClaims
) {

    public record Home(String name, Position position, Instant createdAt) {}

    // contents uses the bukkit PlayerInventory layout (0-35 storage, 36-39 armor, 40 offhand)
    public record Inventory(ItemStack[] contents, ItemStack[] enderChest, int heldSlot) {}

    public record KitClaim(String kit, @Nullable Instant expiresAt, boolean permanent) {}

    public static Builder builder(UUID uuid, String name) {
        return new Builder(uuid, name);
    }

    public static final class Builder {

        private final UUID uuid;
        private final String name;
        private final LegacyToggles toggles = new LegacyToggles();
        private final List<Home> homes = new ArrayList<>();
        private final Set<UUID> blocked = new LinkedHashSet<>();
        private final List<KitClaim> kitClaims = new ArrayList<>();

        private PlayerProfile.Skin skin;
        private Double balance;
        private String nickname;
        private Boolean godMode;
        private Boolean flightAllowed;
        private Float walkSpeed;
        private Float flySpeed;
        private Integer playerTime;
        private PlayerProfile.PersonalWeather playerWeather;
        private Position lastLocation;
        private Inventory inventory;

        private Builder(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }

        public Builder skin(@Nullable PlayerProfile.Skin skin) { this.skin = skin; return this; }
        public Builder balance(@Nullable Double balance) { this.balance = balance; return this; }
        public Builder nickname(@Nullable String nickname) { this.nickname = nickname; return this; }
        public Builder godMode(@Nullable Boolean godMode) { this.godMode = godMode; return this; }
        public Builder flightAllowed(@Nullable Boolean allowed) { this.flightAllowed = allowed; return this; }
        public Builder walkSpeed(@Nullable Float walkSpeed) { this.walkSpeed = walkSpeed; return this; }
        public Builder flySpeed(@Nullable Float flySpeed) { this.flySpeed = flySpeed; return this; }
        public Builder playerTime(@Nullable Integer ticks) { this.playerTime = ticks; return this; }
        public Builder playerWeather(@Nullable PlayerProfile.PersonalWeather weather) { this.playerWeather = weather; return this; }
        public Builder lastLocation(@Nullable Position position) { this.lastLocation = position; return this; }
        public Builder inventory(@Nullable Inventory inventory) { this.inventory = inventory; return this; }

        public Builder toggle(ToggleKey key, @Nullable Boolean value) {
            toggles.set(key, value);
            return this;
        }

        public Builder home(Home home) {
            homes.add(home);
            return this;
        }

        public Builder blocked(UUID target) {
            blocked.add(target);
            return this;
        }

        public Builder kitClaim(KitClaim claim) {
            kitClaims.add(claim);
            return this;
        }

        public LegacyPlayer build() {
            return new LegacyPlayer(uuid, name, skin, balance, toggles, nickname, godMode, flightAllowed,
                    walkSpeed, flySpeed, playerTime, playerWeather, lastLocation,
                    List.copyOf(homes), Set.copyOf(blocked), inventory, List.copyOf(kitClaims));
        }
    }
}