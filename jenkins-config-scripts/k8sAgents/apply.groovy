import org.yaml.snakeyaml.Yaml
import conn.JenkinsConnection
import http.JenkinsApi

def buildGroovyScript(String cloudName, Map template) {
    """
        import org.csanchez.jenkins.plugins.kubernetes.*
        import org.csanchez.jenkins.plugins.kubernetes.volumes.EmptyDirVolume
        import jenkins.model.Jenkins

        def cloud = Jenkins.instance.clouds.find { it.name == '${cloudName}' } as KubernetesCloud
        if (cloud == null) {
            println "Cloud '${cloudName}' not found"
            return
        }

        def templates = cloud.templates
        templates.removeIf { it.name == '${template.name}' }

        def container = new ContainerTemplate('${template.container_name}', '${template.image}')
        container.command = '${template.command}'
        container.args = '${template.args}'
        container.privileged = ${template.privileged}

        def podTemplate = new PodTemplate()
        podTemplate.name = '${template.name}'
        podTemplate.label = '${template.label}'
        podTemplate.containers = [container]

        def volume = new EmptyDirVolume('${template.mountPath}', false)
        podTemplate.volumes = [volume]

        templates.add(podTemplate)
        cloud.templates = templates
        Jenkins.instance.save()

        println "Applied template '${template.name}' to cloud '${cloudName}'"
    """.stripIndent()
}

// --- Entry point called from main.groovy ---
def apply(JenkinsConnection conn, String baseDir) {
    def yamlLoader = new Yaml()
    def client = JenkinsApi.newClient()
    def crumb = JenkinsApi.getCrumb(client, conn)

    def dir = new File(baseDir,"configs")

    dir.listFiles({ f -> f.name.endsWith('.yaml') || f.name.endsWith('.yml') } as FileFilter)
       .sort { it.name }
       .each { file ->
            println "=== Processing ${file.name} ==="
            def data = yamlLoader.load(file.text)
            def cloudName = data.cloud_name

            data.templates.each { template ->
                println "Applying template: ${template.name}"
                def script = buildGroovyScript(cloudName, template)
                def response = JenkinsApi.runScript(client, conn, crumb, script)
                println response.body()
            }
       }
}