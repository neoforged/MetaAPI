<#-- @ftlvariable name="version" type="net.neoforged.meta.db.MinecraftVersion" -->
<#import "page.ftlh" as page>

<@page.layout title="Minecraft ${version.version} - NeoForge Meta API">
    <section>
        <h2>Minecraft ${version.version}</h2>

        <h3>Version Information</h3>
        <table>
            <tbody>
            <tr>
                <th style="width: 200px;">Version</th>
                <td>${version.version}</td>
            </tr>
            <tr>
                <th>Type</th>
                <td>${version.type}</td>
            </tr>
            <tr>
                <th>Released</th>
                <td>${version.released}</td>
            </tr>
            <tr>
                <th>Java Version</th>
                <td>${version.javaVersion}</td>
            </tr>
            <tr>
                <th>Imported</th>
                <td>${version.imported?string("Yes", "No")}</td>
            </tr>
            <tr>
                <th>Reimport</th>
                <td>
                    ${version.reimport?string("Yes", "No")}
                    <form action="/ui/minecraft-versions/version/${version.version?url}/reimport" method="POST">
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                        <button class="btn">Reimport</button>
                    </form>
                </td>
            </tr>
            </tbody>
        </table>

        <h3>Version Manifest</h3>
        <table>
            <tbody>
            <tr>
                <th style="width: 200px;">SHA-1</th>
                <td><code>${version.manifest.sha1}</code></td>
            </tr>
            <tr>
                <th>Last Modified</th>
                <td>${version.manifest.lastModified}</td>
            </tr>
            <#if version.manifest.sourceUrl??>
                <tr>
                    <th>Source URL</th>
                    <td><a href="${version.manifest.sourceUrl?url}"
                           target="_blank">${version.manifest.sourceUrl}</a></td>
                </tr>
            </#if>
            <tr>
                <th>Download</th>
                <td>
                    <a href="/v1/minecraft-versions/version/${version.version?url}/version-manifest/" class="btn"
                       download="version-manifest-${version.version?url}.json">
                        Download Manifest JSON
                    </a>
                    <a href="/v1/minecraft-versions/version/${version.version?url}/version-manifest/" class="btn">
                        View Manifest JSON
                    </a>
                </td>
            </tr>
            </tbody>
        </table>

        <h3>Libraries (${version.libraries?size})</h3>
        <table>
            <thead>
            <tr>
                <th>Artifact</th>
                <th>Size</th>
            </tr>
            </thead>
            <tbody>
            <#list version.libraries as library>
                <tr>
                    <td><a href="${library.url}" target="_blank" download>${library.mavenComponentIdString}</a></td>
                    <td>
                        <#if library.size??>
                            <#assign sizeKb = library.size / 1024>
                            <#if sizeKb lt 1024>
                                ${sizeKb?string("0.0")} KB
                            <#else>
                                ${(sizeKb / 1024)?string("0.0")} MB
                            </#if>
                        <#else>
                            -
                        </#if>
                    </td>
                </tr>
            </#list>
            </tbody>
        </table>

        <p style="margin-top: 2rem;">
            <a href="/ui/minecraft-versions" class="btn btn-secondary">Back to Versions List</a>
        </p>
    </section>
</@page.layout>
