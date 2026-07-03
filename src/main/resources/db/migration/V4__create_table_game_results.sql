CREATE TABLE game_results (
    id UUID PRIMARY KEY,
    room_code VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL,
    winner_id UUID REFERENCES users(id),
    loser_id UUID REFERENCES users(id),
    duration_seconds BIGINT,
    winner_shots INT NOT NULL DEFAULT 0,
    winner_hits INT NOT NULL DEFAULT 0,
    winner_misses INT NOT NULL DEFAULT 0,
    winner_ships_destroyed INT NOT NULL DEFAULT 0,
    loser_shots INT NOT NULL DEFAULT 0,
    loser_hits INT NOT NULL DEFAULT 0,
    loser_misses INT NOT NULL DEFAULT 0,
    loser_ships_destroyed INT NOT NULL DEFAULT 0,
    finished_at TIMESTAMP WITH TIME ZONE NOT NULL
);
