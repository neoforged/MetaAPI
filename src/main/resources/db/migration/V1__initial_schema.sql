-- Initial schema migration for MetaAPI
-- Generated from JPA entity mappings, adapted for PostgreSQL

-- ============================================================================
-- Software Component Versions (with JOINED inheritance)
-- ============================================================================

CREATE TABLE software_component_version (
    id BIGSERIAL PRIMARY KEY,
    group_id VARCHAR(255) NOT NULL,
    artifact_id VARCHAR(255) NOT NULL,
    version VARCHAR(255) NOT NULL,
    snapshot BOOLEAN NOT NULL,
    repository VARCHAR(255) NOT NULL,
    released TIMESTAMPTZ NOT NULL,
    discovered TIMESTAMPTZ NOT NULL,
    last_modified TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_maven_group_artifact ON software_component_version (group_id, artifact_id);
CREATE UNIQUE INDEX idx_maven_gav ON software_component_version (group_id, artifact_id, version);

-- NeoForge version (extends SoftwareComponentVersion via JOINED inheritance)
CREATE TABLE neoforge_version (
    id BIGINT PRIMARY KEY REFERENCES software_component_version (id),
    minecraft_version_id BIGINT,
    launcher_profile_id VARCHAR(255) NOT NULL,
    launcher_profile BYTEA NOT NULL,
    installer_profile BYTEA NOT NULL,
    client_main_class VARCHAR(255) NOT NULL,
    server_main_class VARCHAR(255) NOT NULL
);

-- ============================================================================
-- Minecraft Versions
-- ============================================================================

CREATE TABLE minecraft_version (
    id BIGSERIAL PRIMARY KEY,
    version VARCHAR(255) NOT NULL UNIQUE,
    type VARCHAR(255) NOT NULL,
    released TIMESTAMPTZ NOT NULL,
    discovered TIMESTAMPTZ NOT NULL,
    last_modified TIMESTAMPTZ NOT NULL,
    imported BOOLEAN NOT NULL,
    java_version INTEGER NOT NULL,
    reimport BOOLEAN NOT NULL
);

ALTER TABLE neoforge_version
    ADD CONSTRAINT fk_neoforge_minecraft_version
    FOREIGN KEY (minecraft_version_id) REFERENCES minecraft_version (id);

-- ============================================================================
-- Minecraft Version Manifests
-- ============================================================================

CREATE TABLE minecraft_version_manifest (
    id BIGSERIAL PRIMARY KEY,
    minecraft_version_id BIGINT NOT NULL UNIQUE REFERENCES minecraft_version (id),
    imported BOOLEAN NOT NULL,
    sha1 VARCHAR(255) NOT NULL,
    last_modified TIMESTAMPTZ NOT NULL,
    source_url VARCHAR(512),
    content BYTEA NOT NULL
);

-- ============================================================================
-- Software Component Artifacts
-- ============================================================================

CREATE TABLE software_component_artifact (
    id BIGSERIAL PRIMARY KEY,
    component_version_id BIGINT NOT NULL REFERENCES software_component_version (id),
    relative_path VARCHAR(255) NOT NULL,
    classifier VARCHAR(255),
    extension VARCHAR(255),
    size BIGINT NOT NULL,
    last_modified TIMESTAMPTZ NOT NULL,
    etag VARCHAR(255),
    md5_checksum VARCHAR(255) NOT NULL,
    sha1_checksum VARCHAR(255) NOT NULL,
    sha256_checksum VARCHAR(255) NOT NULL,
    sha512_checksum VARCHAR(255) NOT NULL
);

CREATE UNIQUE INDEX idx_artifact_version_classifier_ext ON software_component_artifact (component_version_id, classifier, extension);

-- ============================================================================
-- Release Notes
-- ============================================================================

CREATE TABLE software_component_release_notes (
    id BIGSERIAL PRIMARY KEY,
    component_version_id BIGINT NOT NULL UNIQUE REFERENCES software_component_version (id),
    original_text TEXT NOT NULL,
    text TEXT NOT NULL,
    markdown TEXT NOT NULL
);

-- ============================================================================
-- Element Collections
-- ============================================================================

-- Warnings for software component versions (DiscoveryLogMessage)
CREATE TABLE software_component_version_warnings (
    software_component_version_id BIGINT NOT NULL REFERENCES software_component_version (id),
    details TEXT
);

-- Libraries for Minecraft versions (ReferencedLibrary)
CREATE TABLE minecraft_version_libraries (
    minecraft_version_id BIGINT NOT NULL REFERENCES minecraft_version (id),
    group_id VARCHAR(255) NOT NULL,
    artifact_id VARCHAR(255) NOT NULL,
    version VARCHAR(255) NOT NULL,
    classifier VARCHAR(255),
    extension VARCHAR(255),
    client_classpath BOOLEAN NOT NULL,
    client_module_path BOOLEAN NOT NULL,
    client_installer BOOLEAN NOT NULL,
    server_installer BOOLEAN NOT NULL,
    sha1_checksum VARCHAR(255),
    size BIGINT,
    url VARCHAR(255)
);

-- Libraries for NeoForge versions (ReferencedLibrary)
CREATE TABLE neoforge_version_libraries (
    neoforge_version_id BIGINT NOT NULL REFERENCES neoforge_version (id),
    group_id VARCHAR(255) NOT NULL,
    artifact_id VARCHAR(255) NOT NULL,
    version VARCHAR(255) NOT NULL,
    classifier VARCHAR(255),
    extension VARCHAR(255),
    client_classpath BOOLEAN NOT NULL,
    client_module_path BOOLEAN NOT NULL,
    client_installer BOOLEAN NOT NULL,
    server_installer BOOLEAN NOT NULL,
    sha1_checksum VARCHAR(255),
    size BIGINT,
    url VARCHAR(255)
);

-- Client startup JVM args (StartupArgument)
CREATE TABLE neoforge_client_jvm_args (
    neoforge_version_id BIGINT NOT NULL REFERENCES neoforge_version (id),
    ordinal INTEGER NOT NULL,
    argument VARCHAR(255) NOT NULL,
    windows BOOLEAN NOT NULL,
    linux BOOLEAN NOT NULL,
    mac BOOLEAN NOT NULL
);

-- Client startup program args (StartupArgument)
CREATE TABLE neoforge_client_program_args (
    neoforge_version_id BIGINT NOT NULL REFERENCES neoforge_version (id),
    ordinal INTEGER NOT NULL,
    argument VARCHAR(255) NOT NULL,
    windows BOOLEAN NOT NULL,
    linux BOOLEAN NOT NULL,
    mac BOOLEAN NOT NULL
);

-- Server startup JVM args (StartupArgument)
CREATE TABLE neoforge_server_jvm_args (
    neoforge_version_id BIGINT NOT NULL REFERENCES neoforge_version (id),
    ordinal INTEGER NOT NULL,
    argument VARCHAR(255) NOT NULL,
    windows BOOLEAN NOT NULL,
    linux BOOLEAN NOT NULL,
    mac BOOLEAN NOT NULL
);

-- Server startup program args (StartupArgument)
CREATE TABLE neoforge_server_program_args (
    neoforge_version_id BIGINT NOT NULL REFERENCES neoforge_version (id),
    ordinal INTEGER NOT NULL,
    argument VARCHAR(255) NOT NULL,
    windows BOOLEAN NOT NULL,
    linux BOOLEAN NOT NULL,
    mac BOOLEAN NOT NULL
);

-- ============================================================================
-- Broken Software Component Versions
-- ============================================================================

CREATE TABLE broken_software_component_version (
    id BIGSERIAL PRIMARY KEY,
    group_id VARCHAR(255) NOT NULL,
    artifact_id VARCHAR(255) NOT NULL,
    version VARCHAR(255) NOT NULL,
    created TIMESTAMPTZ NOT NULL,
    last_attempt TIMESTAMPTZ NOT NULL,
    retry BOOLEAN NOT NULL DEFAULT FALSE,
    attempts INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_broken_version_maven_group_artifact ON broken_software_component_version (group_id, artifact_id);
CREATE UNIQUE INDEX idx_broken_version_maven_gav ON broken_software_component_version (group_id, artifact_id, version);

-- Errors for broken versions (DiscoveryLogMessage)
CREATE TABLE broken_software_component_version_errors (
    broken_software_component_version_id BIGINT NOT NULL REFERENCES broken_software_component_version (id),
    details TEXT
);

-- ============================================================================
-- Event Receiver State
-- ============================================================================

CREATE TABLE event_receiver_state (
    id VARCHAR(255) PRIMARY KEY,
    version BIGINT,
    delivery_state VARCHAR(50) NOT NULL DEFAULT 'NEVER',
    highest_event_id_seen BIGINT,
    events_delivered BIGINT NOT NULL DEFAULT 0,
    last_success TIMESTAMPTZ,
    last_failure TIMESTAMPTZ,
    consecutive_failures INTEGER NOT NULL DEFAULT 0,
    failing_since TIMESTAMPTZ,
    last_failure_message TEXT,
    paused BOOLEAN NOT NULL DEFAULT FALSE,
    paused_until TIMESTAMPTZ
);

-- ============================================================================
-- Events (SINGLE TABLE inheritance with discriminator)
-- ============================================================================

CREATE TABLE event (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    external_id UUID NOT NULL,
    created TIMESTAMPTZ NOT NULL,
    group_id VARCHAR(255),
    artifact_id VARCHAR(255),
    version VARCHAR(255)
);

CREATE UNIQUE INDEX idx_event_external_id ON event (external_id);

-- ============================================================================
-- Distributed Job Locks (for HA scheduling)
-- ============================================================================

CREATE TABLE job_lock (
    job_id VARCHAR(255) PRIMARY KEY,
    locked_at TIMESTAMPTZ,
    locked_by VARCHAR(255),
    expires_at TIMESTAMPTZ
);
