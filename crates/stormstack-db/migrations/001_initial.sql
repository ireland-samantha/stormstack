-- StormStack Initial Database Schema
-- Migration 001: Create core tables for containers, matches, and users

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Containers table
-- Containers provide isolated execution environments for game matches
CREATE TABLE IF NOT EXISTS containers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    current_tick BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for tenant isolation queries
CREATE INDEX IF NOT EXISTS idx_containers_tenant_id ON containers(tenant_id);

-- Matches table
-- Matches represent active game sessions within a container
CREATE TABLE IF NOT EXISTS matches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    container_id UUID NOT NULL REFERENCES containers(id) ON DELETE CASCADE,
    state VARCHAR(50) NOT NULL DEFAULT 'pending' CHECK (state IN ('pending', 'active', 'completed')),
    game_mode VARCHAR(100) NOT NULL,
    max_players INTEGER NOT NULL DEFAULT 10,
    current_tick BIGINT NOT NULL DEFAULT 0,
    players JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for container lookups
CREATE INDEX IF NOT EXISTS idx_matches_container_id ON matches(container_id);

-- Index for state filtering
CREATE INDEX IF NOT EXISTS idx_matches_state ON matches(state);

-- Users table
-- Users are authenticated principals within a tenant
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    roles JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Unique constraint per tenant
    CONSTRAINT users_email_tenant_unique UNIQUE (tenant_id, email)
);

-- Index for tenant isolation queries
CREATE INDEX IF NOT EXISTS idx_users_tenant_id ON users(tenant_id);

-- Index for email lookups (global, for login)
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- Trigger function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply trigger to containers table
DROP TRIGGER IF EXISTS containers_updated_at ON containers;
CREATE TRIGGER containers_updated_at
    BEFORE UPDATE ON containers
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
