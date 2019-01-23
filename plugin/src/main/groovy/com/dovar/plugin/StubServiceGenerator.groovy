package com.dovar.plugin

import com.android.build.gradle.AppExtension
import groovy.xml.MarkupBuilder
import org.gradle.api.Project

class StubServiceGenerator {

    def static final NAME = 'android:name'
    def static final PROCESS = 'android:process'
    def static final EXPORTED = 'android:exported'
    def static final ENABLED = 'android:enabled'
    def static final FALSE = 'false'
    def static final TRUE = 'true'
    def static
    final STUB_SERVICE = 'com.dovar.router_api.multiprocess.ConnectMultiRouterService$ConnectMultiRouterService'

    def public static final MATCH_DIR = "build"
    def public static final MATCH_FILE_NAME = "match_stub.txt"

    private Map<String, String> matchedServices

    private String rootDirPath

    void injectStubServiceToManifest(Project project) {

        rootDirPath = project.rootDir.absolutePath

        def android = project.extensions.getByType(AppExtension)

        project.afterEvaluate {
            android.applicationVariants.all { variant ->
                variant.outputs.each { output ->

                    output.processManifest.doLast {
                        //output.getProcessManifest().manifestOutputDirectory
                        output.processManifest.outputs.files.each { File file ->
                            //在gradle plugin 3.0.0之前，file是文件，且文件名为AndroidManifest.xml
                            //在gradle plugin 3.0.0之后，file是目录，且不包含AndroidManifest.xml，需要自己拼接
                            //除了目录和AndroidManifest.xml之外，还可能会包含manifest-merger-debug-report.txt等不相干的文件，过滤它
                            if ((file.name.equalsIgnoreCase("AndroidManifest.xml") && !file.isDirectory()) || file.isDirectory()) {
                                if (file.isDirectory()) {
                                    //3.0.0之后，自己拼接AndroidManifest.xml
                                    injectManifestFile(new File(file, "AndroidManifest.xml"))
                                } else {
                                    //3.0.0之前，直接使用
                                    injectManifestFile(file)
                                }
                            }
                        }
                    }

                }
            }
        }
    }

    private void injectManifestFile(File manifestFile) {
        //检测文件是否存在
        if (manifestFile != null && manifestFile.exists()) {

            String serviceManifest = addServiceItem(manifestFile.absolutePath)

            writeStubService2File(rootDirPath + File.separator + MATCH_DIR, MATCH_FILE_NAME)

            String newManifestContent = manifestFile.getText("UTF-8")
            int index = newManifestContent.lastIndexOf("</application>")
            newManifestContent = newManifestContent.substring(0, index) + serviceManifest + newManifestContent.substring(index)
            manifestFile.write(newManifestContent, 'UTF-8')

        } else {
            println "Attention!manifest file may not exist!"
        }
    }

    Map<String, String> getMatchServices() {
        return matchedServices
    }
    //注意:闭包中只能调用static方法
    private String addServiceItem(String manifestPath) {
        ManifestParser manifestParser = new ManifestParser()
        Set<String> customProcessNames = manifestParser.getCustomProcessNames(manifestPath)

        def writer = new StringWriter()
        def xml = new MarkupBuilder(writer)

        xml.application {

            int index = 0
            customProcessNames.each {

                String serviceName = "${STUB_SERVICE}" + index.toString()

                service("${NAME}": serviceName,
                        "${ENABLED}": "${TRUE}",
                        "${EXPORTED}": "${FALSE}",
                        "${PROCESS}": it
                )

                if (matchedServices == null) {
                    matchedServices = new HashMap<>()
                }
                matchedServices.put(it, serviceName)

                ++index
            }
        }

        // 删除 application 标签
        def normalStr = writer.toString().replace("<application>", "").replace("</application>", "")

        return normalStr
    }

    private void writeStubService2File(String dirPath, String fileName) {
        File dir = new File(dirPath)
        if (!dir.exists()) {
            dir.mkdirs()
        }

        File file = new File(dir, fileName)
        if (file.exists()) {
            file.delete()
        }
        file.createNewFile()

        BufferedOutputStream osm = file.newOutputStream()
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(osm))
        matchedServices.each {
            writer.writeLine(it.getKey() + "," + it.getValue())
        }
        writer.close()
        osm.close()
    }

}