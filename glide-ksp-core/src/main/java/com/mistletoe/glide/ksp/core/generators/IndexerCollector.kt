package com.mistletoe.glide.ksp.core.generators

import com.google.devtools.ksp.symbol.KSClassDeclaration

/**
 * @brief
 * @author mistletoe
 * @date 2022/3/8
 **/
object IndexerCollector {
    val modules = mutableSetOf<KSClassDeclaration>()
    val extensions = mutableSetOf<KSClassDeclaration>()
}