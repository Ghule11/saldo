CREATE TABLE IF NOT EXISTS app_state (
    name TEXT PRIMARY KEY,
    encrypted_value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS pending_authorizations (
    state TEXT PRIMARY KEY,
    institution_name TEXT NOT NULL,
    institution_country TEXT NOT NULL,
    expires_at INTEGER NOT NULL
);
