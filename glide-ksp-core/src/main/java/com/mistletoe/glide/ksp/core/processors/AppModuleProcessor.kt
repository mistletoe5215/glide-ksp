package com.mistletoe.glide.ksp.core.processors

import com.bumptech.glide.annotation.GlideModule
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.mistletoe.glide.ksp.core.ProcessorUtil
import com.mistletoe.glide.ksp.core.getAnnotationsByType
import com.mistletoe.glide.ksp.core.toClassName
import com.mistletoe.glide.ksp.core.generators.*
import com.mistletoe.glide.ksp.core.generators.AppModuleGenerator
import com.mistletoe.glide.ksp.core.generators.GlideGenerator
import com.mistletoe.glide.ksp.core.generators.RequestBuilderGenerator
import com.mistletoe.glide.ksp.core.generators.RequestManagerFactoryGenerator
import com.squareup.kotlinpoet.TypeSpec

/**
 * @brief Runs the final steps of Glide's annotation process and generates the combined `AppGlideModule`, `com.bumptech.glide.Glide`, `com.bumptech.glide.RequestManager`, and
 * `com.bumptech.glide.request.RequestOptions` classes.
 * @author mistletoe
 * @date 2022/3/2
 **/
internal class AppModuleProcessor(private val processorUtil: ProcessorUtil) {
    private val appGlideModules = mutableListOf<KSClassDeclaration>()
    private val requestOptionsGenerator by lazy { RequestOptionsGenerator(processorUtil) }
    private val requestManagerGenerator by lazy { RequestManagerGenerator(processorUtil) }
    private val appModuleGenerator by lazy { AppModuleGenerator(processorUtil) }
    private val requestBuilderGenerator by lazy { RequestBuilderGenerator(processorUtil) }
    private val requestManagerFactoryGenerator by lazy {
        RequestManagerFactoryGenerator(processorUtil)
    }
    private val glideGenerator by lazy { GlideGenerator(processorUtil) }
    fun processModules() {
        val allGlideModuleClazz =
            processorUtil.resolver.getSymbolsWithAnnotation(GlideModule::class.java.name)
                .filter { it is KSClassDeclaration }.map { it as KSClassDeclaration }.toList()
        for (element in allGlideModuleClazz) {
            if (processorUtil.isAppGlideModule(element)) {
                appGlideModules.add(element)
            }
        }
        check(appGlideModules.size <= 1) { "You cannot have more than one AppGlideModule, found: $appGlideModules" }
    }

    fun maybeWriteAppModule(): Boolean {
        // appGlideModules is added to in order to catch errors where multiple AppGlideModules may be
        // present for a single application or library. Because we only add to appGlideModules, we use
        // isGeneratedAppGlideModuleWritten to make sure the GeneratedAppGlideModule is written at
        // most once.
        if (appGlideModules.isEmpty()) {
            return false
        }
        val appModule = appGlideModules[0]
        // If this package is null, it means there are no classes with this package name. One way this
        // could happen is if we process an annotation and reach this point without writing something
        // to the package. We do not error check here because that shouldn't happen with the
        // current implementation.
        val indexedClassNames = getIndexedClassNames()
        // Write all generated code to the package containing the AppGlideModule. Doing so fixes
        // classpath collisions if more than one Application containing a AppGlideModule is included
        // in a project.
        val generatedCodePackageName = appModule.packageName.asString()
        val generatedRequestOptions =
            requestOptionsGenerator.generate(generatedCodePackageName, indexedClassNames.extensions)
        writeRequestOptions(generatedCodePackageName, generatedRequestOptions)
        val generatedRequestBuilder = requestBuilderGenerator.generate(generatedCodePackageName,
            indexedClassNames.extensions,
            generatedRequestOptions)
        writeRequestBuilder(generatedCodePackageName, generatedRequestBuilder)
        val requestManager = requestManagerGenerator.generate(
            generatedCodePackageName,
            generatedRequestOptions,
            generatedRequestBuilder,
            indexedClassNames.extensions)
        writeRequestManager(generatedCodePackageName, requestManager)
        val requestManagerFactory =
            requestManagerFactoryGenerator.generate(generatedCodePackageName, requestManager)
        writeRequestManagerFactory(requestManagerFactory)
        val glide = glideGenerator.generate(generatedCodePackageName,
            getGlideName(appModule),
            requestManager)
        writeGlide(generatedCodePackageName, glide)
        val generatedAppGlideModule =
            appModuleGenerator.generate(appModule, indexedClassNames.glideModules)
        writeAppModule(generatedAppGlideModule)
        processorUtil.infoLog("Wrote GeneratedAppGlideModule with: " + indexedClassNames.glideModules)
        return true
    }

    @OptIn(KspExperimental::class)
    private fun getGlideName(appModule: KSClassDeclaration): String {
        return appModule.getAnnotationsByType(GlideModule::class).firstOrNull()?.glideName ?: ""
    }

    @OptIn(KspExperimental::class)
    private fun getIndexedClassNames(): FoundIndexedClassNames {
        return FoundIndexedClassNames(IndexerCollector.modules.map {
            it.toClassName().reflectionName()
        }.toSet(), IndexerCollector.extensions.map { it.toClassName().reflectionName() }.toSet())
    }

    private fun writeGlide(packageName: String, glide: TypeSpec) {
        processorUtil.writeClass(packageName, glide)
    }

    private fun writeRequestManager(packageName: String, requestManager: TypeSpec) {
        processorUtil.writeClass(packageName, requestManager)
    }

    // We dont' care about collisions in IDEs since this class isn't an API class.
    private fun writeRequestManagerFactory(requestManagerFactory: TypeSpec) {
        processorUtil.writeClass(
            AppModuleGenerator.GENERATED_ROOT_MODULE_PACKAGE_NAME, requestManagerFactory)
    }

    // The app module we generate subclasses a package private class. We don't care about classpath
    // collisions in IDEs since this class isn't an API class.
    private fun writeAppModule(appModule: TypeSpec) {
        processorUtil.writeClass(AppModuleGenerator.GENERATED_ROOT_MODULE_PACKAGE_NAME, appModule)
    }

    private fun writeRequestOptions(packageName: String, requestOptions: TypeSpec) {
        processorUtil.writeClass(packageName, requestOptions)
    }

    private fun writeRequestBuilder(packageName: String, requestBuilder: TypeSpec) {
        processorUtil.writeClass(packageName, requestBuilder)
    }

    private class FoundIndexedClassNames(val glideModules: Set<String>, val extensions: Set<String>)
}