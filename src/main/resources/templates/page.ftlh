<#--
  Page layout macro for NeoForge Meta API

  Usage:
    <#import "page.ftlh" as page>
    <@page.layout title="Page Title">
      <section>
        <h2>Your content here</h2>
      </section>
    </@page.layout>
-->

<#macro layout title="NeoForge Meta API">
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${title}</title>
    <link rel="stylesheet" href="/css/style.css">
    <link rel="stylesheet" href="/css/datatables.css">
</head>
<body>
    <header>
        <h1><a href="/">NeoForge Meta API</a></h1>
        <nav>
            <#if user??>
                <div class="user-info">
                    <span>Welcome, ${user.name!user.principal}</span>
                    <#if roles?? && roles?size gt 0>
                        <span>
                            <#list roles as role>
                                <span class="role-badge <#if role == 'admin'>admin</#if>">${role}</span>
                            </#list>
                        </span>
                    </#if>
                    <a href="/logout" class="btn btn-secondary">Logout</a>
                </div>
            <#else>
                <a href="/oauth2/authorization/dex" class="btn">Login</a>
            </#if>
        </nav>
    </header>

    <main>
        <#nested>
    </main>

    <footer>
        <p>&copy; 2024 NeoForge. All rights reserved.</p>
    </footer>

    <script src="/js/jquery-3.7.1.slim.min.js"></script>
    <script src="/js/datatables.js"></script>
    <script src="/js/app.js"></script>
</body>
</html>
</#macro>
