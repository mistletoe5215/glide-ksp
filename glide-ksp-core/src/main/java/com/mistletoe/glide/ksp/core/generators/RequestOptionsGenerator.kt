package com.mistletoe.glide.ksp.core.generators

import com.bumptech.glide.annotation.GlideExtension
import com.bumptech.glide.annotation.GlideOption
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.mistletoe.glide.ksp.core.ProcessorUtil
import com.mistletoe.glide.ksp.core.getAnnotationsByType
import com.mistletoe.glide.ksp.core.isConstructor
import com.squareup.kotlinpoet.*
import java.lang.Deprecated
import java.util.*

/**
 * @brief
 * @author mistletoe
 * @date 2022/3/1
 **/
internal class RequestOptionsGenerator(private val processorUtil: ProcessorUtil) {
    private var nextFieldId = 0
    private val requestOptionsName by lazy {
        ClassName(REQUEST_OPTIONS_PACKAGE_NAME,
            REQUEST_OPTIONS_SIMPLE_NAME)
    }
    private val requestOptionsType by lazy {
        processorUtil.resolver.getClassDeclarationByName(processorUtil.resolver.getKSNameFromString(
            REQUEST_OPTIONS_QUALIFIED_NAME))
    }
    private val requestOptionsOverrideGenerator by lazy {
        RequestOptionsOverrideGenerator(processorUtil)
    }
    private var glideOptionsName: ClassName? = null

    /**
     * static method && properties in CompanionObject
     */
    private val companionBuilder by lazy { TypeSpec.companionObjectBuilder() }

    companion object {
        private const val GENERATED_REQUEST_OPTIONS_SIMPLE_NAME = "GlideOptions"
        const val REQUEST_OPTIONS_PACKAGE_NAME = "com.bumptech.glide.request"
        private const val REQUEST_OPTIONS_SIMPLE_NAME = "RequestOptions"
        const val REQUEST_OPTIONS_QUALIFIED_NAME =
            "$REQUEST_OPTIONS_PACKAGE_NAME.$REQUEST_OPTIONS_SIMPLE_NAME"
        const val BASE_REQUEST_OPTIONS_SIMPLE_NAME = "BaseRequestOptions"
        const val BASE_REQUEST_OPTIONS_QUALIFIED_NAME =
            "$REQUEST_OPTIONS_PACKAGE_NAME.$BASE_REQUEST_OPTIONS_SIMPLE_NAME"

        /**
         * This method is a bit of a hack, but it lets us tie the static version of a method with the
         * instance version. In turn that lets us call the instance versions on the generated subclass,
         * instead of just delegating to the RequestOptions static methods. Using the instance methods on
         * the generated subclass allows our static methods to properly call code that overrides an
         * existing method in RequestOptions.
         *
         *
         * The string names here just map between the static methods in `com.bumptech.glide.request.RequestOptions` and the instance methods they call.
         */
        private fun getInstanceMethodNameFromStaticMethodName(staticMethodName: String): String {
            val equivalentInstanceMethodName = when {
                "bitmapTransform" == staticMethodName -> {
                    "transform"
                }
                "decodeTypeOf" == staticMethodName -> {
                    "decode"
                }
                staticMethodName.endsWith("Transform") -> {
                    staticMethodName.substring(0, staticMethodName.length - 9)
                }
                staticMethodName.endsWith("Of") -> {
                    staticMethodName.substring(0, staticMethodName.length - 2)
                }
                "noTransformation" == staticMethodName -> {
                    "dontTransform"
                }
                "noAnimation" == staticMethodName -> {
                    "dontAnimate"
                }
                staticMethodName == "option" -> {
                    "set"
                }
                else -> {
                    throw IllegalArgumentException("Unrecognized static method name: $staticMethodName")
                }
            }
            return equivalentInstanceMethodName
        }

        private fun memoizeStaticMethodFromArguments(staticMethod: KSFunctionDeclaration): Boolean {
            return (staticMethod.parameters.isEmpty()
                    || (staticMethod.parameters.size == 1
                    && (staticMethod
                .parameters[0].name?.asString() == "android.content.Context")))
        }

        @OptIn(KspExperimental::class)
        private fun getStaticMethodName(element: KSFunctionDeclaration): String? {
            val glideOption = element.getAnnotationsByType(GlideOption::class).firstOrNull()
            return glideOption?.staticMethodName
        }

        @OptIn(KspExperimental::class)
        private fun memoizeStaticMethodFromAnnotation(element: KSFunctionDeclaration): Boolean {
            val glideOption = element.getAnnotationsByType(GlideOption::class).firstOrNull()
            return glideOption != null && glideOption.memoizeStaticMethod
        }

        @OptIn(KspExperimental::class)
        private fun skipStaticMethod(element: KSFunctionDeclaration): Boolean {
            val glideOption = element.getAnnotationsByType(GlideOption::class).firstOrNull()
            return glideOption != null && glideOption.skipStaticMethod
        }
    }

    fun generate(
        generatedCodePackageName: String,
        glideExtensionClassNames: Set<String>,
    ): TypeSpec {
        glideOptionsName =
            ClassName(generatedCodePackageName, GENERATED_REQUEST_OPTIONS_SIMPLE_NAME)
        val requestOptionsExtensionGenerator =
            RequestOptionsExtensionGenerator(glideOptionsName!!, processorUtil)
        val staticMethodsForExtensions =
            requestOptionsExtensionGenerator.getRequestOptionExtensionMethods(
                glideExtensionClassNames).filter {
                !skipStaticMethod(it)
            }.map {
                generateStaticMethodEquivalentForExtensionMethod(it)
            }
        val methodsForExtensions: MutableList<MethodAndLaterInitVar> = ArrayList()
        methodsForExtensions.addAll(staticMethodsForExtensions)
        val extensionMethodSignatures = methodsForExtensions.map {
            MethodSignature(it.method!!)
        }.toSet()
        val staticOverrides = generateStaticMethodOverridesForRequestOptions()
        val instanceOverrides =
            requestOptionsOverrideGenerator.generateInstanceMethodOverridesForRequestOptions(
                glideOptionsName!!)
        val allMethodsAndLaterInitVars: MutableList<MethodAndLaterInitVar> = ArrayList()
        for (item in staticOverrides) {
            if (extensionMethodSignatures.contains(MethodSignature(
                    item.method!!))
            ) {
                continue
            }
            allMethodsAndLaterInitVars.add(item)
        }
        allMethodsAndLaterInitVars.addAll(methodsForExtensions)
        val classBuilder = TypeSpec.classBuilder(GENERATED_REQUEST_OPTIONS_SIMPLE_NAME)
            .addKdoc(generateClassJavadoc(glideExtensionClassNames))
            .superclass(requestOptionsName)
        for (methodAndLaterInitVar in allMethodsAndLaterInitVars) {
            if (methodAndLaterInitVar.method != null) {
                companionBuilder.addFunction(methodAndLaterInitVar.method)
            }
            if (methodAndLaterInitVar.laterInitVar != null) {
                companionBuilder.addProperty(methodAndLaterInitVar.laterInitVar.toBuilder()
                    .mutable(true).build())
            }
        }
        instanceOverrides.filter {
            !extensionMethodSignatures.contains(MethodSignature(it))
        }.forEach {
            classBuilder.addFunction(it)
        }
        return classBuilder.addType(companionBuilder.build()).build()
    }

    private fun generateClassJavadoc(glideExtensionClassNames: Set<String>): CodeBlock {
        val builder = CodeBlock.builder()
            .add(
                "Automatically generated from {@link %T} annotated classes.\n",
                GlideExtension::class.java)
            .add("\n")
            .add("@see %T\n", requestOptionsName)
        for (glideExtensionClass in glideExtensionClassNames) {
            builder.add("@see %T\n", ClassName.bestGuess(glideExtensionClass))
        }
        return builder.build()
    }

    @OptIn(KspExperimental::class)
    private fun generateStaticMethodOverridesForRequestOptions(): List<MethodAndLaterInitVar> {
        val staticMethodsThatReturnRequestOptions = processorUtil.findStaticMethodsReturning(
            requestOptionsType!!,
            requestOptionsType!!.asType(emptyList())).filter { !it.isConstructor() }
        val staticMethods = mutableListOf<MethodAndLaterInitVar>()
        for (element in staticMethodsThatReturnRequestOptions) {
            if (element.getAnnotationsByType(Deprecated::class).firstOrNull() != null) {
                continue
            }
            staticMethods.add(generateStaticMethodEquivalentForRequestOptionsStaticMethod(element))
        }
        return staticMethods
    }

    private fun generateStaticMethodEquivalentForRequestOptionsStaticMethod(
        staticMethod: KSFunctionDeclaration,
    ): MethodAndLaterInitVar {
        val memoize = memoizeStaticMethodFromArguments(staticMethod)
        val staticMethodName = staticMethod.simpleName.asString()
        val equivalentInstanceMethodName =
            getInstanceMethodNameFromStaticMethodName(staticMethodName)
        val methodSpecBuilder = FunSpec.builder(staticMethodName)
            .addKdoc(processorUtil.generateSeeMethodJavadoc(staticMethod))
            .returns(glideOptionsName!!)
        val createNewOptionAndCall = createNewOptionAndCall(
            memoize, methodSpecBuilder, "%T().%N(", processorUtil.getParameters(staticMethod))
        var requiredStaticField: PropertySpec? = null
        if (memoize) {
            // Mix in an incrementing unique id to handle method overloading.
            val staticVariableName = staticMethodName + nextFieldId++
            requiredStaticField =
                PropertySpec.builder(staticVariableName, glideOptionsName!!.copy(nullable = true))
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("null")
                    .build()
            methodSpecBuilder
                .beginControlFlow("if (%T.%N == null)", glideOptionsName!!, staticVariableName)
                .addStatement(
                    "%T.%N = $createNewOptionAndCall.autoClone()",
                    glideOptionsName!!,
                    staticVariableName,
                    glideOptionsName!!,
                    equivalentInstanceMethodName)
                .endControlFlow()
                .addStatement("return %T.%N!!", glideOptionsName!!, staticVariableName)
        } else {
            // Generates code that looks like:
            // return new GlideOptions().<methodName>()
            methodSpecBuilder.addStatement(
                "return $createNewOptionAndCall", glideOptionsName!!, equivalentInstanceMethodName)
        }
        val typeParameters = staticMethod.typeParameters
        for (typeParameterElement in typeParameters) {
            methodSpecBuilder.addTypeVariable(
                TypeVariableName(typeParameterElement.simpleName.asString()))
        }
        methodSpecBuilder
            .addAnnotation(processorUtil.checkResult())
            .addAnnotation(AnnotationSpec.builder(JvmStatic::class).build())
        return MethodAndLaterInitVar(methodSpecBuilder.build(), requiredStaticField)
    }

    private fun createNewOptionAndCall(
        memoize: Boolean,
        methodSpecBuilder: FunSpec.Builder,
        start: String,
        specs: List<ParameterSpec>,
    ): StringBuilder {
        var createNewOptionAndCall = StringBuilder(start)
        if (specs.isNotEmpty()) {
            methodSpecBuilder.addParameters(specs)
            for (parameter in specs) {
                createNewOptionAndCall.append(parameter.name)
                // use the Application Context to avoid memory leaks.
                if (memoize && isAndroidContext(parameter)) {
                    createNewOptionAndCall.append(".getApplicationContext()")
                }
                createNewOptionAndCall.append(", ")
            }
            createNewOptionAndCall = StringBuilder(
                createNewOptionAndCall.substring(0, createNewOptionAndCall.length - 2))
        }
        createNewOptionAndCall.append(")")
        return createNewOptionAndCall
    }

    private fun isAndroidContext(parameter: ParameterSpec): Boolean {
        return parameter.type.toString() == "android.content.Context"
    }

    private fun generateStaticMethodEquivalentForExtensionMethod(
        instanceMethod: KSFunctionDeclaration,
    ): MethodAndLaterInitVar {
        var staticMethodName = getStaticMethodName(instanceMethod)
        val instanceMethodName = instanceMethod.simpleName.asString()
        if (staticMethodName.isNullOrEmpty()) {
            staticMethodName = if (instanceMethodName.startsWith("dont")) {
                "no" + instanceMethodName.replace("dont", "")
            } else {
                instanceMethodName + "Of"
            }
        }
        val memoize = memoizeStaticMethodFromAnnotation(instanceMethod)
        val methodSpecBuilder = FunSpec.builder(staticMethodName)
            .addKdoc(processorUtil.generateSeeMethodJavadoc(instanceMethod))
            .returns(glideOptionsName!!)
        var parameters = instanceMethod.parameters

        // Always remove the first parameter because it's always RequestOptions in extensions. The
        // actual method we want to generate will pass the RequestOptions in to the extension method,
        // but should not itself require a RequestOptions object to be passed in.
        require(parameters.isNotEmpty()) { "Expected non-empty parameters for: $instanceMethod" }
        // Remove is not supported.
        parameters = parameters.subList(1, parameters.size)
        val createNewOptionAndCall = createNewOptionAndCall(
            memoize, methodSpecBuilder, "%T().%L(", processorUtil.getParameters(parameters))
        var requiredStaticField: PropertySpec? = null
        if (memoize) {
            // Mix in an incrementing unique id to handle method overloading.
            val staticVariableName = staticMethodName + nextFieldId++
            requiredStaticField = PropertySpec.builder(staticVariableName, glideOptionsName!!)
                .addModifiers(KModifier.PRIVATE)
                .build()
            methodSpecBuilder
                .beginControlFlow("if (%T.%N == null)", glideOptionsName!!, staticVariableName)
                .addStatement(
                    "%T.%N = $createNewOptionAndCall.autoClone()",
                    glideOptionsName!!,
                    staticVariableName,
                    glideOptionsName!!,
                    instanceMethodName)
                .endControlFlow()
                .addStatement("return %T.%N", glideOptionsName!!, staticVariableName)
        } else {
            // Generates code that looks like:
            // return new GlideOptions().<methodName>()
            methodSpecBuilder.addStatement(
                "return $createNewOptionAndCall", glideOptionsName!!, instanceMethodName)
        }
        val typeParameters = instanceMethod.typeParameters
        for (typeParameterElement in typeParameters) {
            methodSpecBuilder.addTypeVariable(
                TypeVariableName(typeParameterElement.simpleName.asString()))
        }
        methodSpecBuilder.addAnnotation(processorUtil.checkResult())
        return MethodAndLaterInitVar(methodSpecBuilder.build(), requiredStaticField)
    }

    private class MethodAndLaterInitVar(
        val method: FunSpec?,
        val laterInitVar: PropertySpec?,
    )

    private class MethodSignature constructor(spec: FunSpec) {
        private val returnType = spec.returnType!!
        private val parameterTypes = spec.parameters.map { it.type }
        private val name = spec.name
        override fun equals(o: Any?): Boolean {
            if (o is MethodSignature) {
                return (name == o.name && returnType == o.returnType
                        && parameterTypes == o.parameterTypes)
            }
            return false
        }

        override fun hashCode(): Int {
            return Objects.hash(name, returnType, parameterTypes)
        }
    }
}