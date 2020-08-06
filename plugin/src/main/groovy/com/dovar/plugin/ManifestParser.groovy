package com.dovar.plugin

import org.gradle.api.GradleException
import org.gradle.api.Project

import java.util.regex.Pattern

class ManifestParser {

    Set<String> getCustomProcessNames(File manifestPath) {

        Set<String> processSet = new HashSet<>()

        ComponentReader reader = new ComponentReader(manifestPath)
        reader.readActivities(processSet)
        reader.readServices(processSet)
        reader.readBroadcastReceivers(processSet)
        reader.readProviders(processSet)
        return processSet
    }

    /**
     * 获取 AndroidManifest.xml 路径
     */
    def static getManifestPath(Project project, String variantDir) {
        // Compatible with path separators for window and Linux, and fit split param based on 'Pattern.quote'
        def variantDirArray = variantDir.split(Pattern.quote(File.separator))
        String variantName = ""
        variantDirArray.each {
            //首字母大写进行拼接
            variantName += it.capitalize()
        }
        //获取processManifestTask
        def processManifestTask = project.tasks.getByName("process${variantName}Manifest")

        //如果processManifestTask存在的话
        //transform的task目前能保证在processManifestTask之后执行
        if (processManifestTask) {
            File result = null
            //正常的manifest
            File manifestOutputFile = null
            //instant run的manifest
            File instantRunManifestOutputFile = null
            try {
                manifestOutputFile = processManifestTask.getManifestOutputFile()
                instantRunManifestOutputFile = processManifestTask.getInstantRunManifestOutputFile()
            } catch (Exception e) {
                manifestOutputFile = new File(processManifestTask.getManifestOutputDirectory(), "AndroidManifest.xml")
                instantRunManifestOutputFile = new File(processManifestTask.getInstantRunManifestOutputDirectory(), "AndroidManifest.xml")
            }

            if (manifestOutputFile == null && instantRunManifestOutputFile == null) {
                throw new GradleException("can't get manifest file")
            }

            //先设置为正常的manifest
            result = manifestOutputFile

            try {
                //获取instant run 的Task
                def instantRunTask = project.tasks.getByName("transformClassesWithInstantRunFor${variantName}")
                //查找instant run是否存在且文件存在
                if (instantRunTask && instantRunManifestOutputFile.exists()) {
                    println ' Instant run is enabled and the manifest is exist.'
                    if (!manifestOutputFile.exists()) {
                        //因为这里只是为了读取activity，所以无论用哪个manifest差别不大
                        //正常情况下不建议用instant run的manifest，除非正常的manifest不存在
                        //只有当正常的manifest不存在时，才会去使用instant run产生的manifest
                        result = instantRunManifestOutputFile
                    }
                }
            } catch (ignored) {
                // transformClassesWithInstantRunForXXX may not exists
            }

            //最后检测文件是否存在，打印
            if (!result.exists()) {
                println ' AndroidManifest.xml not exist'
            }
            return result.absolutePath
        }

        return ""
    }
}