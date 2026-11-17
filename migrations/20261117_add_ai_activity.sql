-- Activity metadata only; prompts, tokens and credentials are never stored here.
CREATE TABLE IF NOT EXISTS ai_activity (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    repo_id UUID REFERENCES repositories(id) ON DELETE CASCADE,
    kind VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    result_id UUID,
    started_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    touched_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp
);
CREATE INDEX IF NOT EXISTS idx_ai_activity_user_started ON ai_activity(user_id, started_at DESC);

