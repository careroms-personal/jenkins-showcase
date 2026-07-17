import org.yaml.snakeyaml.Yaml
import conn.JenkinsConnection
import apis.JenkinsYamlApi

if (args.length < 1) {
    println "Usage: groovy apply-config.groovy <server-config.yaml>"
    System.exit(1)
}

def serverConfigFile = new File(args[0])
if (!serverConfigFile.exists()) {
    println "Server config file not found: ${serverConfigFile.path}"
    System.exit(1)
}

def config = new Yaml().load(serverConfigFile.text)
def jenkinsConn = JenkinsConnection.fromYaml(config)

println "Target Jenkins: ${jenkinsConn.url} (user: ${jenkinsConn.username})"

def jcacDir = new File("jcac")
def yamlFiles = jcacDir.listFiles({ f -> f.name.endsWith(".yaml") || f.name.endsWith(".yml") } as FileFilter)
    ?.sort { it.name }

if (!yamlFiles) {
    println "No yaml files found in ${jcacDir.path}"
    return
}

yamlFiles.each { file ->
    println "=== Applying ${file.name} ==="
    def response = JenkinsYamlApi.applyYaml(jenkinsConn, file)
    if (response.statusCode() != 200) {
        println "Failed to apply ${file.name}, status ${response.statusCode()}: ${response.body().take(300)}"
        System.exit(1)
    }
    println "Applied ${file.name} (status ${response.statusCode()})"
}

println "Done."
