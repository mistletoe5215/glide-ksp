package com.mistletoe.glide.ksp.core

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.mistletoe.glide.ksp.core.processors.AppModuleProcessor
import com.mistletoe.glide.ksp.core.processors.ExtensionProcessor
import com.mistletoe.glide.ksp.core.processors.LibraryModuleProcessor

/**
 * @brief Glide ksp
 * @author mistletoe
 * @date 2022/2/25
 **/
class KSPGlideProcessor(private val environment: SymbolProcessorEnvironment) : SymbolProcessor {
    companion object {
        const val DEBUG = false
    }

    private var isSolved: Boolean = false
    private val isCompatible by lazy { environment.kotlinVersion.isAtLeast(1, 6, 10) }
    private var processorUtil: ProcessorUtil? = null
    private var libraryModuleProcessor: LibraryModuleProcessor? = null
    private var appModuleProcessor: AppModuleProcessor? = null
    private var extensionProcessor: ExtensionProcessor? = null
    override fun process(resolver: Resolver): List<KSAnnotated> {
        require(isCompatible) {
            "current module's kotlin version must be at least 1.6.10"
        }
        //codes in init block
        if (processorUtil == null) {
            processorUtil = ProcessorUtil(environment, resolver)
        }
        if (libraryModuleProcessor == null) {
            libraryModuleProcessor = LibraryModuleProcessor(processorUtil!!)
        }
        if (appModuleProcessor == null) {
            appModuleProcessor = AppModuleProcessor(processorUtil!!)
        }
        if (extensionProcessor == null) {
            extensionProcessor = ExtensionProcessor(processorUtil!!)
        }
        processorUtil?.infoLog("KSPGlideProcessor begin process ...")
        processorUtil!!.process()
        if (!isSolved) {
            libraryModuleProcessor!!.processModules()
            extensionProcessor!!.processExtensions()
            appModuleProcessor!!.processModules()
            isSolved = true
        } else {
            processorUtil?.infoLog("KSPGlideProcessor skp this round ...")
        }
        return emptyList()
    }

    override fun finish() {
        libraryModuleProcessor!!.maybeWriteLibraryModule()
        extensionProcessor!!.maybeWriteLibraryExtensions()
        appModuleProcessor!!.maybeWriteAppModule()
        processorUtil?.infoLog("KSPGlideProcessor finished ...")
    }
}