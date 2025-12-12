<#-- @ftlvariable name="details" type="net.neoforged.meta.db.SoftwareComponentVersion" -->
<#-- @ftlvariable name="warnings" type="net.neoforged.meta.db.DiscoveryLogMessage[]" -->
<#-- @ftlvariable name="artifacts" type="net.neoforged.meta.db.SoftwareComponentArtifact[]" -->
<#-- @ftlvariable name="neoForgeVersion" type="net.neoforged.meta.db.NeoForgeVersion" -->
<#-- @ftlvariable name="neoForgeVersionLibraries" type="net.neoforged.meta.db.ReferencedLibrary[]" -->
<#import "page.ftlh" as page>

<@page.layout title="Component Version - NeoForge Meta API">
    <section>
        <h3>Component ${groupId}:${artifactId} Version ${version}</h3>

        <#if neoForgeVersion??>
            <p>Show <a href="/v1/neoforge-versions/version/${version?url}/">API</a> response.</p>
        </#if>

        <h3>Version Information</h3>
        <table>
            <tbody>
            <#if warnings?has_content>
                <#list warnings as warning>
                    <tr>
                        <#if warning_index == 0>
                            <th style="width: 200px;" rowspan="${warnings?size}" scope="rowgroup">Warnings</th>
                        </#if>
                        <td>${warning.details}</td>
                    </tr>
                </#list>
            <#else>
                <tr>
                    <th style="width: 200px;" scope="row">Warnings</th>
                    <td>No Warnings</td>
                </tr>
            </#if>
            <tr>
                <th style="width: 200px;" scope="row">Group ID</th>
                <td>${groupId}</td>
            </tr>
            <tr>
                <th style="width: 200px;" scope="row">Artifact ID</th>
                <td>${artifactId}</td>
            </tr>
            <tr>
                <th style="width: 200px;" scope="row">Version</th>
                <td>${version}</td>
            </tr>
            <#if neoForgeVersion??>
                <tr>
                    <th scope="row">Minecraft Version</th>
                    <td>
                        <a href="/ui/minecraft-versions/version/${neoForgeVersion.minecraftVersion.version?url}">${neoForgeVersion.minecraftVersion.version}</a>
                    </td>
                </tr>
                <tr>
                    <th scope="row">Launcher Profile</th>
                    <td>
                        <a href="/v1/neoforge-versions/version/${neoForgeVersion.version}/launcher-manifest/"
                           class="btn btn-secondary"
                           download="${neoForgeVersion.launcherProfileId?url}.json">Download</a>
                        <a href="/v1/neoforge-versions/version/${neoForgeVersion.version}/launcher-manifest/"
                           class="btn btn-secondary">View</a>
                    </td>
                </tr>
                <tr>
                    <th scope="row">Installer Profile</th>
                    <td>
                        <a href="/v1/neoforge-versions/version/${neoForgeVersion.version}/installer-profile/"
                           class="btn btn-secondary" download="installer_profile_${neoForgeVersion.version?url}.json">Download</a>
                        <a href="/v1/neoforge-versions/version/${neoForgeVersion.version}/installer-profile/"
                           class="btn btn-secondary">View</a>
                    </td>
                </tr>
                <tr>
                    <th scope="row">Server Args</th>
                    <td>
                        <a href="/v1/neoforge-versions/version/${neoForgeVersion.version}/server-args-unix/"
                           class="btn btn-secondary" download="unix_args_${neoForgeVersion.version?url}.txt">Download
                            Unix</a>
                        <a href="/v1/neoforge-versions/version/${neoForgeVersion.version}/server-args-unix/"
                           class="btn btn-secondary">View Unix</a>
                        <a href="/v1/neoforge-versions/version/${neoForgeVersion.version}/server-args-windows/"
                           class="btn btn-secondary" download="unix_args_${neoForgeVersion.version?url}.txt">Download
                            Windows</a>
                        <a href="/v1/neoforge-versions/version/${neoForgeVersion.version}/server-args-windows/"
                           class="btn btn-secondary">View Windows</a>
                    </td>
                </tr>
            </#if>
            <tr>
                <th style="width: 200px;" scope="row">Repository</th>
                <td>${details.repository}</td>
            </tr>
            <tr>
                <th style="width: 200px;" scope="row">Released</th>
                <td>${details.released}</td>
            </tr>
            <tr>
                <th style="width: 200px;" scope="row">Discovered</th>
                <td>${details.discovered}</td>
            </tr>
            <tr>
                <th style="width: 200px;" scope="row">Changelog</th>
                <td><#if details.releaseNotes??>${details.releaseNotes.html?no_esc}</#if></td>
            </tr>
            <tr>
                <th style="width: 200px;" scope="row">Original Changelog</th>
                <td><pre><#if details.releaseNotes??>${details.releaseNotes.originalText}</#if></pre></td>
            </tr>
            <tr>
                <th style="width: 200px;" scope="row">Artifacts</th>
                <td>
                    <#if artifacts?has_content>
                        <table>
                            <tr>
                                <th>File</th>
                                <th>Size</th>
                                <th>Last Modified</th>
                                <th>MD5</th>
                                <th>SHA1</th>
                                <th>SHA256</th>
                                <th>SHA512</th>
                            </tr>
                            <#list artifacts as artifact>
                                <tr>
                                    <td><a href="${repository.getDownloadUrl(artifact)}"
                                           download>${artifact.classifier!""}.${artifact.extension!""}</a></td>
                                    <td>${artifact.size}</td>
                                    <td>${artifact.lastModified}</td>
                                    <td>${artifact.md5Checksum???then("Y", "N")}</td>
                                    <td>${artifact.sha1Checksum???then("Y", "N")}</td>
                                    <td>${artifact.sha256Checksum???then("Y", "N")}</td>
                                    <td>${artifact.sha512Checksum???then("Y", "N")}</td>
                                </tr>
                            </#list>
                        </table>
                    <#else>
                        No Artifacts
                    </#if>
                </td>
            </tr>
            </tbody>
        </table>

        <#if neoForgeVersionLibraries??>
            <h3>Libraries (${neoForgeVersionLibraries?size})</h3>
            <table id="libraries" class="display">
                <thead>
                <tr>
                    <th rowspan="2">Artifact</th>
                    <th rowspan="2">Size</th>
                    <th colspan="4">Use</th>
                </tr>
                <tr>
                    <th title="client classpath">CCP</th>
                    <th title="client module-path">CMP</th>
                    <th title="client installer">CI</th>
                    <th title="server installer">SI</th>
                </tr>
                </thead>
                <tbody>
                <#list neoForgeVersionLibraries as library>
                    <tr>
                        <td><a href="${library.url}" download>${library.mavenComponentIdString}</a><br></td>
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
                        <td>${library.clientClasspath?then('CCP', '')}</td>
                        <td>${library.clientModulePath?then('CMP', '')}</td>
                        <td>${library.clientInstaller?then('CI', '')}</td>
                        <td>${library.serverInstaller?then('SI', '')}</td>
                    </tr>
                </#list>
                </tbody>
            </table>
        </#if>

        <p style="margin-top: 2rem;">
            <a href="/" class="btn btn-secondary">Back to Home</a>
        </p>
    </section>


    <script>
        document.addEventListener('DOMContentLoaded', function () {
            jQuery("#libraries").DataTable({
                paging: false,
                order: [[0, 'desc']],
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
