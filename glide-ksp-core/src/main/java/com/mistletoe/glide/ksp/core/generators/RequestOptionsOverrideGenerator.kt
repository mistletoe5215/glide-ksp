package com.mistletoe.glide.ksp.core.generators

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Variance
import com.mistletoe.glide.ksp.core.generators.RequestOptionsGenerator.Companion.BASE_REQUEST_OPTIONS_QUALIFIED_NAME
import com.mistletoe.glide.ksp.core.toClassName
import com.mistletoe.glide.ksp.core.JET_BRAINS_DEPRECATED
import com.mistletoe.glide.ksp.core.ProcessorUtil
import com.squareup.kotlinpoet.*
import org.jetbrains.kotlin.com.google.common.base.Joiner
import org.jetbrains.kotlin.com.google.common.collect.FluentIterable

/**
 * @brief Generates overrides for BaseRequestOptions methods so that subclasses' methods return the
 * subclass type, not just BaseRequestOptions.
 * @author mistletoe
 * @date 2022/3/2
 **/
internal class RequestOptionsOverrideGenerator(private val processorUtil: ProcessorUtil) {
    private val baseRequestOptionsType by lazy {
        processorUtil.resolver.getClassDeclarationByName(processorUtil.resolver.getKSNameFromString(
            BASE_REQUEST_OPTIONS_QUALIFIED_NAME))!!
    }

    fun generateInstanceMethodOverridesForRequestOptions(typeToOverrideIn: TypeName): List<FunSpec> {
        return generateInstanceMethodOverridesForRequestOptions(
            typeToOverrideIn, emptySet())
    }

    private fun generateInstanceMethodOverridesForRequestOptions(
        typeToOverrideIn: TypeName, excludedMethods: Set<String>,
    ): List<FunSpec> {
        val exMethods = mutableSetOf<String>()
        exMethods.addAll(excludedMethods)
        exMethods.add("clone")
        return processorUtil.findInstanceMethodsReturning(
            baseRequestOptionsType, baseRequestOptionsType.asType(emptyList())).filter {
            !exMethods.contains(it.simpleName.asString())
        }.map {
            generateRequestOptionOverride(typeToOverrideIn, it)
        }
    }

    private fun generateRequestOptionOverride(
        typeToOverrideIn: TypeName, methodToOverride: KSFunctionDeclaration,
    ): FunSpec {
        val result = processorUtil.overriding(methodToOverride).returns(typeToOverrideIn)
        val parametersStr = FluentIterable.from(methodToOverride.parameters).transform {
            it?.name?.asString()
        }.join(Joiner.on(", "))
        val isTransformMethod = methodToOverride.simpleName.asString().contains("transform")
                && methodToOverride.parameters.any { it.isVararg }
        if (isTransformMethod) {
            result
                .addAnnotation(SafeVarargs::class)
                .addAnnotation(
                    AnnotationSpec.builder(SuppressWarnings::class)
                        .addMember("%S", "varargs")
                        .build())
        }
        result.addCode(
            CodeBlock.builder()
                .add(
                    "return super.%N(${if (isTransformMethod) Variance.STAR.label else ""}",
                    methodToOverride.simpleName.asString(),
                )
                .add(parametersStr)
                .add(") as %T \n", typeToOverrideIn)
                .build())
        for (annotation in methodToOverride.annotations) {
            val deprecatedTypeQualifiedName = processorUtil.kotlinDeprecated().reflectionName()
            val annotationQualifiedName =
                annotation.annotationType.resolve().toClassName().reflectionName()
            if (annotationQualifiedName == deprecatedTypeQualifiedName) {
                result.addAnnotation(
                    AnnotationSpec.builder(
                        JET_BRAINS_DEPRECATED)
                        .addMember("%S", "this method is deprecated")
                        .build())
            } else {
                result.addAnnotation(AnnotationSpec.builder(annotation.annotationType.resolve()
                    .toClassName()).build())
            }
        }
        return result.build()
    }
}