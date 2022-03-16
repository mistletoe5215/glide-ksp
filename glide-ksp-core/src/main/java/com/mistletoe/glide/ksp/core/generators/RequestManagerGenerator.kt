package com.mistletoe.glide.ksp.core.generators

import com.bumptech.glide.annotation.GlideExtension
import com.bumptech.glide.annotation.GlideType
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.mistletoe.glide.ksp.core.JET_BRAINS_DEPRECATED
import com.mistletoe.glide.ksp.core.ProcessorUtil
import com.mistletoe.glide.ksp.core.toClassName
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy

/**
 * @brief
 * @author mistletoe
 * @date 2022/3/2
 **/
internal class RequestManagerGenerator(private val processorUtil: ProcessorUtil) {
    companion object {
        private const val GLIDE_QUALIFIED_NAME = "com.bumptech.glide.Glide"
        private const val REQUEST_MANAGER_QUALIFIED_NAME = "com.bumptech.glide.RequestManager"
        private const val LIFECYCLE_QUALIFIED_NAME = "com.bumptech.glide.manager.Lifecycle"
        private const val REQUEST_MANAGER_TREE_NODE_QUALIFIED_NAME =
            "com.bumptech.glide.manager.RequestManagerTreeNode"
        private val CONTEXT_CLASS_NAME = ClassName("android.content", "Context")
        private const val GENERATED_REQUEST_MANAGER_SIMPLE_NAME = "GlideRequests"
    }

    private val lifecycleType by lazy {
        processorUtil.resolver.getClassDeclarationByName(processorUtil.resolver.getKSNameFromString(
            LIFECYCLE_QUALIFIED_NAME))!!
    }
    private val requestManagerTreeNodeType by lazy {
        processorUtil.resolver.getClassDeclarationByName(processorUtil.resolver.getKSNameFromString(
            REQUEST_MANAGER_TREE_NODE_QUALIFIED_NAME))!!
    }
    private val glideType by lazy {
        processorUtil.resolver.getClassDeclarationByName(processorUtil.resolver.getKSNameFromString(
            GLIDE_QUALIFIED_NAME))!!
    }
    private val requestManagerType by lazy {
        processorUtil.resolver.getClassDeclarationByName(processorUtil.resolver.getKSNameFromString(
            REQUEST_MANAGER_QUALIFIED_NAME))!!
    }
    private val requestBuilderType by lazy {
        processorUtil.resolver.getClassDeclarationByName(processorUtil.resolver.getKSNameFromString(
            RequestBuilderGenerator.REQUEST_BUILDER_QUALIFIED_NAME))!!
    }
    private var generatedRequestBuilderClassName: ClassName? = null
    private val requestManagerClassName by lazy { requestManagerType.toClassName() }
    fun generate(
        generatedCodePackageName: String,
        requestOptions: TypeSpec,
        requestBuilder: TypeSpec,
        glideExtensions: Set<String>,
    ): TypeSpec {
        generatedRequestBuilderClassName =
            ClassName(generatedCodePackageName, requestBuilder.name!!)
        return TypeSpec.classBuilder(GENERATED_REQUEST_MANAGER_SIMPLE_NAME)
            .superclass(requestManagerClassName)
            .addKdoc(
                """
                Includes all additions from methods in {@link %T}s
                annotated with {@link %T}
                
                <p>Generated code, do not modify
                
                """.trimIndent(),
                GlideExtension::class,
                GlideType::class)
            .addAnnotation(
                AnnotationSpec.builder(SuppressWarnings::class)
                    .addMember("%S", "deprecation")
                    .build())
            .addFunction(generateAsMethod(generatedCodePackageName, requestBuilder))
            .addFunction(generateCallSuperConstructor())
            .addFunctions(generateExtensionRequestManagerMethods(glideExtensions))
            .addFunctions(generateRequestManagerRequestManagerMethodOverrides(
                generatedCodePackageName))
            .addFunctions(generateRequestManagerRequestBuilderMethodOverrides())
            .addFunctions(listOfNotNull(generateOverrideSetRequestOptions(generatedCodePackageName,
                requestOptions)))
            .build()
    }

    private fun generateCallSuperConstructor(): FunSpec {
        return FunSpec.constructorBuilder()
            .addParameter(
                ParameterSpec.builder("glide", glideType.toClassName())
                    .build())
            .addParameter(
                ParameterSpec.builder("lifecycle", lifecycleType.toClassName())
                    .build())
            .addParameter(
                ParameterSpec.builder("treeNode", requestManagerTreeNodeType.toClassName())
                    .build())
            .addParameter(
                ParameterSpec.builder("context", CONTEXT_CLASS_NAME)
                    .build())
            .callSuperConstructor("glide", "lifecycle", "treeNode", "context")
            .build()
    }

    private fun generateAsMethod(
        generatedCodePackageName: String,
        requestBuilder: TypeSpec,
    ): FunSpec {
        val resourceType = TypeVariableName("ResourceType")
        val classOfResourceType = Class::class.asClassName().parameterizedBy(resourceType)
        val generatedRequestBuilderClassName =
            ClassName(generatedCodePackageName, requestBuilder.name!!)
        val requestBuilderOfResourceType =
            generatedRequestBuilderClassName.parameterizedBy(resourceType)
        return FunSpec.builder("as")
            .addModifiers(KModifier.OVERRIDE)
            .addAnnotation(processorUtil.checkResult())
            .addTypeVariable(TypeVariableName("ResourceType"))
            .returns(requestBuilderOfResourceType)
            .addParameter(
                ParameterSpec.builder("resourceClass", classOfResourceType)
                    .build())
            .addStatement(
                "return %T(glide, this, resourceClass, context)",
                this.generatedRequestBuilderClassName!!)
            .build()
    }

    /** Generates the list of overrides of methods that return `RequestManager`.  */
    private fun generateRequestManagerRequestManagerMethodOverrides(
        generatedPackageName: String,
    ): List<FunSpec> {
        return processorUtil.findInstanceMethodsReturning(requestManagerType,
            requestManagerType.asType(emptyList()))
            .map {
                generateRequestManagerRequestManagerMethodOverride(
                    generatedPackageName, it)
            }
    }

    private fun generateRequestManagerRequestManagerMethodOverride(
        generatedPackageName: String, method: KSFunctionDeclaration,
    ): FunSpec {
        val generatedRequestManagerName =
            ClassName(generatedPackageName, GENERATED_REQUEST_MANAGER_SIMPLE_NAME)
        val returns = processorUtil
            .overriding(method)
            .returns(generatedRequestManagerName)
        return returns
            .addCode(
                ProcessorUtil.generateCastingSuperCall(generatedRequestManagerName,
                    returns.build()))
            .build()
    }

    /** Generates the list of overrides of methods that return `RequestBuilder`.  */
    private fun generateRequestManagerRequestBuilderMethodOverrides(): List<FunSpec> {
        // Without the erasure, this is a RequestBuilder<Y>. A RequestBuilder<X> is not assignable to a
        // RequestBuilder<Y>. After type erasure this is a RequestBuilder. A RequestBuilder<X> is
        // assignable to the raw RequestBuilder.
        val rawRequestBuilder = requestBuilderType.asType(emptyList()).starProjection()
        return processorUtil.findInstanceMethodsReturning(requestManagerType, rawRequestBuilder)
            .filter {
                it.simpleName.asString() != "as"
            }.map {
                generateRequestManagerRequestBuilderMethodOverride(it)
            }

    }

    /**
     * Generates overrides of existing RequestManager methods so that they return our generated
     * RequestBuilder subtype.
     */
    private fun generateRequestManagerRequestBuilderMethodOverride(
        methodToOverride: KSFunctionDeclaration,
    ): FunSpec {
        // We've already verified that this method returns a RequestBuilder and RequestBuilders have
        // exactly one type argument, so this is safe unless those assumptions change.
        val typeArgument = methodToOverride.returnType?.resolve()?.arguments?.firstOrNull()
        typeArgument ?: throw IllegalArgumentException("error,typeArgument null")
        val generatedRequestBuilderOfType =
            generatedRequestBuilderClassName!!.parameterizedBy(typeArgument.type!!.resolve()
                .toClassName())
        val builder =
            processorUtil.overriding(methodToOverride).returns(generatedRequestBuilderOfType)
        builder.addCode(
            ProcessorUtil.generateCastingSuperCall(generatedRequestBuilderOfType, builder.build()))
        val deprecatedTypeQualifiedName = processorUtil.kotlinDeprecated().reflectionName()

        for (annotation in methodToOverride.annotations) {
            val annotationQualifiedName =
                annotation.annotationType.resolve().toClassName().reflectionName()
            if (annotationQualifiedName == deprecatedTypeQualifiedName) {
                builder.addAnnotation(
                    AnnotationSpec.builder(
                        JET_BRAINS_DEPRECATED)
                        .addMember("%S", "this method is deprecated")
                        .build())
            } else {
                builder.addAnnotation(AnnotationSpec.builder(annotation.annotationType.resolve()
                    .toClassName()).build())
            }
        }
        return builder.build()
    }

    private fun generateExtensionRequestManagerMethods(glideExtensions: Set<String>): List<FunSpec> {
        val requestManagerExtensionMethods =
            processorUtil.findAnnotatedElementsInClasses(glideExtensions,
                GlideType::class.java)
        return requestManagerExtensionMethods.map {
            generateAdditionalRequestManagerMethod(it)
        }
    }

    // Generates methods added to RequestManager via GlideExtensions.
    private fun generateAdditionalRequestManagerMethod(extensionMethod: KSFunctionDeclaration): FunSpec {
        return if (extensionMethod.returnType?.resolve()
                ?.toClassName() == Unit::class.asClassName()
        ) {
            generateAdditionalRequestManagerMethodLegacy(extensionMethod)
        } else {
            generateAdditionalRequestManagerMethodNew(extensionMethod)
        }
    }

    private fun generateAdditionalRequestManagerMethodLegacy(
        extensionMethod: KSFunctionDeclaration,
    ): FunSpec {
        val returnType: String = processorUtil
            .findClassValuesFromAnnotationOnClassAsNames(extensionMethod, GlideType::class.java)
            .iterator()
            .next()
        val returnTypeClassName = ClassName.bestGuess(returnType)
        val parameterizedTypeName =
            generatedRequestBuilderClassName!!.parameterizedBy(returnTypeClassName)
        return FunSpec.builder(extensionMethod.simpleName.asString())
            .returns(parameterizedTypeName)
            .addKdoc(processorUtil.generateSeeMethodJavadoc(extensionMethod))
            .addAnnotation(processorUtil.nonNull())
            .addAnnotation(processorUtil.checkResult())
            .addStatement(
                "%T requestBuilder = this.as(%T.class)",
                parameterizedTypeName,
                returnTypeClassName)
            .addStatement(
                "%T.%N(requestBuilder)",
                extensionMethod.parentDeclaration!!,
                extensionMethod.simpleName)
            .addStatement("return requestBuilder")
            .build()
    }

    private fun generateAdditionalRequestManagerMethodNew(extensionMethod: KSFunctionDeclaration): FunSpec {
        val returnType = processorUtil
            .findClassValuesFromAnnotationOnClassAsNames(extensionMethod, GlideType::class.java)
            .iterator()
            .next()
        val returnTypeClassName = ClassName.bestGuess(returnType)
        val parameterizedTypeName =
            generatedRequestBuilderClassName!!.parameterizedBy(returnTypeClassName)
        return FunSpec.builder(extensionMethod.simpleName.asString())
            .returns(parameterizedTypeName)
            .addKdoc(processorUtil.generateSeeMethodJavadoc(extensionMethod))
            .addAnnotation(processorUtil.nonNull())
            .addAnnotation(processorUtil.checkResult())
            .addStatement(
                "return (%T) %T.%N(this.as(%T.class))",
                parameterizedTypeName,
                extensionMethod.parentDeclaration!!,
                extensionMethod.simpleName,
                returnTypeClassName)
            .build()
    }

    /**
     * The `RequestOptions` subclass should always be our generated subclass type to avoid
     * inadvertent errors where a different subclass is applied that accidentally wipes out some logic
     * in overidden methods in our generated subclass.
     */
    private fun generateOverrideSetRequestOptions(
        generatedCodePackageName: String, generatedRequestOptions: TypeSpec?,
    ): FunSpec? {
        if (generatedRequestOptions == null) {
            return null
        }
        val requestOptionsType =
            processorUtil.resolver.getClassDeclarationByName(processorUtil.resolver.getKSNameFromString(
                RequestOptionsGenerator.REQUEST_OPTIONS_QUALIFIED_NAME))!!

        // This class may have just been generated and therefore may not be found if we try to obtain
        // it via Elements, so use just the String version instead.
        val generatedRequestOptionsQualifiedName =
            generatedCodePackageName + "." + generatedRequestOptions.name
        val methodName = "setRequestOptions"
        val parameterName = "toSet"
        return FunSpec.builder(methodName)
            .addModifiers(KModifier.OVERRIDE)
            .addModifiers(KModifier.PROTECTED)
            .addParameter(
                ParameterSpec.builder(parameterName, requestOptionsType.toClassName())
                    .build())
            .beginControlFlow(
                "if (%N is %L)", parameterName, generatedRequestOptionsQualifiedName)
            .addStatement("super.%N(%N)", methodName, parameterName)
            .nextControlFlow("else")
            .addStatement(
                "super.setRequestOptions(%L().apply(%N))",
                generatedRequestOptionsQualifiedName,
                parameterName)
            .endControlFlow()
            .build()
    }
}