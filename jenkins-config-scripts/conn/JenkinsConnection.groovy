package conn

class JenkinsConnection {
    String url
    String username
    String password

    static JenkinsConnection fromYaml(Map config) {
        new JenkinsConnection(
            url: config.server.url,
            username: config.server.auth.username,
            password: config.server.auth.password
        )
    }

    String basicAuthHeader() {
        "Basic " + "${username}:${password}".bytes.encodeBase64().toString()
    }
}