import org.yaml.snakeyaml.Yaml
import conn.JenkinsConnection

def yaml = new Yaml()
def config = yaml.load(new File('config.yaml').text)
def jenkinsConn = JenkinsConnection.fromYaml(config)

def shell = new GroovyShell(this.class.classLoader)

config.configDirs.each { dirName ->
    def applyFile = new File("${dirName}/apply.groovy")

    if (!applyFile.exists()) {
        println "No apply.groovy found in ${dirName}, skipping."
        return
    }

    println "=== Running apply.groovy for ${dirName} ==="
    def script = shell.parse(applyFile)
    script.invokeMethod('apply', [jenkinsConn, dirName] as Object[])
}

println "Done."