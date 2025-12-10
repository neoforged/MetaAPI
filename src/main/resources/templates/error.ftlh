<#import "page.ftlh" as page>

<@page.layout title="NeoForge Meta API">
    <div class="error-container">
    <h1 class="error-code">${status!"Error"}</h1>
    <h2 class="error-message">${error!"An unexpected error occurred"}</h2>

    <#if message?? && message != "No message available">
        <p style="color: #666; margin: 15px 0;">${message}</p>
    </#if>

    <div class="error-details">
        <#if status??>
            <p><strong>Status:</strong> ${status}</p>
        </#if>
        <#if error??>
            <p><strong>Error:</strong> ${error}</p>
        </#if>
        <#if path??>
            <p><strong>Path:</strong> ${path}</p>
        </#if>
        <#if timestamp??>
            <p><strong>Timestamp:</strong> ${timestamp?string('yyyy-MM-dd HH:mm:ss')}</p>
        </#if>
        <#if exception??>
            <details style="margin-top: 15px;">
                <summary style="cursor: pointer; color: #667eea; font-weight: bold;">Exception Details</summary>
                <pre style="text-align: left; font-size: 11px; overflow-x: auto; margin-top: 10px; color: #333; white-space: pre-wrap;">${exception}</pre>
            </details>
        </#if>
        <#if trace??>
            <details style="margin-top: 15px;">
                <summary style="cursor: pointer; color: #667eea; font-weight: bold;">Stack Trace</summary>
                <pre style="text-align: left; font-size: 11px; overflow-x: auto; margin-top: 10px; color: #333; white-space: pre-wrap;">${trace}</pre>
            </details>
        </#if>
    </div>

    <a href="/" class="btn">Go Home</a>

    <#if timestamp??>
        <p class="timestamp">
            Error occurred at ${timestamp?string('yyyy-MM-dd HH:mm:ss')}
        </p>
    </#if>
    </div>
</@page.layout>
