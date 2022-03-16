package com.mistletoe.glide.ksp.core.generators

import com.bumptech.glide.annotation.GlideExtension
import com.bumptech.glide.annotation.GlideOption
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Variance
import com.mistletoe.glide.ksp.core.JET_BRAINS_DEPRECATED
import com.mistletoe.glide.ksp.core.ProcessorUtil
import com.mistletoe.glide.ksp.core.asType
import com.mistletoe.glide.ksp.core.toClassName
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.jetbrains.kotlin.com.google.common.base.Joiner
import org.jetbrains.kotlin.com.google.common.collect.FluentIterable
import java.io.File

/**
 * @brief
 * @author mistletoe
 * @date 2022/3/1
 **/
internal class RequestBuilderGenerator(private val processorUtil: ProcessorUtil) {
    private val transcodeTypeName by lazy { TypeVariableName(TRANSCODE_TYPE_NAME) }
    private val requestOptionsType by lazy {
        processorUtil.resolver.getClassDeclarationByName(processorUtil.resolver.getKSNameFromString(
            REQUEST_OPTIONS_QUALIFIED_NAME))!!
    }
    private val requestBuilderType by lazy {
        processorUtil.resolver.getClassDeclarationByName(processorUtil.resolver.getKSNameFromString(
            REQUEST_BUILDER_QUALIFIED_NAME))!!
    }
    private var generatedRequestBuilderClassName: ClassName? = null
    private var requestOptionsClassName: ClassName? = null
    private var generatedRequestBuilderOfTranscodeType: ClassName? = null
    fun generate(
        generatedCodePackageName: String,
        glideExtensionClassNames: Set<String>,
        generatedOptions: TypeSpec?,
    ): TypeSpec {
        requestOptionsClassName = if (generatedOptions != null) {
            ClassName(generatedCodePackageName, generatedOptions.name!!)
        } else {
            ClassName(REQUEST_OPTIONS_PACKAGE_NAME,
                RequestOptionsGenerator.BASE_REQUEST_OPTIONS_SIMPLE_NAME)
        }
        generatedRequestBuilderClassName =
            ClassName(generatedCodePackageName, GENERATED_REQUEST_BUILDER_SIMPLE_NAME)
        generatedRequestBuilderOfTranscodeType = generatedRequestBuilderClassName!!

        val requestOptionsExtensionGenerator =
            RequestOptionsExtensionGenerator(generatedRequestBuilderOfTranscodeType!!,
                processorUtil)
        val requestBuilderOfTranscodeType =
            ClassName(REQUEST_BUILDER_PACKAGE_NAME, REQUEST_BUILDER_SIMPLE_NAME).parameterizedBy(
                transcodeTypeName)
        val requestOptionsExtensionMethods =
            requestOptionsExtensionGenerator.generateInstanceMethodsForExtensions(
                glideExtensionClassNames)
        return TypeSpec.classBuilder(GENERATED_REQUEST_BUILDER_SIMPLE_NAME)
            .addKdoc(
                "Contains all public methods from {@link %T}, all options from\n",
                requestBuilderType.toClassName())
            .addKdoc("{@link %T} and all generated options from\n",
                requestOptionsType.toClassName())
            .addKdoc("{@link %T} in annotated methods in\n", GlideOption::class.java)
            .addKdoc("{@link %T} annotated classes.\n", GlideExtension::class.java)
            .addKdoc("\n")
            .addKdoc("<p>Generated code, do not modify.\n")
            .addKdoc("\n")
            .addKdoc("@see %T\n", requestBuilderType.toClassName())
            .addKdoc("@see %T\n", requestOptionsType.toClassName())
            .addAnnotation(
                AnnotationSpec.builder(SuppressWarnings::class.java)
                    .addMember("%S,%S", "unused", "deprecation")
                    .build())
            .addTypeVariable(transcodeTypeName)
            .superclass(requestBuilderOfTranscodeType)
            .addFunctions(generateConstructors())
            .addFunction(generateDownloadOnlyRequestMethod())
            .addFunctions(
                generateGeneratedRequestOptionsEquivalents(
                    requestOptionsExtensionMethods, generatedOptions))
            .addFunctions(generateRequestBuilderOverrides())
            .addFunctions(requestOptionsExtensionMethods)
            .build()
    }

    /**
     * Generates methods with equivalent names and arguments to methods annotated with [ ] in [com.bumptech.glide.annotation.GlideExtension]s that return our generated
     * `com.bumptech.glide.RequestBuilder` subclass.
     */
    private fun generateGeneratedRequestOptionsEquivalents(
        requestOptionsExtensionMethods: List<FunSpec>,
        generatedOptions: TypeSpec?,
    ): List<FunSpec> {
        return generatedOptions?.funSpecs?.filter {
            isUsefulGeneratedRequestOption(requestOptionsExtensionMethods, it)
        }?.map {
            generateGeneratedRequestOptionEquivalent(it)
        }?.toList() ?: emptyList()
    }

    /**
     * Returns `true` if the given [FunSpec] is a useful method to have in our `com.bumptech.glide.RequestBuilder` subclass.
     *
     *
     * Only newly generated methods will be included in the generated `com.bumptech.glide.request.BaseRequestBuilder` subclass, so we only have to filter out methods
     * that override other methods to avoid duplicates.
     */
    private fun isUsefulGeneratedRequestOption(
        requestOptionsExtensionMethods: List<FunSpec>, requestOptionsMethod: FunSpec,
    ): Boolean {
        return (!EXCLUDED_METHODS_FROM_BASE_REQUEST_OPTIONS.contains(
            requestOptionsMethod.name)
                && !requestOptionsMethod.modifiers.contains(KModifier.PRIVATE)
                && !requestOptionsMethod.annotations.any { it.className == JvmStatic::class.asClassName() }
                && requestOptionsMethod.returnType.toString() == requestOptionsClassName.toString()
                && !isExtensionMethod(requestOptionsExtensionMethods, requestOptionsMethod))
    }

    private fun isExtensionMethod(
        requestOptionsExtensionMethods: List<FunSpec>, requestOptionsMethod: FunSpec,
    ): Boolean {
        return requestOptionsExtensionMethods.any {
            it.name == requestOptionsMethod.name && it.parameters == requestOptionsMethod.parameters
        }
    }

    /**
     * Generates a particular method with an equivalent name and arguments to the given method from
     * the generated `com.bumptech.glide.request.BaseRequestBuilder` subclass.
     */
    private fun generateGeneratedRequestOptionEquivalent(requestOptionMethod: FunSpec): FunSpec {
        val isTransformMethod = requestOptionMethod.name.contains("transform")
                && requestOptionMethod.parameters.any { it.modifiers.contains(KModifier.VARARG) }
        val callRequestOptionsMethod = CodeBlock.builder()
            .add(".%N(${if (isTransformMethod) Variance.STAR.label else ""}",
                requestOptionMethod.name)
            .add(
                requestOptionMethod.parameters.joinToString(", ") {
                    it.name
                })
            .add(")\n")
            .build()
        val result = FunSpec.builder(requestOptionMethod.name)
            .addKdoc(
                processorUtil.generateSeeMethodJavadoc(
                    requestOptionsClassName!!, requestOptionMethod))
            .apply {
                if (requestOptionMethod.modifiers.contains(KModifier.OVERRIDE)) {
                    addModifiers(KModifier.OVERRIDE)
                }
            }
            .addAnnotations(
                requestOptionMethod.annotations.filter {
                    it.className != SafeVarargs::class.asClassName() && it.className != SuppressWarnings::class.asClassName()
                }.toList())
            .addTypeVariables(requestOptionMethod.typeVariables)
            .addParameters(requestOptionMethod.parameters)
            .addCode("return super")
            .addCode(callRequestOptionsMethod)
            .addCode(" as %T", generatedRequestBuilderOfTranscodeType)

        val suppressWarnings = buildSuppressWarnings(requestOptionMethod)
        if (suppressWarnings != null) {
            result.addAnnotation(suppressWarnings)
        }
        return result.build()
    }

    private fun buildSuppressWarnings(requestOptionMethod: FunSpec): AnnotationSpec? {
        val suppressions = mutableSetOf<String>()
        if (requestOptionMethod.annotations.contains(
                AnnotationSpec.builder(SuppressWarnings::class).build())
        ) {
            for (annotation in requestOptionMethod.annotations) {
                if (annotation.className == SuppressWarnings::class.asClassName()) {
                    val codeBlocks = annotation.members
                    suppressions.addAll(codeBlocks.map { it.toString() }.toSet())
                }
            }
        }
        if (requestOptionMethod.annotations.contains(
                AnnotationSpec.builder(SafeVarargs::class.java).build())
        ) {
            suppressions.add("unchecked")
            suppressions.add("varargs")
        }
        if (suppressions.isEmpty()) {
            return null
        }
        // Enforce ordering across compilers (Internal and External compilers end up disagreeing on the
        // order produced by the Set additions above.)
        val suppressionList = ArrayList(suppressions)
        suppressionList.sort()
        val builder: AnnotationSpec.Builder = AnnotationSpec.builder(SuppressWarnings::class.java)
        for (suppression in suppressionList) {
            builder.addMember("%S", suppression)
        }
        return builder.build()
    }

    /**
     * Generates overrides of all methods in `com.bumptech.glide.RequestBuilder` that return
     * `com.bumptech.glide.RequestBuilder` so that they return our generated subclass instead.
     */
    private fun generateRequestBuilderOverrides(): List<FunSpec> {
        val rawRequestBuilderType = requestBuilderType.asType().starProjection()
        return processorUtil.findInstanceMethodsReturning(requestBuilderType, rawRequestBuilderType)
            .map {
                generateRequestBuilderOverride(it)
            }
    }

    /**
     * Generates an override of a particular method in `com.bumptech.glide.RequestBuilder` that
     * returns `com.bumptech.glide.RequestBuilder` so that it returns our generated subclass
     * instead.
     */
    private fun generateRequestBuilderOverride(methodToOverride: KSFunctionDeclaration): FunSpec {
        // We've already verified that this method returns a RequestBuilder and RequestBuilders have
        // exactly one type argument, so this is safe unless those assumptions change.
        val typeArgument =
            methodToOverride.returnType?.resolve()?.declaration!!.typeParameters.firstOrNull()
        typeArgument ?: throw IllegalArgumentException("error,typeArgument null")
        val hasVarargs = methodToOverride.parameters.any { it.isVararg }
        val generatedRequestBuilderOfType =
            generatedRequestBuilderClassName!!.parameterizedBy(TypeVariableName(typeArgument.name.asString()))
        var builder =
            processorUtil.overriding(methodToOverride).returns(generatedRequestBuilderOfType)
        builder.addCode(
            CodeBlock.builder()
                .add(
                    "return super.%N(${if (hasVarargs) Variance.STAR.label else ""}",
                    methodToOverride.simpleName.asString())
                .add(FluentIterable.from(builder.build().parameters).transform { it!!.name }
                    .join(Joiner.on(", ")))
                .add(")")
                .add(" as %T\n", generatedRequestBuilderOfType)
                .build())
        for (annotation in methodToOverride.annotations) {
            val deprecatedTypeQualifiedName = processorUtil.kotlinDeprecated().reflectionName()
            val annotationQualifiedName =
                annotation.annotationType.resolve().toClassName().reflectionName()
            if (annotationQualifiedName == deprecatedTypeQualifiedName) {
                builder.addAnnotation(
                    AnnotationSpec.builder(
                        JET_BRAINS_DEPRECATED)
                        .addMember("%S", "this method is deprecated")
                        .build())
            } else {
                builder =
                    builder.addAnnotation(AnnotationSpec.builder(annotation.annotationType.resolve()
                        .toClassName()).build())
            }
        }
        if (methodToOverride.parameters.any { it.isVararg }) {
            builder = builder
                .addAnnotation(SafeVarargs::class)
                .addAnnotation(
                    AnnotationSpec.builder(SuppressWarnings::class)
                        .addMember("%S", "varargs")
                        .build())
        }
        return builder.build()
    }

    private fun generateConstructors(): List<FunSpec> {
        val classOfTranscodeType = Class::class.asClassName().parameterizedBy(transcodeTypeName)
        val starProjectionOfObject = TypeVariableName(Variance.STAR.label)
        val requestBuilderOfWildcardOfObject =
            requestBuilderType.toClassName().parameterizedBy(starProjectionOfObject)
        val firstConstructor = FunSpec.constructorBuilder()
            .addModifiers(KModifier.INTERNAL)
            .addParameter(
                ParameterSpec.builder("transcodeClass", classOfTranscodeType)
                    .build())
            .addParameter(
                ParameterSpec.builder("other", requestBuilderOfWildcardOfObject)
                    .build())
            .callSuperConstructor("transcodeClass", "other")
            .build()
        val context = ClassName("android.content", "Context")
        val glide = ClassName("com.bumptech.glide", "Glide")
        val requestManager = ClassName("com.bumptech.glide", "RequestManager")
        val secondConstructor = FunSpec.constructorBuilder()
            .addModifiers(KModifier.INTERNAL)
            .addParameter(
                ParameterSpec.builder("glide", glide)
                    .build())
            .addParameter(
                ParameterSpec.builder("requestManager", requestManager)
                    .build())
            .addParameter(
                ParameterSpec.builder("transcodeClass", classOfTranscodeType)
                    .build())
            .addParameter(
                ParameterSpec.builder("context", context)
                    .build())
            .callSuperConstructor("glide", "requestManager", "transcodeClass", "context")
            .build()
        return listOf(firstConstructor, secondConstructor)
    }

    /**
     * Overrides the protected downloadOnly method in `com.bumptech.glide.RequestBuilder` to
     * return our generated subclass instead.
     */
    private fun generateDownloadOnlyRequestMethod(): FunSpec {
        val generatedRequestBuilderOfFile =
            generatedRequestBuilderClassName!!.parameterizedBy(File::class.asClassName())
        return FunSpec.builder("getDownloadOnlyRequest")
            .addModifiers(KModifier.OVERRIDE)
            .addAnnotation(processorUtil.checkResult())
            .returns(generatedRequestBuilderOfFile)
            .addStatement(
                "return %T(%T::class.java, this).apply(DOWNLOAD_ONLY_OPTIONS)",
                generatedRequestBuilderClassName!!,
                File::class.java)
            .build()
    }

    companion object {
        private const val REQUEST_OPTIONS_PACKAGE_NAME = "com.bumptech.glide.request"
        private const val REQUEST_OPTIONS_SIMPLE_NAME = "RequestOptions"
        private const val REQUEST_OPTIONS_QUALIFIED_NAME =
            "$REQUEST_OPTIONS_PACKAGE_NAME.$REQUEST_OPTIONS_SIMPLE_NAME"
        private const val REQUEST_BUILDER_PACKAGE_NAME = "com.bumptech.glide"
        private const val REQUEST_BUILDER_SIMPLE_NAME = "RequestBuilder"
        const val REQUEST_BUILDER_QUALIFIED_NAME =
            "$REQUEST_BUILDER_PACKAGE_NAME.$REQUEST_BUILDER_SIMPLE_NAME"

        // Uses package private methods and variables.
        private const val GENERATED_REQUEST_BUILDER_SIMPLE_NAME = "GlideRequest"

        /**
         * An arbitrary name of the Generic type in the generated RequestBuilder. e.g.
         * RequestBuilder<TranscodeType>
        </TranscodeType> */
        private const val TRANSCODE_TYPE_NAME = "TranscodeType"

        /** A set of method names to avoid overriding from RequestOptions.  */
        private val EXCLUDED_METHODS_FROM_BASE_REQUEST_OPTIONS = listOf("clone", "apply")
    }
}