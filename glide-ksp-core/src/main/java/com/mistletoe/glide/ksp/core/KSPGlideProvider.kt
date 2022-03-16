package com.mistletoe.glide.ksp.core

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * @brief Glide ksp provider
 * @author mistletoe
 * @date 2022/2/25
 **/
class KSPGlideProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return KSPGlideProcessor(environment)
    }
}