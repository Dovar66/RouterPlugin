package com.dovar.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import com.android.build.gradle.AppExtension

class RouterPlugin implements Plugin<Project> {

    private StubServiceGenerator stubServiceGenerator = new StubServiceGenerator()

    @Override
    void apply(Project project) {
        //创建扩展属性 RouterPluginConfig，并将外部属性配置使用 RouterExtention 进行管理
        //创建后可以在build.gradle中使用 RouterPluginConfig 配置属性
        project.extensions.create("RouterPluginConfig", RouterExtention)
        RouterExtention re = project.RouterPluginConfig
        if (re.supportMultiProcess) {
            stubServiceGenerator.injectStubServiceToManifest(project)
        }

        def android = project.extensions.findByType(AppExtension)
        android.registerTransform(new RouterTransform(project, stubServiceGenerator))
        println("================apply router plugin==========")
    }
}