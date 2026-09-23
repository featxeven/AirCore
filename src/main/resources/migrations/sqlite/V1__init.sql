CREATE TABLE IF NOT EXISTS {prefix}players (
    uuid VARCHAR(36) PRIMARY KEY,
    name VARCHAR(16) NOT NULL,
    nickname VARCHAR(32),
    nickname_normalized VARCHAR(32),
    skin_value TEXT NOT NULL DEFAULT '',
    skin_signature TEXT NOT NULL DEFAULT '',
    join_number INTEGER NOT NULL UNIQUE,
    first_join_at BIGINT NOT NULL,
    last_seen_at BIGINT NOT NULL,
    last_world VARCHAR(64),
    last_x DOUBLE,
    last_y DOUBLE,
    last_z DOUBLE,
    last_yaw FLOAT,
    last_pitch FLOAT,
    chat_channel VARCHAR(32),
    game_mode VARCHAR(16) NOT NULL DEFAULT 'SURVIVAL',
    god_mode BOOLEAN NOT NULL DEFAULT 0,
    allow_flight BOOLEAN NOT NULL DEFAULT 0,
    flying BOOLEAN NOT NULL DEFAULT 0,
    walk_speed FLOAT NOT NULL DEFAULT 0.2,
    fly_speed FLOAT NOT NULL DEFAULT 0.1,
    player_time INTEGER,
    player_weather VARCHAR(16),
    toggles INTEGER NOT NULL DEFAULT 0,
    pending_payment DOUBLE NOT NULL DEFAULT 0,
    balance DOUBLE NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_{prefix}players_name ON {prefix}players (name);
CREATE UNIQUE INDEX IF NOT EXISTS idx_{prefix}players_nickname_normalized ON {prefix}players (nickname_normalized);
CREATE INDEX IF NOT EXISTS idx_{prefix}players_balance ON {prefix}players (balance, uuid);

CREATE TABLE IF NOT EXISTS {prefix}id_sequences (
    sequence_key VARCHAR(64) PRIMARY KEY,
    value INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS {prefix}player_inventories (
    uuid VARCHAR(36) PRIMARY KEY,
    contents BLOB NOT NULL,
    held_slot INTEGER NOT NULL DEFAULT 0,
    ender_chest BLOB NOT NULL
);

CREATE TABLE IF NOT EXISTS {prefix}homes (
    owner VARCHAR(36) NOT NULL,
    name VARCHAR(32) NOT NULL,
    world VARCHAR(64) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    icon VARCHAR(64),
    favorite BOOLEAN NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (owner, name)
    );

CREATE INDEX IF NOT EXISTS idx_{prefix}homes_owner_created ON {prefix}homes (owner, created_at);
CREATE INDEX IF NOT EXISTS idx_{prefix}homes_owner_world ON {prefix}homes (owner, world);
CREATE INDEX IF NOT EXISTS idx_{prefix}homes_owner_icon ON {prefix}homes (owner, icon);
CREATE INDEX IF NOT EXISTS idx_{prefix}homes_owner_favorite ON {prefix}homes (owner, favorite);

CREATE TABLE IF NOT EXISTS {prefix}cooldowns (
    owner VARCHAR(36) NOT NULL,
    scope VARCHAR(16) NOT NULL,
    cooldown_key VARCHAR(64) NOT NULL,
    arg VARCHAR(64) NOT NULL DEFAULT '',
    expires_at BIGINT NOT NULL,
    PRIMARY KEY (owner, scope, cooldown_key, arg)
);

CREATE INDEX IF NOT EXISTS idx_{prefix}cooldowns_expires ON {prefix}cooldowns (expires_at);
CREATE INDEX IF NOT EXISTS idx_{prefix}cooldowns_scope_key ON {prefix}cooldowns (scope, cooldown_key);

CREATE TABLE IF NOT EXISTS {prefix}blocks (
    owner VARCHAR(36) NOT NULL,
    blocked VARCHAR(36) NOT NULL,
    blocked_at BIGINT NOT NULL,
    PRIMARY KEY (owner, blocked)
);

CREATE TABLE IF NOT EXISTS {prefix}variables (
    owner VARCHAR(36) NOT NULL,
    var_key VARCHAR(64) NOT NULL,
    value TEXT NOT NULL,
    numeric_value DOUBLE,
    PRIMARY KEY (owner, var_key)
);

CREATE INDEX IF NOT EXISTS idx_{prefix}variables_ranking ON {prefix}variables (var_key, numeric_value);

CREATE TABLE IF NOT EXISTS {prefix}locations (
    category VARCHAR(16) NOT NULL,
    loc_key VARCHAR(64) NOT NULL,
    world VARCHAR(64) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    created_at BIGINT NOT NULL,
    created_by VARCHAR(36),
    PRIMARY KEY (category, loc_key)
);

CREATE TABLE IF NOT EXISTS {prefix}kits (
    name VARCHAR(32) PRIMARY KEY,
    items BLOB NOT NULL,
    one_time BOOLEAN NOT NULL DEFAULT 0,
    cooldown_seconds INTEGER,
    drop_on_full_inventory BOOLEAN NOT NULL DEFAULT 0,
    exact_slots BOOLEAN NOT NULL DEFAULT 0,
    requires_permission BOOLEAN NOT NULL DEFAULT 1,
    created_at BIGINT NOT NULL,
    created_by VARCHAR(36)
);

CREATE TABLE IF NOT EXISTS {prefix}persistent_bossbar (
    announcement_key VARCHAR(64) PRIMARY KEY,
    bar_text TEXT NOT NULL,
    duration_seconds INTEGER NOT NULL,
    color VARCHAR(16) NOT NULL,
    overlay VARCHAR(16) NOT NULL,
    countdown BOOLEAN NOT NULL DEFAULT 0,
    initial_progress DOUBLE NOT NULL DEFAULT 1.0,
    started_at BIGINT NOT NULL,
    placeholders TEXT NOT NULL DEFAULT '',
    sync_on_join BOOLEAN NOT NULL DEFAULT 1,
    forced BOOLEAN NOT NULL DEFAULT 0,
    conditions TEXT NOT NULL DEFAULT ''
);