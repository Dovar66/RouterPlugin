package com.dovar.plugin

import com.android.build.api.transform.JarInput
import javassist.ClassPool
import javassist.CtClass
import javassist.CtMethod
import org.apache.commons.io.FileUtils

import java.util.jar.JarEntry
import java.util.jar.JarFile

class StubServiceMatchInjector {

    private static final String STUB_SERVICE_MATCHER = "com.dovar.router_api.router.cache.JavassistGenerateMethod"
    private static final String STUB_SERVICE_MATCHER_CLASS = "JavassistGenerateMethod.class"
    private static final String GET_TARGET_SERVICE = "getTargetService"
    private static final String GET_PROXY_CLASSES = "getProxyClassNames"

    private ClassPool classPool
    private String rootDirPath

    private StubServiceGenerator serviceGenerator

    private Map<String, String> matchedServices
    private boolean found = false

    StubServiceMatchInjector(ClassPool classPool, StubServiceGenerator serviceGenerator, String rootDirPath) {
        this.classPool = classPool
        this.serviceGenerator = serviceGenerator
        this.rootDirPath = rootDirPath
    }

    private void readMatchedServices(String dirPath, String fileName) {
        File dir = new File(dirPath)
        if (!dir.exists()) {
            return
        }
        File matchFile = new File(dir, fileName)
        if (!matchFile.exists()) {
            return
        }
        BufferedInputStream ism = matchFile.newInputStream()
        BufferedReader reader = new BufferedReader(new InputStreamReader(ism))
        String content
        while ((content = reader.readLine()) != null) {
            String[] matchKeyValues = content.split(",")
            if (matchKeyValues != null) {
                matchedServices.put(matchKeyValues[0], matchKeyValues[1])
            }
        }
        reader.close()
        ism.close()
    }

    void injectMatchCode(JarInput jarInput) {
        if (found) {
            return
        }

        String filePath = jarInput.file.getAbsolutePath()

        if (filePath.endsWith(".jar") && !filePath.contains("com.android.support")
                && !filePath.contains("/com/android/support")) {

            JarFile jarFile = new JarFile(jarInput.file)
            Enumeration enumeration = jarFile.entries()

            while (enumeration.hasMoreElements()) {
                JarEntry jarEntry = (JarEntry) enumeration.nextElement()
                String entryName = jarEntry.getName()

                if (entryName.endsWith(STUB_SERVICE_MATCHER_CLASS)) {
                    prepareInjectMatchCode(filePath)
                    found = true
                    break
                }
            }

        }
    }

    private void prepareInjectMatchCode(String filePath) {
        File jarFile = new File(filePath)
        String jarDir = jarFile.getParent() + File.separator + jarFile.getName().replace('.jar', '')

        //解压jar包，解压之后就是.class文件
        List<String> classNameList = JarUtils.unzipJar(filePath, jarDir)

        //删除原来的jar包
        jarFile.delete()

        //注入代码
        classPool.appendClassPath(jarDir)

        for (String className : classNameList) {
            if (className.endsWith(STUB_SERVICE_MATCHER_CLASS)) {
                doInjectMatchCode(jarDir)
                break
            }
        }

        //重新打包jar
        JarUtils.zipJar(jarDir, filePath)

        //删除目录
        FileUtils.deleteDirectory(new File(jarDir))
    }

    private void fetchServiceInfo() {
        matchedServices = serviceGenerator.getMatchServices()
        if (matchedServices == null) {
            this.matchedServices = new HashMap<>()
            readMatchedServices(rootDirPath + File.separator + StubServiceGenerator.MATCH_DIR, StubServiceGenerator.MATCH_FILE_NAME)
        }
    }

    //这个className含有.class,而实际上要获取CtClass的话只需要前面那部分
    private void doInjectMatchCode(String path) {
        //首先获取服务信息
        fetchServiceInfo()

        CtClass ctClass = classPool.getCtClass(STUB_SERVICE_MATCHER)
        if (ctClass.isFrozen()) {
            ctClass.defrost()
        }
        CtMethod[] ctMethods = ctClass.getDeclaredMethods()
        CtMethod getTargetServiceMethod = null
        CtMethod getProxyClassesMethod = null
        ctMethods.each {
            if (GET_TARGET_SERVICE.equals(it.getName())) {
                getTargetServiceMethod = it
            } else if (GET_PROXY_CLASSES.equals(it.getName())) {
                getProxyClassesMethod = it
            }
        }

        //注入getTargetService()
        StringBuilder code = new StringBuilder()
        //注意:javassist的编译器不支持泛型
        code.append("{\njava.util.Map matchedServices=new java.util.HashMap();\n")
        matchedServices.each {
            code.append("matchedServices.put(\"" + it.getKey() + "\"," + it.getValue() + ".class);\n")
        }
        code.append('return matchedServices;\n}')
        getTargetServiceMethod.insertBefore(code.toString())

        //注入getProxyClassNames()
        code = new StringBuilder()
        //注意:javassist的编译器不支持泛型
        code.append("{\njava.util.ArrayList list = new java.util.ArrayList();\n")
        generateClasses.each {
            code.append("list.add(\"" + it + "\");\n")
        }
        code.append('return list;\n}')
        getProxyClassesMethod.insertBefore(code.toString())

        ctClass.writeFile(path)
    }

    private ArrayList<String> generateClasses = new ArrayList<>()
    private static final String TAG_PROXY = 'com.dovar.router.generate.RouterInitProxy$$'

    void lookupDirectory(File file) {
        if (file.isDirectory()) {
            file.listFiles().each { f ->
                lookupDirectory(f)
            }
        } else {
            String filePath = file.getAbsolutePath().replace(File.separator, '.')
            //匹配包名与部分文件名
            if (filePath.contains(TAG_PROXY)) {
                int index = filePath.lastIndexOf(TAG_PROXY)
                String entryName = filePath.substring(index)
//                System.out.println("[INFO] " + entryName)
                generateClasses.add(entryName.replace('.class',''))
            }
        }
    }

    void lookupJar(JarInput jarInput) {
        String filePath = jarInput.file.getAbsolutePath()

        if (filePath.endsWith(".jar") && !filePath.contains("com.android.support")
                && !filePath.contains("/com/android/support")) {

            JarFile jarFile = new JarFile(jarInput.file)
            Enumeration enumeration = jarFile.entries()

            while (enumeration.hasMoreElements()) {
                JarEntry jarEntry = (JarEntry) enumeration.nextElement()
                String entryName = jarEntry.getName().replace('/', '.')
                //匹配包名与部分文件名
                if (entryName.contains(TAG_PROXY)) {
//                    System.out.println("[INFO] " + entryName)
                    generateClasses.add(entryName.replace('.class',''))
                }
            }
        }
    }
}