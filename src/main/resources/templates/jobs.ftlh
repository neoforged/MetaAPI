<#-- @ftlvariable name="jobs" type="net.neoforged.meta.web.JobModel[]" -->
<#import "page.ftlh" as page>

<@page.layout title="Jobs - NeoForge Meta API">
    <section>
        <h2>Jobs</h2>

        <table id="jobs" class="display">
            <thead>
            <tr>
                <th>Job</th>
                <th>Next Run</th>
                <th>Last Run</th>
                <th>Last Status</th>
                <th>Last Error</th>
            </tr>
            </thead>
            <tbody>
            <#list jobs as job>
                <tr>
                    <td>${job.name()}</td>
                    <td><#if job.nextExecution()??><span title="${job.absoluteNextExecution()}">${job.relativeNextExecution()}</span><#else>-</#if></td>
                    <td><#if job.lastExecution()??><span title="${job.absoluteLastExecution()}">${job.relativeLastExecution()}</span><#else>-</#if></td>
                    <td>${job.lastStatus()}</td>
                    <td>${job.lastError()!"-"}</td>
                </tr>
            </#list>
            </tbody>
        </table>

        <p style="margin-top: 2rem;">
            <a href="/" class="btn btn-secondary">Back to Home</a>
        </p>
    </section>
</@page.layout>
