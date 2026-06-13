CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

--
-- USERS TABLE
-- Stores registered users. For now you will skip auth, but the column
-- exists so you don't have to migrate the schema later when you add JWT.
-- UUID as primary key instead of integer — harder to guess, better for APIs.
--
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,  -- bcrypt hash, never plain text
    name VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

--
-- REPOSITORIES TABLE
-- Every time someone submits a GitHub URL, one row is created here immediately.
-- The status column tracks where ingestion is: PENDING → PROCESSING → READY or FAILED.
-- This lets the frontend poll for progress without the HTTP connection staying open.
-- processed_files counts how many files have been embedded so far (for progress bar).
--
CREATE TABLE IF NOT EXISTS repositories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    github_url TEXT NOT NULL,
    repo_name VARCHAR(255),
    status VARCHAR(50) DEFAULT 'PENDING',
    total_files INTEGER DEFAULT 0,
    processed_files INTEGER DEFAULT 0,
    total_chunks INTEGER DEFAULT 0,
    error_message TEXT,        -- If status=FAILED, what went wrong
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

--
-- CODE_CHUNKS TABLE
-- This is the most important table. Every chunk of code from every file
-- gets one row here. The embedding column stores 768 numbers representing
-- the "meaning" of that chunk. pgvector uses these for similarity search.
-- content stores the actual text (file path header + code lines).
-- file_path stores which file this chunk came from — shown as sources in chat.
--

CREATE TABLE IF NOT EXISTS code_chunks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    repo_id UUID REFERENCES repositories(id) ON DELETE CASCADE,
    file_path TEXT NOT NULL,
    language VARCHAR(50),
    content TEXT NOT NULL,
    chunk_index INTEGER,     -- Which chunk within the file (0, 1, 2...)
    start_line INTEGER,      -- First line number of this chunk
    end_line INTEGER,        -- Last line number of this chunk
    embedding vector(768),   -- 768 floats — the nomic-embed-text output dimension
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

--
-- CONVERSATIONS TABLE
-- Groups messages together into one conversation per repo session.
-- When a user starts chatting about a repo, one conversation is created.
-- All their messages in that session belong to this conversation.
--
CREATE TABLE IF NOT EXISTS conversations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    repo_id UUID REFERENCES repositories(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
-- MESSAGES TABLE
-- Every single message — user or assistant — gets stored here.
-- role is either 'user' or 'assistant'.
-- sources stores which files were used to answer (as comma-separated paths).
-- Storing history lets you show the full conversation when page reloads.
--
CREATE TABLE IF NOT EXISTS messages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    conversation_id UUID REFERENCES conversations(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    sources TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
--
-- VECTOR INDEX
-- Without this index, every similarity search does a full table scan —
-- comparing your question's embedding against every single chunk's embedding.
-- With ivfflat index, PostgreSQL narrows down candidates first (approximate
-- but much faster). lists=100 means it creates 100 clusters of similar vectors.
-- For a small project, this is fine. For millions of chunks, you'd increase lists.
--
CREATE INDEX IF NOT EXISTS code_chunks_embedding_idx
ON code_chunks USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);

-- Regular index for filtering by repo before doing similarity search
CREATE INDEX IF NOT EXISTS code_chunks_repo_id_idx ON code_chunks(repo_id);
