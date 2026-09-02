-- Run once against existing PostgreSQL deployments before deploying the
-- application version that validates RepoEntity.isPrivate.
--
-- Do not default existing rows to FALSE: their historical GitHub visibility is
-- unknown, and a false default would expose previously ingested private code.
ALTER TABLE repositories ADD COLUMN IF NOT EXISTS is_private BOOLEAN;
