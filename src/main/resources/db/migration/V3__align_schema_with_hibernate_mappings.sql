-- Aligns the hand-written V1 schema with Hibernate's actual entity mappings so that
-- `ddl-auto: validate` passes on PostgreSQL. The postgres profile was never usable before
-- this migration (startup always failed at schema validation).

-- ============================================================================
-- Element collection join columns for NeoForgeVersion collections must use
-- Hibernate's derived name (snake_case of the owning entity class) instead of
-- the table-derived name used in V1.
-- ============================================================================

ALTER TABLE neoforge_client_jvm_args RENAME COLUMN neoforge_version_id TO neo_forge_version_id;
ALTER TABLE neoforge_client_program_args RENAME COLUMN neoforge_version_id TO neo_forge_version_id;
ALTER TABLE neoforge_server_jvm_args RENAME COLUMN neoforge_version_id TO neo_forge_version_id;
ALTER TABLE neoforge_server_program_args RENAME COLUMN neoforge_version_id TO neo_forge_version_id;
ALTER TABLE neoforge_version_libraries RENAME COLUMN neoforge_version_id TO neo_forge_version_id;

-- ============================================================================
-- @OrderColumn names derived from the embedded collection property name.
-- ============================================================================

ALTER TABLE neoforge_client_jvm_args RENAME COLUMN ordinal TO jvm_args_order;
ALTER TABLE neoforge_client_program_args RENAME COLUMN ordinal TO program_args_order;
ALTER TABLE neoforge_server_jvm_args RENAME COLUMN ordinal TO jvm_args_order;
ALTER TABLE neoforge_server_program_args RENAME COLUMN ordinal TO program_args_order;

-- ============================================================================
-- Nullability alignment with the JPA mappings.
-- ============================================================================

ALTER TABLE neoforge_version ALTER COLUMN client_main_class DROP NOT NULL;
ALTER TABLE neoforge_version ALTER COLUMN server_main_class DROP NOT NULL;

ALTER TABLE minecraft_version_libraries ALTER COLUMN client_classpath DROP NOT NULL;
ALTER TABLE minecraft_version_libraries ALTER COLUMN client_module_path DROP NOT NULL;
ALTER TABLE minecraft_version_libraries ALTER COLUMN client_installer DROP NOT NULL;
ALTER TABLE minecraft_version_libraries ALTER COLUMN server_installer DROP NOT NULL;

ALTER TABLE neoforge_version_libraries ALTER COLUMN client_classpath DROP NOT NULL;
ALTER TABLE neoforge_version_libraries ALTER COLUMN client_module_path DROP NOT NULL;
ALTER TABLE neoforge_version_libraries ALTER COLUMN client_installer DROP NOT NULL;
ALTER TABLE neoforge_version_libraries ALTER COLUMN server_installer DROP NOT NULL;

-- Optimistic lock column is nullable in Hibernate's mapping.
ALTER TABLE broken_software_component_version ALTER COLUMN version DROP NOT NULL;
