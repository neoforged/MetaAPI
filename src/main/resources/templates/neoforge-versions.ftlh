<#-- @ftlvariable name="versions" type="net.neoforged.meta.db.NeoForgeVersion[]" -->
<#import "page.ftlh" as page>

<@page.layout title="NeoForge Versions - NeoForge Meta API">
    <section>
        <h2>NeoForge Versions</h2>

        <p>Showing ${versions?size} NeoForge versions. Show <a href="/v1/neoforge-versions/">API</a> response.</p>

        <table id="neoForgeVersions" class="display">
            <thead>
            <tr>
                <th>Version</th>
                <th>Minecraft</th>
                <th>Released</th>
                <th>Changelog</th>
            </tr>
            </thead>
            <tbody>
            <#list versions as version>
                <tr>
                    <td><a href="/ui/components/${version.groupId?url}/${version.artifactId?url}/versions/${version.version?url}">${version.version}</a></td>
                    <td><a href="/ui/minecraft-versions/version/${version.minecraftVersion.version?url}">${version.minecraftVersion.version}</a></td>
                    <td><#if version.released??>${version.released}<#else>-</#if></td>
                    <td class="changelog"><#if version.releaseNotes??>${version.releaseNotes.html?no_esc}</#if></td>
                </tr>
            </#list>
            </tbody>
        </table>

        <p style="margin-top: 2rem;">
            <a href="/" class="btn btn-secondary">Back to Home</a>
        </p>
    </section>

    <script>
        document.addEventListener('DOMContentLoaded', function () {
            jQuery("#neoForgeVersions").DataTable({
                paging: false,
                order: [[2, 'desc']],
                fixedHeader: true,
                columnControl: [
                    {
                        target: 0,
                        content: ['order', ['searchList']]
                    },
                    {
                        target: 1,
                        content: ['search']
                    }
                ],
                ordering: {
                    indicators: false,
                    handler: false
                }
            });

        });
    </script>
</@page.layout>
