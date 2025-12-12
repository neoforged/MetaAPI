<#-- @ftlvariable name="latestNeoForgeVersions" type="java.util.Map<String, net.neoforged.meta.db.NeoForgeVersion>" -->
<#-- @ftlvariable name="minecraftVersions" type="net.neoforged.meta.db.MinecraftVersion[]" -->
<#import "page.ftlh" as page>

<@page.layout title="Minecraft Versions - NeoForge Meta API">
    <section>
        <h2>Minecraft Versions</h2>

        <p>Showing ${minecraftVersions?size} Minecraft versions. Show <a href="/v1/minecraft-versions/">API response</a>.
        </p>

        <table id="minecraftVersions" class="display">
            <thead>
            <tr>
                <th>Version</th>
                <th>Type</th>
                <th>Java Version</th>
                <th>Latest NeoForge</th>
                <th>Released</th>
            </tr>
            </thead>
            <tbody>
            <#list minecraftVersions as version>
                <tr>
                    <td><a href="/ui/minecraft-versions/version/${version.version?url}">${version.version}</a></td>
                    <td>${version.type}</td>
                    <td><#if version.javaVersion gt 0>${version.javaVersion}<#else>-</#if></td>
                    <td>
                        <#if latestNeoForgeVersions[version.version]??>
                            <#assign latestNeoForgeVersion=latestNeoForgeVersions[version.version]/>
                            <a
                            href="/ui/neoforge-versions/version/${latestNeoForgeVersion.version?url}">${latestNeoForgeVersion.version}</a></#if>
                    </td>
                    <td><#if version.released??>${version.released}<#else>-</#if></td>
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
            jQuery("#minecraftVersions").DataTable({
                paging: false,
                order: [[4, 'desc']],
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
