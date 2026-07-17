package apis

import groovy.json.JsonSlurper
import conn.JenkinsConnection

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.CookieManager
import java.net.CookiePolicy

class JenkinsYamlApi {

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

    // Applies a JCasC yaml config file to the given Jenkins instance via
    // POST /configuration-as-code/apply (merges into the running config).
    static HttpResponse<String> applyYaml(JenkinsConnection conn, File yamlFile) {
        def client = newClient()
        def crumb = getCrumb(client, conn)

        def request = HttpRequest.newBuilder()
            .uri(URI.create("${conn.url}/configuration-as-code/apply"))
            .header("Authorization", conn.basicAuthHeader())
            .header(crumb.crumbRequestField, crumb.crumb)
            .header("Content-Type", "text/plain;charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofFile(yamlFile.toPath()))
            .build()

        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }
}
