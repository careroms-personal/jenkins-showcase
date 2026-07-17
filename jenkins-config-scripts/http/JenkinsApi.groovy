package http

import groovy.json.JsonSlurper
import conn.JenkinsConnection

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.CookieManager
import java.net.CookiePolicy

class JenkinsApi {

    static HttpClient newClient() {
        def cookieManager = new CookieManager()
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL)
        return HttpClient.newBuilder()
            .cookieHandler(cookieManager)
            .build()
    }

    static def getCrumb(HttpClient client, JenkinsConnection conn) {
        def request = HttpRequest.newBuilder()
            .uri(URI.create("${conn.url}/crumbIssuer/api/json"))
            .header("Authorization", conn.basicAuthHeader())
            .GET()
            .build()

        def response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to get crumb, status ${response.statusCode()}: ${response.body().take(300)}")
        }
        return new JsonSlurper().parseText(response.body())
    }

    static HttpResponse<String> sendApply(HttpClient client, JenkinsConnection conn, def crumb, String yamlContent) {
        def request = HttpRequest.newBuilder()
            .uri(URI.create("${conn.url}/configuration-as-code/apply"))
            .header("Authorization", conn.basicAuthHeader())
            .header(crumb.crumbRequestField, crumb.crumb)
            .header("Content-Type", "text/plain;charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(yamlContent))
            .build()

        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    static String exportConfig(HttpClient client, JenkinsConnection conn, def crumb) {
        def request = HttpRequest.newBuilder()
            .uri(URI.create("${conn.url}/configuration-as-code/export"))
            .header("Authorization", conn.basicAuthHeader())
            .header(crumb.crumbRequestField, crumb.crumb)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()

        def response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to export config, status ${response.statusCode()}: ${response.body().take(300)}")
        }
        return response.body()
    }

    static HttpResponse<String> runScript(HttpClient client, JenkinsConnection conn, def crumb, String groovyScript) {
        def body = "script=" + URLEncoder.encode(groovyScript, "UTF-8")

        def request = HttpRequest.newBuilder()
            .uri(URI.create("${conn.url}/scriptText"))
            .header("Authorization", conn.basicAuthHeader())
            .header(crumb.crumbRequestField, crumb.crumb)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    static HttpResponse<String> createJob(HttpClient client, JenkinsConnection conn, def crumb, String jobName, String configXml) {
        def request = HttpRequest.newBuilder()
            .uri(URI.create("${conn.url}/createItem?name=${jobName}"))
            .header("Authorization", conn.basicAuthHeader())
            .header(crumb.crumbRequestField, crumb.crumb)
            .header("Content-Type", "application/xml")
            .POST(HttpRequest.BodyPublishers.ofString(configXml))
            .build()

        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    static boolean jobExists(HttpClient client, JenkinsConnection conn, String jobName) {
        def request = HttpRequest.newBuilder()
            .uri(URI.create("${conn.url}/job/${jobName}/api/json"))
            .header("Authorization", conn.basicAuthHeader())
            .GET()
            .build()

        def response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return response.statusCode() == 200
    }

    static HttpResponse<String> updateJob(HttpClient client, JenkinsConnection conn, def crumb, String jobName, String configXml) {
        def request = HttpRequest.newBuilder()
            .uri(URI.create("${conn.url}/job/${jobName}/config.xml"))
            .header("Authorization", conn.basicAuthHeader())
            .header(crumb.crumbRequestField, crumb.crumb)
            .header("Content-Type", "application/xml")
            .POST(HttpRequest.BodyPublishers.ofString(configXml))
            .build()

        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private static String folderUrlPrefix(String folderPath) {
        if (!folderPath) return ""
        return folderPath.split('/').collect { "job/${it}" }.join('/') + '/'
    }

    private static String folderConfigXml(String name) {
        """<?xml version='1.1' encoding='UTF-8'?>
<com.cloudbees.hudson.plugins.folder.Folder plugin="cloudbees-folder">
  <description></description>
  <properties/>
  <folderViews class="com.cloudbees.hudson.plugins.folder.views.DefaultFolderViewHolder">
    <views>
      <hudson.model.AllView>
        <owner class="com.cloudbees.hudson.plugins.folder.Folder" reference="../../../.."/>
        <name>All</name>
        <filterExecutors>false</filterExecutors>
        <filterQueue>false</filterQueue>
        <properties class="hudson.model.View\$PropertyList"/>
      </hudson.model.AllView>
    </views>
    <tabBar class="hudson.views.DefaultViewsTabBar"/>
  </folderViews>
  <healthMetrics/>
  <icon class="com.cloudbees.hudson.plugins.folder.icons.StockFolderIcon"/>
</com.cloudbees.hudson.plugins.folder.Folder>"""
    }

    static boolean folderExists(HttpClient client, JenkinsConnection conn, String folderPath) {
        if (!folderPath) return true
        def request = HttpRequest.newBuilder()
            .uri(URI.create("${conn.url}/${folderUrlPrefix(folderPath)}api/json"))
            .header("Authorization", conn.basicAuthHeader())
            .GET()
            .build()

        def response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return response.statusCode() == 200
    }

    static HttpResponse<String> createFolder(HttpClient client, JenkinsConnection conn, def crumb, String folderPath) {
        def segments = folderPath.split('/')
        def currentPath = ""
        HttpResponse<String> response = null

        segments.each { seg ->
            def parentUrlPrefix = folderUrlPrefix(currentPath)
            def checkPath = currentPath ? "${currentPath}/${seg}" : seg

            if (!folderExists(client, conn, checkPath)) {
                def request = HttpRequest.newBuilder()
                    .uri(URI.create("${conn.url}/${parentUrlPrefix}createItem?name=${URLEncoder.encode(seg, 'UTF-8')}&mode=com.cloudbees.hudson.plugins.folder.Folder"))
                    .header("Authorization", conn.basicAuthHeader())
                    .header(crumb.crumbRequestField, crumb.crumb)
                    .header("Content-Type", "application/xml")
                    .POST(HttpRequest.BodyPublishers.ofString(folderConfigXml(seg)))
                    .build()
                response = client.send(request, HttpResponse.BodyHandlers.ofString())
            }

            currentPath = checkPath
        }

        return response
    }

    static boolean jobExistsInFolder(HttpClient client, JenkinsConnection conn, String folderPath, String jobName) {
        def request = HttpRequest.newBuilder()
            .uri(URI.create("${conn.url}/${folderUrlPrefix(folderPath)}job/${jobName}/api/json"))
            .header("Authorization", conn.basicAuthHeader())
            .GET()
            .build()

        def response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return response.statusCode() == 200
    }

    static HttpResponse<String> createJobInFolder(HttpClient client, JenkinsConnection conn, def crumb, String folderPath, String jobName, String configXml) {
        def request = HttpRequest.newBuilder()
            .uri(URI.create("${conn.url}/${folderUrlPrefix(folderPath)}createItem?name=${URLEncoder.encode(jobName, 'UTF-8')}"))
            .header("Authorization", conn.basicAuthHeader())
            .header(crumb.crumbRequestField, crumb.crumb)
            .header("Content-Type", "application/xml")
            .POST(HttpRequest.BodyPublishers.ofString(configXml))
            .build()

        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    static HttpResponse<String> updateJobInFolder(HttpClient client, JenkinsConnection conn, def crumb, String folderPath, String jobName, String configXml) {
        def request = HttpRequest.newBuilder()
            .uri(URI.create("${conn.url}/${folderUrlPrefix(folderPath)}job/${jobName}/config.xml"))
            .header("Authorization", conn.basicAuthHeader())
            .header(crumb.crumbRequestField, crumb.crumb)
            .header("Content-Type", "application/xml")
            .POST(HttpRequest.BodyPublishers.ofString(configXml))
            .build()

        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }
}