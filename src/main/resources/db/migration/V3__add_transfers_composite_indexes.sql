CREATE INDEX idx_transfers_source_wallet_created ON transfers(source_wallet_id, created_at DESC);
CREATE INDEX idx_transfers_destination_wallet_created ON transfers(destination_wallet_id, created_at DESC);
