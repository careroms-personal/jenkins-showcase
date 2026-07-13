import org.yaml.snakeyaml.Yaml
import conn.JenkinsConnection
import http.JenkinsApi

def apply(JenkinsConnection conn, String baseDir) {
    def yamlLoader = new Yaml()
    def client = JenkinsApi.newClient()
    def crumb = JenkinsApi.getCrumb(client, conn)

    def configsDir = new File("${baseDir}/configs")
    if (!configsDir.exists()) {
        println "No configs dir found at ${configsDir.path}"
        return
    }

    def jobFolders = []
    configsDir.eachDirRecurse { dir ->
        def metaFile = new File(dir, "meta.yaml")
        if (metaFile.exists()) {
            jobFolders << dir
        }
    }

    if (jobFolders.isEmpty()) {
        println "No job folders (with meta.yaml) found under ${configsDir.path}"
        return
    }

    println "Found ${jobFolders.size()} job definition(s)"

    jobFolders.sort { it.path }.each { jobDir ->
        def metaFile = new File(jobDir, "meta.yaml")
        def xmlFile = new File(jobDir, "job.xml")

        def meta = yamlLoader.load(metaFile.text)

        if (!meta?.name) {
            println "SKIP: ${jobDir.path} — meta.yaml missing required 'name' field"
            return
        }

        if (!xmlFile.exists()) {
            println "SKIP: ${jobDir.path} — no job.xml found"
            return
        }

        def jobName = meta.name
        def folderPath = meta.folder ?: ""
        def configXml = xmlFile.text

        println "=== ${folderPath ? folderPath + '/' : ''}${jobName} ==="

        if (folderPath && !JenkinsApi.folderExists(client, conn, folderPath)) {
            println "Creating Jenkins folder: ${folderPath}"
            def folderResp = JenkinsApi.createFolder(client, conn, crumb, folderPath)
            println "  -> ${folderResp.statusCode()}"
        }

        if (JenkinsApi.jobExistsInFolder(client, conn, folderPath, jobName)) {
            println "Updating job: ${jobName}"
            def response = JenkinsApi.updateJobInFolder(client, conn, crumb, folderPath, jobName, configXml)
            println "  -> ${response.statusCode()}"
        } else {
            println "Creating job: ${jobName}"
            def response = JenkinsApi.createJobInFolder(client, conn, crumb, folderPath, jobName, configXml)
            println "  -> ${response.statusCode()}"
        }
    }
}