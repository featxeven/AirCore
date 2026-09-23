CREATE TABLE IF NOT EXISTS {prefix}players (
    uuid VARCHAR(36) PRIMARY KEY,
    name VARCHAR(16) NOT NULL,
    nickname VARCHAR(32) NULL,
    nickname_normalized VARCHAR(32) NULL,
    skin_value TEXT NOT NULL,
    skin_signature TEXT NOT NULL,
    join_number INT NOT NULL,
    first_join_at BIGINT NOT NULL,
    last_seen_at BIGINT NOT NULL,
    last_world VARCHAR(64) NULL,
    last_x DOUBLE NULL,
    last_y DOUBLE NULL,
    last_z DOUBLE NULL,
    last_yaw FLOAT NULL,
    last_pitch FLOAT NULL,
    chat_channel VARCHAR(32) NULL,
    game_mode VARCHAR(16) NOT NULL DEFAULT 'SURVIVAL',
    god_mode BOOLEAN NOT NULL DEFAULT FALSE,
    allow_flight BOOLEAN NOT NULL DEFAULT FALSE,
    flying BOOLEAN NOT NULL DEFAULT FALSE,
    walk_speed FLOAT NOT NULL DEFAULT 0.2,
    fly_speed FLOAT NOT NULL DEFAULT 0.1,
    player_time INT NULL,
    player_weather VARCHAR(16) NULL,
    toggles INT NOT NULL DEFAULT 0,
    pending_payment DOUBLE NOT NULL DEFAULT 0,
    balance DOUBLE NOT NULL DEFAULT 0,
    UNIQUE KEY uk_join_number (join_number),
    UNIQUE KEY uk_nickname_normalized (nickname_normalized),
    KEY idx_name (name),
    KEY idx_balance (balance, uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS {prefix}id_sequences (
    sequence_key VARCHAR(64) PRIMARY KEY,
    value BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS {prefix}player_inventories (
    uuid VARCHAR(36) PRIMARY KEY,
    contents MEDIUMBLOB NOT NULL,
    held_slot INT NOT NULL DEFAULT 0,
    ender_chest MEDIUMBLOB NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS {prefix}homes (
    owner VARCHAR(36) NOT NULL,
    name VARCHAR(32) NOT NULL,
    world VARCHAR(64) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    icon VARCHAR(64) NULL,
    favorite BOOLEAN NOT NULL DEFAULT FALSE,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (owner, name),
    KEY idx_owner_created (owner, created_at),
    KEY idx_owner_world (owner, world),
    KEY idx_owner_icon (owner, icon),
    KEY idx_owner_favorite (owner, favorite)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS {prefix}cooldowns (
    owner VARCHAR(36) NOT NULL,
    scope VARCHAR(16) NOT NULL,
    cooldown_key VARCHAR(64) NOT NULL,
    arg VARCHAR(64) NOT NULL DEFAULT '',
    expires_at BIGINT NOT NULL,
    PRIMARY KEY (owner, scope, cooldown_key, arg),
    KEY idx_expires (expires_at),
    KEY idx_scope_key (scope, cooldown_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS {prefix}blocks (
    owner VARCHAR(36) NOT NULL,
    blocked VARCHAR(36) NOT NULL,
    blocked_at BIGINT NOT NULL,
    PRIMARY KEY (owner, blocked)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS {prefix}variables (
    owner VARCHAR(36) NOT NULL,
    var_key VARCHAR(64) NOT NULL,
    value TEXT NOT NULL,
    numeric_value DOUBLE NULL,
    PRIMARY KEY (owner, var_key),
    KEY idx_var_key_numeric (var_key, numeric_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
    created_by VARCHAR(36) NULL,
    PRIMARY KEY (category, loc_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS {prefix}kits (
    name VARCHAR(32) PRIMARY KEY,
    items MEDIUMBLOB NOT NULL,
    one_time BOOLEAN NOT NULL DEFAULT FALSE,
    cooldown_seconds INT NULL,
    drop_on_full_inventory BOOLEAN NOT NULL DEFAULT FALSE,
    exact_slots BOOLEAN NOT NULL DEFAULT FALSE,
    requires_permission BOOLEAN NOT NULL DEFAULT TRUE,
    created_at BIGINT NOT NULL,
    created_by VARCHAR(36) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS {prefix}persistent_bossbar (
    announcement_key VARCHAR(64) PRIMARY KEY,
    bar_text TEXT NOT NULL,
    duration_seconds INT NOT NULL,
    color VARCHAR(16) NOT NULL,
    overlay VARCHAR(16) NOT NULL,
    countdown BOOLEAN NOT NULL DEFAULT FALSE,
    initial_progress DOUBLE NOT NULL DEFAULT 1.0,
    started_at BIGINT NOT NULL,
    placeholders TEXT NOT NULL,
    sync_on_join BOOLEAN NOT NULL DEFAULT TRUE,
    forced BOOLEAN NOT NULL DEFAULT FALSE,
    conditions TEXT NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;