package com.mistletoe.glide.ksp.core.generators

import com.bumptech.glide.annotation.GlideOption
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.mistletoe.glide.ksp.core.ProcessorUtil
import com.mistletoe.glide.ksp.core.toClassName
import com.squareup.kotlinpoet.*

/**
 * @brief
 * @author mistletoe
 * @date 2022/3/1
 **/
/**
 * Generates method overrides for classes that want to mix in [GlideOption] annotated methods
 * in Glide extensions.
 */
internal class RequestOptionsExtensionGenerator(
    private val containingClassName: ClassName,
    private val processorUtil: ProcessorUtil,
) {
    /**
     * Returns the set of [GlideOption] annotated methods in the classes that correspond to the
     * given extension class names.
     */
    fun getRequestOptionExtensionMethods(glideExtensionClassNames: Set<String>): List<KSFunctionDeclaration> {
        return processorUtil.findAnnotatedElementsInClasses(
            glideExtensionClassNames, GlideOption::class.java)
    }

    /**
     * Returns a list containing an override [MethodSpec] for all [GlideOption] annotated
     * methods in the classes that correspond to the given extension class names.
     */
    fun generateInstanceMethodsForExtensions(glideExtensionClassNames: Set<String>): List<FunSpec> {
        val requestOptionExtensionMethods =
            getRequestOptionExtensionMethods(glideExtensionClassNames)
        val result = mutableListOf<FunSpec>()
        for (requestOptionsExtensionMethod in requestOptionExtensionMethods) {
            result.add(generateMethodsForRequestOptionsExtension(requestOptionsExtensionMethod))
        }
        return result
    }

    private fun generateMethodsForRequestOptionsExtension(element: KSFunctionDeclaration): FunSpec {
        // Assert for legacy versions
        require(element.returnType?.resolve()?.toClassName() != Unit::class.asClassName()) {
            ("The "
                    + element.simpleName.asString()
                    + " method annotated with @GlideOption in the "
                    + element.parentDeclaration?.simpleName?.asString()
                    + " @GlideExtension is using a legacy"
                    + " format that is no longer supported. Please change your method definition so that"
                    + " your @GlideModule annotated methods return BaseRequestOptions<?> objects instead"
                    + " of null.")
        }
        val overrideType = processorUtil.getOverrideType(element)
        val methodName = element.simpleName.asString()

        val builder = FunSpec.builder(methodName)
            .addKdoc(processorUtil.generateSeeMethodJavadoc(element))
            .returns(containingClassName)
            .addAnnotation(
                AnnotationSpec.builder(SuppressWarnings::class.java)
                    .addMember("value = %S", "unchecked")
                    .build())

        // The 0th element is expected to be a RequestOptions object.
        val paramElements = element.parameters.subList(1, element.parameters.size)
        val parameters = processorUtil.getParameters(paramElements)
        builder.addParameters(parameters)
        val extensionRequestOptionsArgument: String
        if (overrideType == GlideOption.OVERRIDE_EXTEND) {
            builder
                .addKdoc(
                    processorUtil.generateSeeMethodJavadoc(
                        containingClassName, methodName, paramElements.map { it.type.resolve() }))
                .addAnnotation(Override::class.java)
            val methodArgs: MutableList<Any> = ArrayList()
            methodArgs.add(element.simpleName.asString())
            var methodLiterals = StringBuilder()
            if (parameters.isNotEmpty()) {
                for (parameter in parameters) {
                    methodLiterals.append("%L, ")
                    methodArgs.add(parameter.name)
                }
                methodLiterals =
                    StringBuilder(methodLiterals.substring(0, methodLiterals.length - 2))
            }
            extensionRequestOptionsArgument = CodeBlock.builder()
                .add("super.%N($methodLiterals)", *methodArgs.toTypedArray())
                .build()
                .toString()
        } else {
            extensionRequestOptionsArgument = "this"
        }
        val args: MutableList<Any> = ArrayList()
        var code = StringBuilder("return (%T) %T.%L(%L, ")
        args.add(containingClassName)
        args.add(element.parentDeclaration?.qualifiedName?.asString()?.toClassName()!!)
        args.add(element.simpleName.asString())
        args.add(extensionRequestOptionsArgument)
        if (parameters.isNotEmpty()) {
            for (parameter in parameters) {
                code.append("%L, ")
                args.add(parameter.name)
            }
        }
        code = StringBuilder(code.substring(0, code.length - 2))
        code.append(")")
        builder.addStatement(code.toString(), *args.toTypedArray())
        builder.addAnnotation(processorUtil.checkResult()).addAnnotation(processorUtil.nonNull())
        builder.addTypeVariables(element.typeParameters.map { TypeVariableName(it.simpleName.asString()) })
        return builder.build()
    }
}