package com.rockwellits

import com.android.build.api.transform.*
import com.android.build.gradle.BaseExtension
import javassist.ClassPool
import javassist.CtNewMethod
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import java.io.FileOutputStream
import java.nio.file.Paths
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

class PatchFlutterAccessibilityTransform(private val androidJarPath: String,
                                         private val logger: Logger) : Transform() {

    override fun getName(): String {
        return "PatchFlutterAccessibility"
    }

    override fun getInputTypes(): Set<QualifiedContent.ContentType> {
        return Collections.singleton(QualifiedContent.DefaultContentType.CLASSES)
    }

    override fun getScopes(): MutableSet<in QualifiedContent.Scope>? {
        return Collections.singleton(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
    }

    override fun isIncremental(): Boolean {
        return false
    }

    override fun transform(transformInvocation: TransformInvocation) {
        super.transform(transformInvocation)

        transformInvocation.outputProvider.deleteAll()

        transformInvocation.inputs.forEach { transformInput ->
            transformInput.jarInputs.forEach { jarInput ->
                val jarName = jarInput.name
                val file = jarInput.file
                val dest = transformInvocation.outputProvider.getContentLocation(jarName,
                        jarInput.contentTypes, jarInput.scopes, Format.JAR);
                val status = jarInput.status

                if (status == Status.REMOVED) {
                    logger.info("Remove $file")
                    FileUtils.deleteQuietly(dest)
                } else if (!isIncremental || status != Status.NOTCHANGED) {
                    if (jarName.startsWith("io.flutter:flutter_embedding")) {
                        logger.info("Patching $file")

                        val classPool = ClassPool()
                        classPool.insertClassPath(file.toString())
                        classPool.insertClassPath(androidJarPath)
                        classPool.importPackage("android.os.Build")
                        classPool.importPackage("android.util.Log")
                        classPool.importPackage("android.view.accessibility.AccessibilityNodeInfo")
                        classPool.importPackage("java.lang.reflect.Field")

                        logger.info("Replacing AccessibilityBridge#createAccessibilityNodeInfo")

                        val ctClass = classPool.get("io.flutter.view.AccessibilityBridge")
                        val ctMethod = ctClass.getDeclaredMethod("createAccessibilityNodeInfo")
                        ctMethod.name = "createAccessibilityNodeInfo_original"

                        ctClass.addMethod(CtNewMethod.make("""
                            public AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualViewId) {
                                final AccessibilityNodeInfo result = createAccessibilityNodeInfo_original(virtualViewId);
                            
                                if (Build.MANUFACTURER.contains("RealWear")) {
                                    if (flutterSemanticsTree.containsKey(Integer.valueOf(virtualViewId))) {
                                        final Object semanticsNode = flutterSemanticsTree.get(Integer.valueOf(virtualViewId));
                                        
                                        if (semanticsNode != null) {
                                            final Field valueField = semanticsNode.getClass().getDeclaredField("value");
                                            valueField.setAccessible(true);
                                            
                                            final CharSequence value = (CharSequence) valueField.get(semanticsNode);
                                            
                                            if (value != null && value.length() > 0) {
                                                Log.d("AccessibilityBridge", "Semantics value injection: " + value.toString());
                                                result.setContentDescription(value);
                                            }
                                        }
                                    }
                                }
                            
                                return result;
                            }
                            """.trimIndent(), ctClass))

                        logger.info("Generating bytecode for AccessibilityBridge")

                        val byteCode = ctClass.toBytecode()
                        val input = JarFile(file)
                        val jarOutputStream = JarOutputStream(FileOutputStream(dest))

                        input.entries().iterator().forEach { jarEntry ->
                            if (jarEntry.name != "io/flutter/view/AccessibilityBridge.class") {
                                logger.info("Writing back ${jarEntry.name} to JAR")

                                val s = input.getInputStream(jarEntry)
                                jarOutputStream.putNextEntry(JarEntry(jarEntry.name))
                                IOUtils.copy(s, jarOutputStream)
                                s.close()
                            }
                        }

                        logger.info("Writing back modified class to JAR")

                        jarOutputStream.putNextEntry(JarEntry("io/flutter/view/AccessibilityBridge.class"))
                        jarOutputStream.write(byteCode)
                        jarOutputStream.close()
                    } else {
                        logger.info("Copy $file without modifications")
                        FileUtils.copyFile(file, dest)
                    }
                }
            }
        }
    }
}

open class PatchFlutterAccessibilityPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val android = project.extensions.findByType(BaseExtension::class.java)
                ?: throw GradleException("Not an Android project")

        val androidJarPath = Paths.get(android.sdkDirectory.absolutePath, "platforms",
                android.compileSdkVersion, "android.jar").toString()

        android.registerTransform(PatchFlutterAccessibilityTransform(androidJarPath, project.logger))
    }
}