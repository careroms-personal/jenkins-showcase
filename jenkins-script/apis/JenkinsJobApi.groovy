package apis

import groovy.json.JsonSlurper
import conn.JenkinsConnection

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.CookieManager
import java.net.CookiePolicy

class JenkinsJobApi {

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

    private static boolean jobExists(HttpClient client, JenkinsConnection conn, String jobName) {
        def request = HttpRequest.newBuilder()
            .uri(URI.create("${conn.url}/job/${jobName}/api/json"))
            .header("Authorization", conn.basicAuthHeader())
            .GET()
            .build()

        def response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return response.statusCode() == 200
    }

    // Creates the job from configXmlFile if it doesn't exist yet, otherwise
    // updates its config.xml in place.
    static HttpResponse<String> applyJob(JenkinsConnection conn, String jobName, File configXmlFile) {
        def client = newClient()
        def crumb = getCrumb(client, conn)

        def uri = jobExists(client, conn, jobName)
            ? URI.create("${conn.url}/job/${jobName}/config.xml")
            : URI.create("${conn.url}/createItem?name=${URLEncoder.encode(jobName, 'UTF-8')}")

        def request = HttpRequest.newBuilder()
            .uri(uri)
            .header("Authorization", conn.basicAuthHeader())
            .header(crumb.crumbRequestField, crumb.crumb)
            .header("Content-Type", "application/xml")
            .POST(HttpRequest.BodyPublishers.ofFile(configXmlFile.toPath()))
            .build()

        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    // Lists the names of all top-level jobs on the given Jenkins instance.
    static List<String> listJobNames(JenkinsConnection conn) {
        def client = newClient()
        def request = HttpRequest.newBuilder()
            .uri(URI.create("${conn.url}/api/json?tree=jobs[name]"))
            .header("Authorization", conn.basicAuthHeader())
            .GET()
            .build()

        def response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to list jobs, status ${response.statusCode()}: ${response.body().take(300)}")
        }
        def data = new JsonSlurper().parseText(response.body())
        return data.jobs.collect { it.name as String }
    }

    // Fetches a job's live config.xml and writes it to destFile, for backup.
    static HttpResponse<String> pullJob(JenkinsConnection conn, String jobName, File destFile) {
        def client = newClient()
        def request = HttpRequest.newBuilder()
            .uri(URI.create("${conn.url}/job/${jobName}/config.xml"))
            .header("Authorization", conn.basicAuthHeader())
            .GET()
            .build()

        def response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 200) {
            destFile.parentFile?.mkdirs()
            destFile.text = response.body()
        }
        return response
    }
}
