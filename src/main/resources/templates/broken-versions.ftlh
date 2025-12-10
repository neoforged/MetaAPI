<#-- @ftlvariable name="versions" type="net.neoforged.meta.db.BrokenSoftwareComponentVersion[]" -->
<#import "page.ftlh" as page>

<@page.layout title="Broken Versions - NeoForge Meta API">
    <section>
        <h2>Broken Versions</h2>

        <p>Showing ${versions?size} broken versions.</p>

        <table id="versions" class="display">
            <thead>
            <tr>
                <th>Group</th>
                <th>Artifact</th>
                <th>Version</th>
                <th>First Attempt</th>
                <th>Last Attempt</th>
                <th>Attempts</th>
                <th>Retry</th>
            </tr>
            </thead>
            <tbody>
            <#list versions as version>
                <tr>
                    <td>${version.groupId}</td>
                    <td>${version.artifactId}</td>
                    <td><a href="/ui/broken-versions/version/${version.id?url}">${version.version}</a></td>
                    <td>${version.created}</td>
                    <td>${version.lastAttempt}</td>
                    <td>${version.attempts}</td>
                    <td><button>Retry</button></td>
                </tr>
            </#list>
            </tbody>
        </table>

        <p style="margin-top: 2rem;">
            <a href="/" class="btn btn-secondary">Back to Home</a>
        </p>
    </section>
</@page.layout>
