ALTER TABLE repositories
ADD COLUMN IF NOT EXISTS sync_message TEXT;
