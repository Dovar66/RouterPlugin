package com.dovar.plugin

import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import javassist.ClassPool
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import org.gradle.api.Project

class RouterTransform extends Transform {

    private Project project

    private StubServiceMatchInjector stubServiceMatchInjector

    private StubServiceGenerator serviceGenerator

    RouterTransform(Project project, StubServiceGenerator serviceGenerator) {
        this.project = project
        this.serviceGenerator = serviceGenerator
    }

    @Override
    String getName() {
        return RouterTransform.simpleName
    }

    //限定输入类型
    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    //限定输入文件所属的范围
    @Override
    Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    //是否支持增量编译
    @Override
    boolean isIncremental() {
        return false
    }

    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        //step1:将所有类的路径加入到ClassPool中
        ClassPool classPool = new ClassPool()
        project.android.bootClasspath.each {
            classPool.appendClassPath((String) it.absolutePath)
        }

        //TODO 这里有优化的空间,实际上只要将我们需要的类加进去即可
        ClassAppender.appendAllClasses(transformInvocation.getInputs(), classPool)

        this.stubServiceMatchInjector = new StubServiceMatchInjector(classPool, serviceGenerator, project.rootDir.absolutePath)
        //第一次遍历只找目标文件
        transformInvocation.inputs.each { TransformInput input ->
            input.directoryInputs.each { DirectoryInput directoryInput ->
                stubServiceMatchInjector.lookupDirectory(directoryInput.file)
            }

            input.jarInputs.each { JarInput jarInput ->
                stubServiceMatchInjector.lookupJar(jarInput)
            }
        }
        //第二次遍历时注入代码，并将input输出到dest
        transformInvocation.inputs.each { TransformInput input ->
            //遍历文件夹
            input.directoryInputs.each { DirectoryInput directoryInput ->
                //获取output目录
                def dest = transformInvocation.outputProvider.getContentLocation(directoryInput.name,
                        directoryInput.contentTypes, directoryInput.scopes, Format.DIRECTORY)

                //将input的目录复制到output指定目录
                FileUtils.copyDirectory(directoryInput.file, dest)
            }

            //遍历jar文件，对jar不操作，但是要输出到out路径
            input.jarInputs.each { JarInput jarInput ->
                //重命名输出文件(同目录copyFile会冲突)
                def jarName = jarInput.name

                stubServiceMatchInjector.injectMatchCode(jarInput)

                def md5Name = DigestUtils.md5Hex(jarInput.file.getAbsolutePath())
                if (jarName.endsWith(".jar")) {
                    jarName = jarName.substring(0, jarName.length() - 4)
                }
                def dest = transformInvocation.outputProvider.getContentLocation(jarName + md5Name,
                        jarInput.contentTypes, jarInput.scopes, Format.JAR)
                FileUtils.copyFile(jarInput.file, dest)
            }
        }
    }
}