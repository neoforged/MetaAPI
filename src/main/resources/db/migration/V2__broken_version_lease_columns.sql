-- Phase 4: distributed retry leases for broken versions (HA deployments)

-- Rename the version column to make room for an optimistic locking field.
ALTER TABLE broken_software_component_version RENAME COLUMN version TO version_string;

-- Optimistic locking counter (JPA @Version on BrokenSoftwareComponentVersion).
ALTER TABLE broken_software_component_version ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Distributed retry lease: which instance is currently retrying this version, and until when.
ALTER TABLE broken_software_component_version ADD COLUMN retrying_by VARCHAR(255);
ALTER TABLE broken_software_component_version ADD COLUMN retrying_until TIMESTAMPTZ;
