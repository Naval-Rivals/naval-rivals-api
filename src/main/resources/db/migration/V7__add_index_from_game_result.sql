CREATE INDEX idx_game_results_winner_id ON game_results(winner_id);
CREATE INDEX idx_game_results_loser_id ON game_results(loser_id);
CREATE INDEX idx_game_results_finished_at ON game_results(finished_at DESC);