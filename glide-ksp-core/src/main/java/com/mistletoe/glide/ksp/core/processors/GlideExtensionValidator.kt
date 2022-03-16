package com.mistletoe.glide.ksp.core.processors

import com.bumptech.glide.annotation.GlideOption
import com.bumptech.glide.annotation.GlideType
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.symbol.*
import com.mistletoe.glide.ksp.core.*
import com.mistletoe.glide.ksp.core.generators.RequestOptionsGenerator
import com.mistletoe.glide.ksp.core.toClassName

/**
 * @brief Validates that classes annotated with [com.bumptech.glide.annotation.GlideExtension]
 * contains methods with the expected format.
 *
 * Validation is performed so that errors can be found when a library is compiled. Without
 * validation, an error written in to a library wouldn't be found until Glide tried to generate code
 * for an Application.
 * @author mistletoe
 * @date 2022/3/2
 **/
internal class GlideExtensionValidator(private val processorUtil: ProcessorUtil) {
    @OptIn(KspExperimental::class)
    fun validateExtension(ksClassDeclaration: KSClassDeclaration) {
        if (!ksClassDeclaration.isPublic()) {
            throw IllegalArgumentException(
                "RequestOptionsExtensions must be public, including: " + ksClassDeclaration.simpleName.asString())
        }
        for (element in ksClassDeclaration.declarations) {
            if (element is KSFunctionDeclaration) {
                if (element.isConstructor()) {
                    validateExtensionConstructor(element)
                } else {
                    if (element.getAnnotationsByType(GlideOption::class).firstOrNull() != null) {
                        validateGlideOption(element)
                    } else if (element.getAnnotationsByType(GlideType::class)
                            .firstOrNull() != null
                    ) {
                        validateGlideType(element)
                    }
                }
            }
        }
    }

    private fun validateGlideOption(ksFunctionDeclaration: KSFunctionDeclaration) {
        validateGlideOptionAnnotations(ksFunctionDeclaration)
        validateGlideOptionParameters(ksFunctionDeclaration)
        val returnType = ksFunctionDeclaration.returnType
        if (!isBaseRequestOptions(returnType?.resolve()!!)) {
            throw IllegalArgumentException(
                "@GlideOption methods should return a"
                        + " BaseRequestOptions<?> object, but "
                        + ksFunctionDeclaration.qualifiedName?.asString()
                        + " returns "
                        + returnType
                        + ". If you're using old style @GlideOption methods, your"
                        + " method may have a void return type, but doing so is deprecated and support will"
                        + " be removed in a future version")
        }
        validateGlideOptionOverride(ksFunctionDeclaration)
    }

    private fun validateGlideOptionAnnotations(ksFunctionDeclaration: KSFunctionDeclaration) {
        validateAnnotatedNonNull(ksFunctionDeclaration)
    }

    private fun validateGlideOptionOverride(ksFunctionDeclaration: KSFunctionDeclaration) {
        val overrideType: Int = processorUtil.getOverrideType(ksFunctionDeclaration)
        val isOverridingBaseRequestOptionsMethod =
            isMethodInBaseRequestOptions(ksFunctionDeclaration)
        if (isOverridingBaseRequestOptionsMethod && overrideType == GlideOption.OVERRIDE_NONE) {
            throw IllegalArgumentException(
                "Accidentally attempting to override a method in"
                        + " BaseRequestOptions. Add an 'override' value in the @GlideOption annotation"
                        + " if this is intentional. Offending method: "
                        + ksFunctionDeclaration.qualifiedName?.asString())
        } else if (!isOverridingBaseRequestOptionsMethod && overrideType != GlideOption.OVERRIDE_NONE) {
            throw IllegalArgumentException(
                "Requested to override an existing method in"
                        + " BaseRequestOptions, but no such method was found. Offending method: "
                        + ksFunctionDeclaration.qualifiedName?.asString())
        }
    }

    private fun isMethodInBaseRequestOptions(toFind: KSFunctionDeclaration): Boolean {
        // toFind is a method in a GlideExtension whose first argument is a BaseRequestOptions<?> type.
        // Since we're comparing against methods in BaseRequestOptions itself, we need to drop that
        // first type.
        val requestOptionsType =
            processorUtil.resolver.getClassDeclarationByName(processorUtil.resolver.getKSNameFromString(
                RequestOptionsGenerator.BASE_REQUEST_OPTIONS_QUALIFIED_NAME))!!
        val toFindParameterNames = getComparableParameterNames(toFind, true)
        val toFindSimpleName = toFind.simpleName.asString()
        for (element in requestOptionsType.declarations) {
            if (element !is KSFunctionDeclaration) {
                continue
            }
            if ((toFindSimpleName == element.simpleName.asString())) {
                val parameterNamesInBase = getComparableParameterNames(element, false)
                if ((parameterNamesInBase == toFindParameterNames)) {
                    return true
                }
            }
        }
        return false
    }

    private fun validateGlideType(ksFunctionDeclaration: KSFunctionDeclaration) {
        val returnType = ksFunctionDeclaration.returnType
        validateGlideTypeAnnotations(ksFunctionDeclaration)
        if (!isRequestBuilder(returnType?.resolve()!!) || !typeMatchesExpected(returnType,
                ksFunctionDeclaration)
        ) {
            val expectedClassName = getGlideTypeValue(ksFunctionDeclaration)
            throw IllegalArgumentException(
                ("@GlideType methods should return a RequestBuilder<"
                        + expectedClassName
                        + "> object, but "
                        + ksFunctionDeclaration.qualifiedName?.asString()
                        + " returns: "
                        + returnType
                        + ". If you're using old style @GlideType methods, your"
                        + " method may have a void return type, but doing so is deprecated and support will"
                        + " be removed in a future version"))
        }
        validateGlideTypeParameters(ksFunctionDeclaration)
    }

    private fun getGlideTypeValue(ksFunctionDeclaration: KSFunctionDeclaration): String {
        return processorUtil
            .findClassValuesFromAnnotationOnClassAsNames(ksFunctionDeclaration,
                GlideType::class.java)
            .iterator()
            .next()
    }

    private fun typeMatchesExpected(
        returnType: KSTypeReference?,
        ksFunctionDeclaration: KSFunctionDeclaration,
    ): Boolean {
        if (returnType == null) {
            return false
        }
        val typeArguments = returnType.resolve().arguments
        if (typeArguments.size != 1) {
            return false
        }
        val argument = typeArguments[0]
        val expected = getGlideTypeValue(ksFunctionDeclaration)
        return (argument.toString() == expected)
    }

    private fun isRequestBuilder(ksType: KSType): Boolean {
        val toCompare = ksType.starProjection()
        return (toCompare.toClassName().reflectionName() == "com.bumptech.glide.RequestBuilder")
    }

    private fun validateGlideTypeAnnotations(executableElement: KSFunctionDeclaration) {
        validateAnnotatedNonNull(executableElement)
    }

    private fun validateAnnotatedNonNull(ksFunctionDeclaration: KSFunctionDeclaration) {
        val annotationNames: Set<String> = ksFunctionDeclaration.annotations.map {
            it.annotationType.resolve().toClassName().reflectionName()
        }.toSet()
        var noNonNull = true
        for (nonNull in processorUtil.nonNulls()) {
            if (annotationNames.contains(nonNull.reflectionName())) {
                noNonNull = false
                break
            }
        }
        if (noNonNull) {
            processorUtil.environment.logger.warn(
                ksFunctionDeclaration.qualifiedName?.asString()
                        + " is missing the "
                        + processorUtil.nonNull().reflectionName()
                        + " annotation,"
                        + " please add it to ensure that your extension methods are always returning"
                        + " non-null values"
            )
        }
    }

    companion object {

        private fun validateExtensionConstructor(ksDeclaration: KSDeclaration) {
            require(ksDeclaration is KSFunctionDeclaration && ksDeclaration.isPublic()) {
                ("RequestOptionsExtensions must be public, with private constructors and only static"
                        + " methods. Found a non-private constructor in: "
                        + ksDeclaration.parentDeclaration?.qualifiedName?.asString())
            }
            require(ksDeclaration.parameters.isEmpty()) {
                ("RequestOptionsExtensions must be public, with private constructors and only static"
                        + " methods. Found parameters in the constructor of: "
                        + ksDeclaration.parentDeclaration?.qualifiedName?.asString())
            }
        }

        private fun validateGlideOptionParameters(ksFunctionDeclaration: KSFunctionDeclaration) {
            require(ksFunctionDeclaration.parameters.isNotEmpty()) {
                ("@GlideOption methods must take a "
                        + "BaseRequestOptions<?> object as their first parameter, but "
                        + ksFunctionDeclaration.qualifiedName?.asString()
                        + " has none")
            }
            val first = ksFunctionDeclaration.parameters[0]
            val expected = first.type.resolve()
            if (!isBaseRequestOptions(expected)) {
                throw IllegalArgumentException(
                    ("@GlideOption methods must take a"
                            + " BaseRequestOptions<?> object as their first parameter, but the first parameter"
                            + " in "
                            + ksFunctionDeclaration.qualifiedName?.asString()
                            + " is "
                            + expected))
            }
        }

        private fun isBaseRequestOptions(ksType: KSType): Boolean {
            return ksType.toClassName()
                .reflectionName() == "com.bumptech.glide.request.BaseRequestOptions<?>"
        }

        private fun getComparableParameterNames(
            element: KSFunctionDeclaration, skipFirst: Boolean,
        ): List<String> {
            var parameters = element.parameters
            if (skipFirst) {
                parameters = parameters.subList(1, parameters.size)
            }
            val result: MutableList<String> = ArrayList(parameters.size)
            for (parameter in parameters) {
                result.add(parameter.type.resolve().toClassName().reflectionName())
            }
            return result
        }

        private fun validateGlideTypeParameters(ksFunctionDeclaration: KSFunctionDeclaration) {
            if (ksFunctionDeclaration.parameters.size != 1) {
                throw IllegalArgumentException(
                    ("@GlideType methods must take a"
                            + " RequestBuilder object as their first and only parameter, but given multiple for: "
                            + ksFunctionDeclaration.qualifiedName?.asString()))
            }
            val first = ksFunctionDeclaration.parameters[0]
            val argumentType = first.type.resolve()
            if (!argumentType.toString().startsWith("com.bumptech.glide.RequestBuilder")) {
                throw IllegalArgumentException(
                    ("@GlideType methods must take a"
                            + " RequestBuilder object as their first and only parameter, but given: "
                            + argumentType
                            + " for: "
                            + ksFunctionDeclaration.qualifiedName?.asString()))
            }
        }
    }
}