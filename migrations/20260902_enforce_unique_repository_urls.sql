-- Prevent two concurrent /ingest requests for the same normalized GitHub URL
-- from creating separate repository rows and separate SQS jobs.
--
-- Existing duplicates must be reconciled deliberately rather than silently
-- deleting repositories, their code chunks, or user access mappings here.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM repositories
        GROUP BY github_url
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot add the GitHub URL uniqueness index: duplicate repositories exist. Reconcile them before rerunning this migration.';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS idx_repositories_github_url_unique
ON repositories (github_url);
