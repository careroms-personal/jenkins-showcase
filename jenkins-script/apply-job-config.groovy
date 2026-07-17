import org.yaml.snakeyaml.Yaml
import conn.JenkinsConnection
import apis.JenkinsJobApi

if (args.length < 1) {
    println "Usage: groovy apply-job-config.groovy <server-config.yaml>"
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

def jobsDir = new File("jobs")
def jobFiles = jobsDir.listFiles({ f -> f.name.endsWith(".xml") } as FileFilter)
    ?.sort { it.name }

if (!jobFiles) {
    println "No job xml files found in ${jobsDir.path}"
    return
}

jobFiles.each { file ->
    def jobName = file.name - ".xml"
    println "=== Applying ${jobName} ==="
    def response = JenkinsJobApi.applyJob(jenkinsConn, jobName, file)
    if (response.statusCode() != 200) {
        println "Failed to apply ${jobName}, status ${response.statusCode()}: ${response.body().take(300)}"
        System.exit(1)
    }
    println "Applied ${jobName} (status ${response.statusCode()})"
}

println "Done."
