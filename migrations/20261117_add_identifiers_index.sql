ALTER TABLE code_chunks ADD COLUMN IF NOT EXISTS identifier_terms tsvector
    GENERATED ALWAYS AS (to_tsvector('simple', regexp_replace(content, '[^a-zA-Z0-9_]+', ' ', 'g'))) STORED;
CREATE INDEX IF NOT EXISTS code_chunks_identifier_terms_idx ON code_chunks USING gin (identifier_terms);

