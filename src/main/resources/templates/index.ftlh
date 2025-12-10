<#import "page.ftlh" as page>

<@page.layout title="NeoForge Meta API">
    <section class="welcome">
        <h2>Welcome to the NeoForge Meta API</h2>
        <p>This API provides metadata about NeoForge versions and Minecraft compatibility.</p>

        <#if brokenVersionCount gt 0>
            <div class="alert alert-danger">There are ${brokenVersionCount} <a href="/ui/broken-versions">broken
                    versions</a>.
            </div>
        </#if>

        <ul>
            <li><a href="/ui/minecraft-versions">Minecraft Versions</a></li>
            <li><a href="/ui/broken-versions">Broken Versions</a></li>
            <li><a href="/ui/jobs">Jobs</a></li>
        </ul>
    </section>
</@page.layout>
