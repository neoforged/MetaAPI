package net.neoforged.meta.db;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MinecraftVersionDao extends org.springframework.data.jpa.repository.JpaRepository<MinecraftVersion, Long> {

    @Query("from MinecraftVersion where version = :version")
    @EntityGraph("MinecraftVersion.full")
    @Nullable
    MinecraftVersion getByVersion(String version);

    @Query("select version from MinecraftVersion")
    List<String> getAllVersions();

    @Modifying
    @Query("update MinecraftVersion mv set mv.reimport = true where mv.imported = true")
    int setReimportForAll();

    @Modifying
    @Query("update MinecraftVersion mv set mv.reimport = true where mv.imported = true and mv.version = :version")
    int setReimportForVersion(String version);

}
