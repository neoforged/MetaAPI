<#-- @ftlvariable name="minecraftVersions" type="net.neoforged.meta.db.MinecraftVersion[]" -->
<#import "page.ftlh" as page>

<@page.layout title="Minecraft Versions - NeoForge Meta API">
    <section>
        <h2>Minecraft Versions</h2>

        <p>Showing ${minecraftVersions?size} Minecraft versions. Show <a href="/v1/versions/minecraft/">/v1/versions/minecraft/</a> response.</p>

        <table id="minecraftVersions" class="display">
            <thead>
            <tr>
                <th>Version</th>
                <th>Type</th>
                <th>Java Version</th>
                <th>Release Time</th>
            </tr>
            </thead>
            <tbody>
            <#list minecraftVersions as version>
                <tr>
                    <td><a href="/ui/minecraft-versions/version/${version.version?url}">${version.version}</a></td>
                    <td>${version.type}</td>
                    <td><#if version.javaVersion gt 0>${version.javaVersion}<#else>-</#if></td>
                    <td><#if version.released??>${version.released}<#else>-</#if></td>
                </tr>
            </#list>
            </tbody>
        </table>

        <p style="margin-top: 2rem;">
            <a href="/" class="btn btn-secondary">Back to Home</a>
        </p>
    </section>
</@page.layout>
