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
                    <td>
                        <a href="/ui/broken-versions/version/${version.groupId?url}/${version.artifactId?url}/${version.versionString?url}">${version.versionString}</a>
                    </td>
                    <td>${version.created}</td>
                    <td>${version.lastAttempt}</td>
                    <td>${version.attempts}</td>
                    <td>${version.retry?then('Y', 'N')}</td>
                </tr>
            </#list>
            </tbody>
        </table>

        <div style="margin-top: 2rem;">
            <form method="POST">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                <input type="hidden" name="action" value="retry-all"/>
                <button class="btn">Retry All</button>
            </form>
            <a href="/" class="btn btn-secondary">Back to Home</a>
        </div>
    </section>
</@page.layout>
