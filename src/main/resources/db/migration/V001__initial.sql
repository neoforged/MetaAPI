create table broken_software_component_version
(
    attempts     integer      not null,
    retry        boolean      not null,
    created      timestamp    not null,
    id,
    last_attempt timestamp    not null,
    artifact_id  varchar(255) not null,
    group_id     varchar(255) not null,
    version      varchar(255) not null,
    primary key (id)
);
create table broken_software_component_version_errors
(
    broken_software_component_version_id bigint not null,
    details                              varchar(255)
);
create table event
(
    created     timestamp    not null,
    id          integer,
    type        varchar(31)  not null check ((type in ('modified_component_version', 'new_component_version',
                                                       'removed_component_version'))),
    artifact_id varchar(255) not null,
    external_id blob         not null unique,
    group_id    varchar(255) not null,
    version     varchar(255) not null,
    primary key (id)
);
create table minecraft_version_libraries
(
    client_classpath     boolean,
    client_installer     boolean,
    download             boolean,
    server_installer     boolean,
    minecraft_version_id bigint       not null,
    size                 bigint,
    artifact_id          varchar(255) not null,
    classifier           varchar(255),
    extension            varchar(255),
    group_id             varchar(255) not null,
    sha1_checksum        varchar(255),
    url                  varchar(255),
    version              varchar(255) not null
);
create table minecraft_version
(
    imported     boolean           not null,
    java_version integer           not null,
    reimport     integer default 0 not null not null,
    id,
    released     timestamp         not null,
    type         varchar(255)      not null,
    version      varchar(255)      not null unique,
    primary key (id)
);
create table minecraft_version_manifest
(
    imported             boolean      not null,
    id                   integer,
    last_modified        timestamp    not null,
    minecraft_version_id bigint       not null unique,
    source_url           varchar(512),
    content              BLOB         not null,
    sha1                 varchar(255) not null,
    primary key (id)
);
create table neoforge_version
(
    id                   bigint       not null,
    minecraft_version_id bigint,
    installer_profile    BLOB         not null,
    launcher_profile     BLOB         not null,
    launcher_profile_id  varchar(255) not null,
    server_args_unix     BLOB         not null,
    server_args_windows  BLOB         not null,
    primary key (id)
);
create table neoforge_version_libraries
(
    client_classpath     boolean,
    client_installer     boolean,
    download             boolean,
    server_installer     boolean,
    neo_forge_version_id bigint       not null,
    size                 bigint,
    artifact_id          varchar(255) not null,
    classifier           varchar(255),
    extension            varchar(255),
    group_id             varchar(255) not null,
    sha1_checksum        varchar(255),
    url                  varchar(255),
    version              varchar(255) not null
);
create table software_component_artifact
(
    component_version_id bigint       not null,
    id                   integer,
    last_modified        timestamp    not null,
    size                 bigint       not null,
    classifier           varchar(255),
    etag                 varchar(255),
    extension            varchar(255),
    md5_checksum         varchar(255) not null,
    relative_path        varchar(255) not null,
    sha1_checksum        varchar(255) not null,
    sha256_checksum      varchar(255) not null,
    sha512_checksum      varchar(255) not null,
    primary key (id)
);
create table software_component_changelog
(
    component_version_id bigint       not null unique,
    id                   integer,
    changelog            varchar(255) not null,
    primary key (id)
);
create table software_component_version
(
    snapshot    boolean      not null,
    discovered  timestamp    not null,
    id          integer,
    released    timestamp    not null,
    artifact_id varchar(255) not null,
    group_id    varchar(255) not null,
    repository  varchar(255) not null,
    version     varchar(255) not null,
    primary key (id)
);
create table software_component_version_warnings
(
    software_component_version_id bigint not null,
    details                       varchar(255)
);
create index idx_broken_version_maven_group_artifact on broken_software_component_version (group_id, artifact_id);
create index idx_maven_group_artifact on software_component_version (group_id, artifact_id);
create table broken_software_component_version
(
    attempts     integer      not null,
    retry        boolean      not null,
    created      timestamp    not null,
    id,
    last_attempt timestamp    not null,
    artifact_id  varchar(255) not null,
    group_id     varchar(255) not null,
    version      varchar(255) not null,
    primary key (id)
);
create table broken_software_component_version_errors
(
    broken_software_component_version_id bigint not null,
    details                              varchar(255)
);
create table event
(
    created     timestamp    not null,
    id          integer,
    type        varchar(31)  not null check ((type in ('modified_component_version', 'new_component_version',
                                                       'removed_component_version'))),
    artifact_id varchar(255) not null,
    external_id blob         not null unique,
    group_id    varchar(255) not null,
    version     varchar(255) not null,
    primary key (id)
);
create table minecraft_version_libraries
(
    client_classpath     boolean,
    client_installer     boolean,
    download             boolean,
    server_installer     boolean,
    minecraft_version_id bigint       not null,
    size                 bigint,
    artifact_id          varchar(255) not null,
    classifier           varchar(255),
    extension            varchar(255),
    group_id             varchar(255) not null,
    sha1_checksum        varchar(255),
    url                  varchar(255),
    version              varchar(255) not null
);
create table minecraft_version
(
    imported     boolean           not null,
    java_version integer           not null,
    reimport     integer default 0 not null not null,
    id,
    released     timestamp         not null,
    type         varchar(255)      not null,
    version      varchar(255)      not null unique,
    primary key (id)
);
create table minecraft_version_manifest
(
    imported             boolean      not null,
    id                   integer,
    last_modified        timestamp    not null,
    minecraft_version_id bigint       not null unique,
    source_url           varchar(512),
    content              BLOB         not null,
    sha1                 varchar(255) not null,
    primary key (id)
);
create table neoforge_version
(
    id                   bigint       not null,
    minecraft_version_id bigint,
    installer_profile    BLOB         not null,
    launcher_profile     BLOB         not null,
    launcher_profile_id  varchar(255) not null,
    server_args_unix     BLOB         not null,
    server_args_windows  BLOB         not null,
    primary key (id)
);
create table neoforge_version_libraries
(
    client_classpath     boolean,
    client_installer     boolean,
    download             boolean,
    server_installer     boolean,
    neo_forge_version_id bigint       not null,
    size                 bigint,
    artifact_id          varchar(255) not null,
    classifier           varchar(255),
    extension            varchar(255),
    group_id             varchar(255) not null,
    sha1_checksum        varchar(255),
    url                  varchar(255),
    version              varchar(255) not null
);
create table software_component_artifact
(
    component_version_id bigint       not null,
    id                   integer,
    last_modified        timestamp    not null,
    size                 bigint       not null,
    classifier           varchar(255),
    etag                 varchar(255),
    extension            varchar(255),
    md5_checksum         varchar(255) not null,
    relative_path        varchar(255) not null,
    sha1_checksum        varchar(255) not null,
    sha256_checksum      varchar(255) not null,
    sha512_checksum      varchar(255) not null,
    primary key (id)
);
create table software_component_changelog
(
    component_version_id bigint       not null unique,
    id                   integer,
    changelog            varchar(255) not null,
    primary key (id)
);
create table software_component_version
(
    snapshot    boolean      not null,
    discovered  timestamp    not null,
    id          integer,
    released    timestamp    not null,
    artifact_id varchar(255) not null,
    group_id    varchar(255) not null,
    repository  varchar(255) not null,
    version     varchar(255) not null,
    primary key (id)
);
create table software_component_version_warnings
(
    software_component_version_id bigint not null,
    details                       varchar(255)
);
create index idx_broken_version_maven_group_artifact on broken_software_component_version (group_id, artifact_id);
create index idx_maven_group_artifact on software_component_version (group_id, artifact_id);
create table broken_software_component_version
(
    attempts     integer      not null,
    retry        boolean      not null,
    created      timestamp    not null,
    id,
    last_attempt timestamp    not null,
    artifact_id  varchar(255) not null,
    group_id     varchar(255) not null,
    version      varchar(255) not null,
    primary key (id)
);
create table broken_software_component_version_errors
(
    broken_software_component_version_id bigint not null,
    details                              varchar(255)
);
create table event
(
    created     timestamp    not null,
    id          integer,
    type        varchar(31)  not null check ((type in ('modified_component_version', 'new_component_version',
                                                       'removed_component_version'))),
    artifact_id varchar(255) not null,
    external_id blob         not null unique,
    group_id    varchar(255) not null,
    version     varchar(255) not null,
    primary key (id)
);
create table minecraft_version_libraries
(
    client_classpath     boolean,
    client_installer     boolean,
    download             boolean,
    server_installer     boolean,
    minecraft_version_id bigint       not null,
    size                 bigint,
    artifact_id          varchar(255) not null,
    classifier           varchar(255),
    extension            varchar(255),
    group_id             varchar(255) not null,
    sha1_checksum        varchar(255),
    url                  varchar(255),
    version              varchar(255) not null
);
create table minecraft_version
(
    imported     boolean           not null,
    java_version integer           not null,
    reimport     integer default 0 not null not null,
    id,
    released     timestamp         not null,
    type         varchar(255)      not null,
    version      varchar(255)      not null unique,
    primary key (id)
);
create table minecraft_version_manifest
(
    imported             boolean      not null,
    id                   integer,
    last_modified        timestamp    not null,
    minecraft_version_id bigint       not null unique,
    source_url           varchar(512),
    content              BLOB         not null,
    sha1                 varchar(255) not null,
    primary key (id)
);
create table neoforge_version
(
    id                   bigint       not null,
    minecraft_version_id bigint,
    installer_profile    BLOB         not null,
    launcher_profile     BLOB         not null,
    launcher_profile_id  varchar(255) not null,
    server_args_unix     BLOB         not null,
    server_args_windows  BLOB         not null,
    primary key (id)
);
create table neoforge_version_libraries
(
    client_classpath     boolean,
    client_installer     boolean,
    download             boolean,
    server_installer     boolean,
    neo_forge_version_id bigint       not null,
    size                 bigint,
    artifact_id          varchar(255) not null,
    classifier           varchar(255),
    extension            varchar(255),
    group_id             varchar(255) not null,
    sha1_checksum        varchar(255),
    url                  varchar(255),
    version              varchar(255) not null
);
create table software_component_artifact
(
    component_version_id bigint       not null,
    id                   integer,
    last_modified        timestamp    not null,
    size                 bigint       not null,
    classifier           varchar(255),
    etag                 varchar(255),
    extension            varchar(255),
    md5_checksum         varchar(255) not null,
    relative_path        varchar(255) not null,
    sha1_checksum        varchar(255) not null,
    sha256_checksum      varchar(255) not null,
    sha512_checksum      varchar(255) not null,
    primary key (id)
);
create table software_component_changelog
(
    component_version_id bigint       not null unique,
    id                   integer,
    changelog            varchar(255) not null,
    primary key (id)
);
create table software_component_version
(
    snapshot    boolean      not null,
    discovered  timestamp    not null,
    id          integer,
    released    timestamp    not null,
    artifact_id varchar(255) not null,
    group_id    varchar(255) not null,
    repository  varchar(255) not null,
    version     varchar(255) not null,
    primary key (id)
);
create table software_component_version_warnings
(
    software_component_version_id bigint not null,
    details                       varchar(255)
);
create index idx_broken_version_maven_group_artifact on broken_software_component_version (group_id, artifact_id);
create index idx_maven_group_artifact on software_component_version (group_id, artifact_id);
create table broken_software_component_version
(
    attempts     integer      not null,
    retry        boolean      not null,
    created      timestamp    not null,
    id,
    last_attempt timestamp    not null,
    artifact_id  varchar(255) not null,
    group_id     varchar(255) not null,
    version      varchar(255) not null,
    primary key (id)
);
create table broken_software_component_version_errors
(
    broken_software_component_version_id bigint not null,
    details                              varchar(255)
);
create table event
(
    created     timestamp    not null,
    id          integer,
    type        varchar(31)  not null check ((type in ('modified_component_version', 'new_component_version',
                                                       'removed_component_version'))),
    artifact_id varchar(255) not null,
    external_id blob         not null unique,
    group_id    varchar(255) not null,
    version     varchar(255) not null,
    primary key (id)
);
create table minecraft_version_libraries
(
    client_classpath     boolean,
    client_installer     boolean,
    download             boolean,
    server_installer     boolean,
    minecraft_version_id bigint       not null,
    size                 bigint,
    artifact_id          varchar(255) not null,
    classifier           varchar(255),
    extension            varchar(255),
    group_id             varchar(255) not null,
    sha1_checksum        varchar(255),
    url                  varchar(255),
    version              varchar(255) not null
);
create table minecraft_version
(
    imported     boolean           not null,
    java_version integer           not null,
    reimport     integer default 0 not null not null,
    id,
    released     timestamp         not null,
    type         varchar(255)      not null,
    version      varchar(255)      not null unique,
    primary key (id)
);
create table minecraft_version_manifest
(
    imported             boolean      not null,
    id                   integer,
    last_modified        timestamp    not null,
    minecraft_version_id bigint       not null unique,
    source_url           varchar(512),
    content              BLOB         not null,
    sha1                 varchar(255) not null,
    primary key (id)
);
create table neoforge_version
(
    id                   bigint       not null,
    minecraft_version_id bigint,
    installer_profile    BLOB         not null,
    launcher_profile     BLOB         not null,
    launcher_profile_id  varchar(255) not null,
    server_args_unix     BLOB         not null,
    server_args_windows  BLOB         not null,
    primary key (id)
);
create table neoforge_version_libraries
(
    client_classpath     boolean,
    client_installer     boolean,
    download             boolean,
    server_installer     boolean,
    neo_forge_version_id bigint       not null,
    size                 bigint,
    artifact_id          varchar(255) not null,
    classifier           varchar(255),
    extension            varchar(255),
    group_id             varchar(255) not null,
    sha1_checksum        varchar(255),
    url                  varchar(255),
    version              varchar(255) not null
);
create table software_component_artifact
(
    component_version_id bigint       not null,
    id                   integer,
    last_modified        timestamp    not null,
    size                 bigint       not null,
    classifier           varchar(255),
    etag                 varchar(255),
    extension            varchar(255),
    md5_checksum         varchar(255) not null,
    relative_path        varchar(255) not null,
    sha1_checksum        varchar(255) not null,
    sha256_checksum      varchar(255) not null,
    sha512_checksum      varchar(255) not null,
    primary key (id)
);
create table software_component_changelog
(
    component_version_id bigint       not null unique,
    id                   integer,
    changelog            varchar(255) not null,
    primary key (id)
);
create table software_component_version
(
    snapshot    boolean      not null,
    discovered  timestamp    not null,
    id          integer,
    released    timestamp    not null,
    artifact_id varchar(255) not null,
    group_id    varchar(255) not null,
    repository  varchar(255) not null,
    version     varchar(255) not null,
    primary key (id)
);
create table software_component_version_warnings
(
    software_component_version_id bigint not null,
    details                       varchar(255)
);
create index idx_broken_version_maven_group_artifact on broken_software_component_version (group_id, artifact_id);
create index idx_maven_group_artifact on software_component_version (group_id, artifact_id);
create table broken_software_component_version
(
    attempts     integer      not null,
    retry        boolean      not null,
    created      timestamp    not null,
    id,
    last_attempt timestamp    not null,
    artifact_id  varchar(255) not null,
    group_id     varchar(255) not null,
    version      varchar(255) not null,
    primary key (id)
);
create table broken_software_component_version_errors
(
    broken_software_component_version_id bigint not null,
    details                              varchar(255)
);
create table event
(
    created     timestamp    not null,
    id          integer,
    type        varchar(31)  not null check ((type in ('modified_component_version', 'new_component_version',
                                                       'removed_component_version'))),
    artifact_id varchar(255) not null,
    external_id blob         not null unique,
    group_id    varchar(255) not null,
    version     varchar(255) not null,
    primary key (id)
);
create table minecraft_version_libraries
(
    client_classpath     boolean,
    client_installer     boolean,
    download             boolean,
    server_installer     boolean,
    minecraft_version_id bigint       not null,
    size                 bigint,
    artifact_id          varchar(255) not null,
    classifier           varchar(255),
    extension            varchar(255),
    group_id             varchar(255) not null,
    sha1_checksum        varchar(255),
    url                  varchar(255),
    version              varchar(255) not null
);
create table minecraft_version
(
    imported     boolean           not null,
    java_version integer           not null,
    reimport     integer default 0 not null not null,
    id,
    released     timestamp         not null,
    type         varchar(255)      not null,
    version      varchar(255)      not null unique,
    primary key (id)
);
create table minecraft_version_manifest
(
    imported             boolean      not null,
    id                   integer,
    last_modified        timestamp    not null,
    minecraft_version_id bigint       not null unique,
    source_url           varchar(512),
    content              BLOB         not null,
    sha1                 varchar(255) not null,
    primary key (id)
);
create table neoforge_version
(
    id                   bigint       not null,
    minecraft_version_id bigint,
    installer_profile    BLOB         not null,
    launcher_profile     BLOB         not null,
    launcher_profile_id  varchar(255) not null,
    server_args_unix     BLOB         not null,
    server_args_windows  BLOB         not null,
    primary key (id)
);
create table neoforge_version_libraries
(
    client_classpath     boolean,
    client_installer     boolean,
    download             boolean,
    server_installer     boolean,
    neo_forge_version_id bigint       not null,
    size                 bigint,
    artifact_id          varchar(255) not null,
    classifier           varchar(255),
    extension            varchar(255),
    group_id             varchar(255) not null,
    sha1_checksum        varchar(255),
    url                  varchar(255),
    version              varchar(255) not null
);
create table software_component_artifact
(
    component_version_id bigint       not null,
    id                   integer,
    last_modified        timestamp    not null,
    size                 bigint       not null,
    classifier           varchar(255),
    etag                 varchar(255),
    extension            varchar(255),
    md5_checksum         varchar(255) not null,
    relative_path        varchar(255) not null,
    sha1_checksum        varchar(255) not null,
    sha256_checksum      varchar(255) not null,
    sha512_checksum      varchar(255) not null,
    primary key (id)
);
create table software_component_changelog
(
    component_version_id bigint       not null unique,
    id                   integer,
    changelog            varchar(255) not null,
    primary key (id)
);
create table software_component_version
(
    snapshot    boolean      not null,
    discovered  timestamp    not null,
    id          integer,
    released    timestamp    not null,
    artifact_id varchar(255) not null,
    group_id    varchar(255) not null,
    repository  varchar(255) not null,
    version     varchar(255) not null,
    primary key (id)
);
create table software_component_version_warnings
(
    software_component_version_id bigint not null,
    details                       varchar(255)
);
create index idx_broken_version_maven_group_artifact on broken_software_component_version (group_id, artifact_id);
create index idx_maven_group_artifact on software_component_version (group_id, artifact_id);
