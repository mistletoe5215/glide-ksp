package com.mistletoe.glide.ksp.core.processors

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.mistletoe.glide.ksp.core.GLIDE_MODULES_KEY
import com.mistletoe.glide.ksp.core.GLIDE_MODULES_KSP_PACKAGE_NAME
import com.mistletoe.glide.ksp.core.ProcessorUtil
import com.mistletoe.glide.ksp.core.generators.IndexerCollector
import com.mistletoe.glide.ksp.core.toClassName

/**
 * @brief
 * @author mistletoe
 * @date 2022/3/2
 **/
internal class LibraryModuleProcessor(
    private val processorUtil: ProcessorUtil,
) {
    private val libraryGlideModules = mutableListOf<KSClassDeclaration>()

    @OptIn(KspExperimental::class)
    fun processModules() {
        // Order matters here, if we find an Indexer below, we return before writing the root module.
        // If we fail to add to appModules before then, we might accidentally skip a valid RootModule.

        //put previous open source module's LibraryGlideModule Impl classes in kotlin args, split by "|" 
        val elements = processorUtil.environment.options[GLIDE_MODULES_KEY]?.split("|")?.map {
            processorUtil.resolver.getClassDeclarationByName(processorUtil.resolver.getKSNameFromString(
                it))!!
        }?.toMutableList()

        //when ur building a new glide library,try to create a directory named "com.bumptech.glide.ksp",then put  module's LibraryGlideModule Impl class under this directory
        // it can be found and registered automatically
        val newKspGlideModules =
            processorUtil.resolver.getDeclarationsFromPackage(GLIDE_MODULES_KSP_PACKAGE_NAME)
                .filterIsInstance<KSClassDeclaration>()
        elements?.addAll(newKspGlideModules)
        if (elements != null) {
            for (element in elements) {
                // Root elements are added separately and must be checked separately because they're sub
                // classes of LibraryGlideModules.
                if (processorUtil.isAppGlideModule(element)) {
                    continue
                } else if (!processorUtil.isLibraryGlideModule(element)) {
                    throw IllegalStateException(
                        "@GlideModule can only be applied to LibraryGlideModule"
                                + " and AppGlideModule implementations, not: "
                                + element)
                }
                libraryGlideModules.add(element)
            }
        }
        processorUtil.debugLog("got child modules: $libraryGlideModules")
    }

    fun maybeWriteLibraryModule(): Boolean {
        // If I write an Indexer in a round in the target package, then try to find all classes in
        // the target package, my newly written Indexer won't be found. Since we wrote a class with
        // an Annotation handled by this processor, we know we will be called again in the next round
        // and we can safely wait to write our AppGlideModule until then.
        if (libraryGlideModules.isEmpty()) {
            return false
        }
        IndexerCollector.modules.addAll(libraryGlideModules)
        return true
    }
}