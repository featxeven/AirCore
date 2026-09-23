package com.ftxeven.aircore.model;

import org.bukkit.GameMode;
import org.bukkit.WeatherType;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record PlayerProfile(
        UUID uuid,
        String name,
        @Nullable String nickname,
        Skin skin,
        int joinNumber, // this player's position in join order, backs %unique%
        Instant firstJoinAt,
        Instant lastSeenAt,
        @Nullable Position lastLocation,
        @Nullable String chatChannel,
        GameMode gameMode,
        boolean godMode,
        Flight flight,
        float walkSpeed,
        float flySpeed,
        @Nullable Integer playerTime, // ticks, null = synced with the world
        @Nullable PersonalWeather playerWeather,
        Toggles toggles,
        double pendingPayment,
        double balance
) {

    // Copy-on-write updates

    public PlayerProfile withLastLocation(@Nullable Position lastLocation) {
        return toBuilder().lastLocation(lastLocation).build();
    }

    public PlayerProfile withNickname(@Nullable String nickname) {
        return toBuilder().nickname(nickname).build();
    }

    public PlayerProfile withChatChannel(@Nullable String chatChannel) {
        return toBuilder().chatChannel(chatChannel).build();
    }

    public PlayerProfile withGameMode(GameMode gameMode) {
        return toBuilder().gameMode(gameMode).build();
    }

    public PlayerProfile withGodMode(boolean godMode) {
        return toBuilder().godMode(godMode).build();
    }

    public PlayerProfile withFlight(Flight flight) {
        return toBuilder().flight(flight).build();
    }

    public PlayerProfile withWalkSpeed(float walkSpeed) {
        return toBuilder().walkSpeed(walkSpeed).build();
    }

    public PlayerProfile withFlySpeed(float flySpeed) {
        return toBuilder().flySpeed(flySpeed).build();
    }

    public PlayerProfile withPlayerTime(@Nullable Integer playerTime) {
        return toBuilder().playerTime(playerTime).build();
    }

    public PlayerProfile withPlayerWeather(@Nullable PersonalWeather playerWeather) {
        return toBuilder().playerWeather(playerWeather).build();
    }

    public PlayerProfile withToggles(Toggles toggles) {
        return toBuilder().toggles(toggles).build();
    }

    public PlayerProfile withBalance(double balance) {
        return toBuilder().balance(balance).build();
    }

    public PlayerProfile withPendingPayment(double pendingPayment) {
        return toBuilder().pendingPayment(pendingPayment).build();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static final class Builder {
        private UUID uuid;
        private String name;
        private @Nullable String nickname;
        private Skin skin;
        private int joinNumber;
        private Instant firstJoinAt;
        private Instant lastSeenAt;
        private @Nullable Position lastLocation;
        private @Nullable String chatChannel;
        private GameMode gameMode;
        private boolean godMode;
        private Flight flight;
        private float walkSpeed;
        private float flySpeed;
        private @Nullable Integer playerTime;
        private @Nullable PersonalWeather playerWeather;
        private Toggles toggles;
        private double pendingPayment;
        private double balance;

        private Builder(PlayerProfile source) {
            this.uuid = source.uuid;
            this.name = source.name;
            this.nickname = source.nickname;
            this.skin = source.skin;
            this.joinNumber = source.joinNumber;
            this.firstJoinAt = source.firstJoinAt;
            this.lastSeenAt = source.lastSeenAt;
            this.lastLocation = source.lastLocation;
            this.chatChannel = source.chatChannel;
            this.gameMode = source.gameMode;
            this.godMode = source.godMode;
            this.flight = source.flight;
            this.walkSpeed = source.walkSpeed;
            this.flySpeed = source.flySpeed;
            this.playerTime = source.playerTime;
            this.playerWeather = source.playerWeather;
            this.toggles = source.toggles;
            this.pendingPayment = source.pendingPayment;
            this.balance = source.balance;
        }

        public Builder uuid(UUID uuid) { this.uuid = uuid; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder nickname(@Nullable String nickname) { this.nickname = nickname; return this; }
        public Builder skin(Skin skin) { this.skin = skin; return this; }
        public Builder joinNumber(int joinNumber) { this.joinNumber = joinNumber; return this; }
        public Builder firstJoinAt(Instant firstJoinAt) { this.firstJoinAt = firstJoinAt; return this; }
        public Builder lastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; return this; }
        public Builder lastLocation(@Nullable Position lastLocation) { this.lastLocation = lastLocation; return this; }
        public Builder chatChannel(@Nullable String chatChannel) { this.chatChannel = chatChannel; return this; }
        public Builder gameMode(GameMode gameMode) { this.gameMode = gameMode; return this; }
        public Builder godMode(boolean godMode) { this.godMode = godMode; return this; }
        public Builder flight(Flight flight) { this.flight = flight; return this; }
        public Builder walkSpeed(float walkSpeed) { this.walkSpeed = walkSpeed; return this; }
        public Builder flySpeed(float flySpeed) { this.flySpeed = flySpeed; return this; }
        public Builder playerTime(@Nullable Integer playerTime) { this.playerTime = playerTime; return this; }
        public Builder playerWeather(@Nullable PersonalWeather playerWeather) { this.playerWeather = playerWeather; return this; }
        public Builder toggles(Toggles toggles) { this.toggles = toggles; return this; }
        public Builder pendingPayment(double pendingPayment) { this.pendingPayment = pendingPayment; return this; }
        public Builder balance(double balance) { this.balance = balance; return this; }

        public PlayerProfile build() {
            return new PlayerProfile(uuid, name, nickname, skin, joinNumber, firstJoinAt, lastSeenAt, lastLocation,
                    chatChannel, gameMode, godMode, flight, walkSpeed, flySpeed, playerTime, playerWeather, toggles,
                    pendingPayment, balance);
        }
    }

    public record Skin(String value, String signature) {
        public static final Skin EMPTY = new Skin("", "");

        public boolean isPresent() {
            return value != null && !value.isEmpty() && signature != null && !signature.isEmpty();
        }
    }

    public record Flight(boolean allowed, boolean flying) {
        public static final Flight GROUNDED = new Flight(false, false);
    }

    public enum PersonalWeather {
        CLEAR, THUNDER;

        public WeatherType toBukkit() {
            return switch (this) {
                case CLEAR -> WeatherType.CLEAR;
                case THUNDER -> WeatherType.DOWNFALL;
            };
        }

        public static PersonalWeather fromBukkit(WeatherType type) {
            return switch (type) {
                case CLEAR -> CLEAR;
                case DOWNFALL -> THUNDER;
            };
        }
    }

    public record Toggles(
            boolean msg, boolean socialSpy, boolean chat, boolean mention, boolean announce,
            boolean pay, boolean payConfirm, boolean tp, boolean tpAutoAccept, boolean tpConfirm
    ) {
        public int toBits() {
            int bits = 0;
            if (msg) bits |= 1;
            if (socialSpy) bits |= 1 << 1;
            if (chat) bits |= 1 << 2;
            if (mention) bits |= 1 << 3;
            if (announce) bits |= 1 << 4;
            if (pay) bits |= 1 << 5;
            if (payConfirm) bits |= 1 << 6;
            if (tp) bits |= 1 << 7;
            if (tpAutoAccept) bits |= 1 << 8;
            if (tpConfirm) bits |= 1 << 9;
            return bits;
        }

        public static Toggles fromBits(int bits) {
            return new Toggles(
                    (bits & 1) != 0, (bits & 1 << 1) != 0, (bits & 1 << 2) != 0, (bits & 1 << 3) != 0,
                    (bits & 1 << 4) != 0, (bits & 1 << 5) != 0, (bits & 1 << 6) != 0, (bits & 1 << 7) != 0,
                    (bits & 1 << 8) != 0, (bits & 1 << 9) != 0
            );
        }
    }
}