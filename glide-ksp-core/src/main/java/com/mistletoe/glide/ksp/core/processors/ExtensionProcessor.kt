package com.mistletoe.glide.ksp.core.processors

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.mistletoe.glide.ksp.core.GLIDE_EXTENSIONS_KEY
import com.mistletoe.glide.ksp.core.ProcessorUtil
import com.mistletoe.glide.ksp.core.generators.IndexerCollector

/**
 * @brief
 * @author mistletoe
 * @date 2022/3/2
 **/
internal class ExtensionProcessor(
    private val processorUtil: ProcessorUtil
) {
    private val extensionValidator by lazy { GlideExtensionValidator(processorUtil) }
    private val libraryExtensions = mutableListOf<KSClassDeclaration>()
    fun processExtensions() {
        val elements = processorUtil.environment.options[GLIDE_EXTENSIONS_KEY]?.split("|")?.map {
            processorUtil.resolver.getClassDeclarationByName(processorUtil.resolver.getKSNameFromString(
                it))!!
        }?.toList()
        if (elements != null) {
            for (typeElement in elements) {
                extensionValidator.validateExtension(typeElement)
                processorUtil.debugLog("Processing elements: " + typeElement.declarations)
                libraryExtensions.add(typeElement)
            }
        }
    }
    fun maybeWriteLibraryExtensions(): Boolean{
        if (libraryExtensions.isEmpty()) {
            return false
        }
        IndexerCollector.extensions.addAll(libraryExtensions)
        return true
    }
}