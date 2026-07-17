import org.yaml.snakeyaml.Yaml
import conn.JenkinsConnection
import apis.JenkinsJobApi

if (args.length < 1) {
    println "Usage: groovy backup-job-config.groovy <server-config.yaml>"
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

def jobNames = JenkinsJobApi.listJobNames(jenkinsConn)
if (!jobNames) {
    println "No jobs found on ${jenkinsConn.url}"
    return
}

def jobsDir = new File("jobs")
jobNames.each { jobName ->
    def destFile = new File(jobsDir, "${jobName}.xml")
    def response = JenkinsJobApi.pullJob(jenkinsConn, jobName, destFile)
    if (response.statusCode() != 200) {
        println "Failed to pull ${jobName}, status ${response.statusCode()}: ${response.body().take(300)}"
        System.exit(1)
    }
    println "Pulled ${jobName} -> ${destFile.path}"
}

println "Done."
