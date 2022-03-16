package com.mistletoe.glide.ksp.core.generators

import com.bumptech.glide.annotation.Excludes
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.mistletoe.glide.ksp.core.ProcessorUtil
import com.mistletoe.glide.ksp.core.isConstructor
import com.mistletoe.glide.ksp.core.toClassName
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import java.util.*

/**
 * @brief
 * @author mistletoe
 * @date 2022/2/28
 **/
internal class AppModuleGenerator(private val processorUtil: ProcessorUtil) {
    fun generate(
        appGlideModule: KSClassDeclaration,
        libraryGlideModuleClassNames: Set<String>,
    ): TypeSpec {
        val appGlideModuleClassName = appGlideModule.toClassName()
        val excludedGlideModuleClassNames = getExcludedGlideModuleClassNames(appGlideModule)
        val orderedLibraryGlideModuleClassNames = ArrayList(libraryGlideModuleClassNames)
        orderedLibraryGlideModuleClassNames.sort()
        val constructor = generateConstructor(
            appGlideModuleClassName,
            orderedLibraryGlideModuleClassNames,
            excludedGlideModuleClassNames)
        val registerComponents = generateRegisterComponents(
            orderedLibraryGlideModuleClassNames, excludedGlideModuleClassNames)
        val getExcludedModuleClasses =
            generateGetExcludedModuleClasses(excludedGlideModuleClassNames)
        val applyOptions = FunSpec.builder("applyOptions")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter(
                ParameterSpec.builder("context", ClassName("android.content", "Context"))
                    .build())
            .addParameter(
                ParameterSpec.builder("builder", ClassName("com.bumptech.glide", "GlideBuilder"))
                    .build())
            .addStatement("appGlideModule.applyOptions(context, builder)", appGlideModule)
            .build()
        val isManifestParsingEnabled =
            FunSpec.builder("isManifestParsingEnabled")
                .addModifiers(KModifier.OVERRIDE)
                .returns(Boolean::class)
                .addStatement("return appGlideModule.isManifestParsingEnabled()", appGlideModule)
                .build()
        val builder = TypeSpec.classBuilder(GENERATED_APP_MODULE_IMPL_SIMPLE_NAME)
            .addModifiers(KModifier.INTERNAL)
            .addAnnotation(
                AnnotationSpec.builder(SuppressWarnings::class.java)
                    .addMember("%S", "deprecation")
                    .build())
            .superclass(ClassName(GENERATED_ROOT_MODULE_PACKAGE_NAME,
                GENERATED_ROOT_MODULE_SIMPLE_NAME))
            .addProperty(PropertySpec.builder("appGlideModule", appGlideModuleClassName)
                .addModifiers(KModifier.PRIVATE).build())
            .addFunction(constructor)
            .addFunction(applyOptions)
            .addFunction(registerComponents)
            .addFunction(isManifestParsingEnabled)
            .addFunction(getExcludedModuleClasses)
        val generatedRequestManagerFactoryClassName = ClassName(
            RequestManagerFactoryGenerator.GENERATED_REQUEST_MANAGER_FACTORY_PACKAGE_NAME,
            RequestManagerFactoryGenerator.GENERATED_REQUEST_MANAGER_FACTORY_SIMPLE_NAME)
        builder.addFunction(
            FunSpec.builder("getRequestManagerFactory")
                .addModifiers(KModifier.OVERRIDE)
                .returns(generatedRequestManagerFactoryClassName)
                .addStatement("return %T()", generatedRequestManagerFactoryClassName)
                .build())
        return builder.build()
    }

    // TODO: When we drop support for parsing GlideModules from AndroidManifests, remove this method.
    private fun generateGetExcludedModuleClasses(excludedClassNames: Collection<String>): FunSpec {
        val wildCardOfObject = WildcardTypeName.producerOf(Any::class)
        val classOfWildcardOfObject =
            Class::class.asClassName().parameterizedBy(wildCardOfObject)
        val setOfClassOfWildcardOfObject =
            MutableSet::class.asClassName().parameterizedBy(classOfWildcardOfObject)
        val hashSetOfClassOfWildcardOfObject =
            HashSet::class.asClassName().parameterizedBy(classOfWildcardOfObject)
        val builder = FunSpec.builder("getExcludedModuleClasses")
            .addModifiers(KModifier.OVERRIDE)
            .returns(setOfClassOfWildcardOfObject)
        if (excludedClassNames.isEmpty()) {
            builder.addStatement("return %T.emptySet()", Collections::class.java)
        } else {
            builder.addStatement(
                "%T excludedClasses = %T()",
                setOfClassOfWildcardOfObject,
                hashSetOfClassOfWildcardOfObject)
            for (excludedClassName in excludedClassNames) {
                // TODO: Remove this when we no longer support manifest parsing.
                // Using a Literal ($L) instead of a type ($T) to get a fully qualified import that allows
                // us to suppress deprecation warnings. Aimed at deprecated GlideModules.
                builder.addStatement("excludedClasses.add(%L.class)", excludedClassName)
            }
            builder.addStatement("return excludedClasses")
        }
        return builder.build()
    }

    private fun generateRegisterComponents(
        libraryGlideModuleClassNames: Collection<String>,
        excludedGlideModuleClassNames: Collection<String>,
    ): FunSpec {
        val registerComponents = FunSpec.builder("registerComponents")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter(
                ParameterSpec.builder("context", ClassName("android.content", "Context"))
                    .build())
            .addParameter(
                ParameterSpec.builder("glide", ClassName("com.bumptech.glide", "Glide"))
                    .build())
            .addParameter(
                ParameterSpec.builder("registry", ClassName("com.bumptech.glide", "Registry"))
                    .build())
        for (glideModule in libraryGlideModuleClassNames) {
            if (excludedGlideModuleClassNames.contains(glideModule)) {
                continue
            }
            val moduleClassName = ClassName.bestGuess(glideModule)
            registerComponents.addStatement(
                "%T().registerComponents(context, glide, registry)", moduleClassName)
        }
        // Order matters here. The AppGlideModule must be called last.
        registerComponents.addStatement("appGlideModule.registerComponents(context, glide, registry)")
        return registerComponents.build()
    }

    private fun doesAppGlideModuleConstructorAcceptContext(appGlideModule: ClassName): Boolean {
        val appGlideModuleType =
            processorUtil.resolver.getClassDeclarationByName(processorUtil.resolver.getKSNameFromString(
                appGlideModule.reflectionName()))!!
        for (enclosed in appGlideModuleType.declarations) {
            if (enclosed is KSFunctionDeclaration && enclosed.isConstructor()) {
                val parameters = enclosed.parameters
                return when {
                    parameters.isEmpty() -> {
                        false
                    }
                    parameters.size > 1 -> {
                        throw IllegalStateException(
                            "Constructor for "
                                    + appGlideModule
                                    + " accepts too many parameters"
                                    + ", it should accept no parameters, or a single Context")
                    }
                    else -> {
                        val parameter = parameters[0]
                        val parameterType = parameter.type.resolve()
                        val contextType =
                            processorUtil.resolver.getClassDeclarationByName(processorUtil.resolver.getKSNameFromString(
                                "android.content.Context"))!!
                        check(parameterType == contextType) { "Unrecognized type: $parameterType" }
                        true
                    }
                }
            }
        }
        return false
    }

    private fun generateConstructor(
        appGlideModule: ClassName,
        libraryGlideModuleClassNames: Collection<String>,
        excludedGlideModuleClassNames: Collection<String>,
    ): FunSpec {
        val constructorBuilder = FunSpec.constructorBuilder()
            .addParameter(
                ParameterSpec.builder("context", ClassName("android.content", "Context"))
                    .build())
        if (doesAppGlideModuleConstructorAcceptContext(appGlideModule)) {
            constructorBuilder.addStatement("appGlideModule = %T(context)", appGlideModule)
        } else {
            constructorBuilder.addStatement("appGlideModule = %T()", appGlideModule)
        }
        val androidLogName = ClassName("android.util", "Log")

        // Add some log lines to indicate to developers which modules where discovered.
        constructorBuilder.beginControlFlow(
            "if (%T.isLoggable(%S, %T.DEBUG))", androidLogName, GLIDE_LOG_TAG, androidLogName)
        constructorBuilder.addStatement(
            "%T.d(%S, %S)",
            androidLogName,
            GLIDE_LOG_TAG,
            "Discovered AppGlideModule from annotation: $appGlideModule")
        // Excluded GlideModule classes from the manifest are logged in Glide's singleton.
        for (glideModule in libraryGlideModuleClassNames) {
            if (excludedGlideModuleClassNames.contains(glideModule)) {
                constructorBuilder.addStatement(
                    "%T.d(%S, %S)",
                    androidLogName,
                    GLIDE_LOG_TAG,
                    "AppGlideModule excludes LibraryGlideModule from annotation: $glideModule")
            } else {
                constructorBuilder.addStatement(
                    "%T.d(%S, %S)",
                    androidLogName,
                    GLIDE_LOG_TAG,
                    "Discovered LibraryGlideModule from annotation: $glideModule")
            }
        }
        constructorBuilder.endControlFlow()
        return constructorBuilder.build()
    }

    private fun getExcludedGlideModuleClassNames(appGlideModule: KSClassDeclaration): List<String> {
        return processorUtil.findClassValuesFromAnnotationOnClassAsNames(appGlideModule,
            Excludes::class.java).toList()
    }

    companion object {
        const val GENERATED_ROOT_MODULE_PACKAGE_NAME = "com.bumptech.glide"
        private const val GLIDE_LOG_TAG = "Glide"
        private const val GENERATED_APP_MODULE_IMPL_SIMPLE_NAME = "GeneratedAppGlideModuleImpl"
        private const val GENERATED_ROOT_MODULE_SIMPLE_NAME = "GeneratedAppGlideModule"
    }

}