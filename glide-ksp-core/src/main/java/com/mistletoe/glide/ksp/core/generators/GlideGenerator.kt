package com.mistletoe.glide.ksp.core.generators

import com.bumptech.glide.annotation.GlideExtension
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.mistletoe.glide.ksp.core.JET_BRAINS_DEPRECATED
import com.mistletoe.glide.ksp.core.ProcessorUtil
import com.mistletoe.glide.ksp.core.toClassName
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeSpec

/**
 * @brief
 * @author mistletoe
 * @date 2022/2/28
 **/
internal class GlideGenerator(private val processorUtil: ProcessorUtil) {
    private val glideType by lazy {
        processorUtil.resolver.getClassDeclarationByName(processorUtil.resolver.getKSNameFromString(
            GLIDE_QUALIFIED_NAME))!!
    }
    private val requestManagerType by lazy {
        processorUtil.resolver.getClassDeclarationByName(processorUtil.resolver.getKSNameFromString(
            REQUEST_MANAGER_QUALIFIED_NAME))!!
    }

    fun generate(
        generatedCodePackageName: String, glideName: String, generatedRequestManager: TypeSpec,
    ): TypeSpec {
        return TypeSpec.objectBuilder(glideName)
            .addKdoc(
                """
                The entry point for interacting with Glide for Applications
                
                <p>Includes all generated APIs from all
                {@link %T}s in source and dependent libraries.
                
                <p>This class is generated and should not be modified
                @see %T
                
                """.trimIndent(),
                GlideExtension::class.java,
                glideType.toClassName())
            .addFunctions(
                generateOverridesForGlideMethods(generatedCodePackageName, generatedRequestManager))
            .build()
    }

    private fun generateOverridesForGlideMethods(
        generatedCodePackageName: String, generatedRequestManager: TypeSpec,
    ): List<FunSpec> {
        return discoverGlideMethodsToOverride().map {
            if (isGlideWithMethod(it)) {
                overrideGlideWithMethod(
                    generatedCodePackageName, generatedRequestManager, it)
            } else {
                overrideGlideStaticMethod(it)
            }
        }
    }

    private fun overrideGlideStaticMethod(methodToOverride: KSFunctionDeclaration): FunSpec {
        val parameters = processorUtil.getParameters(methodToOverride)
        val element = methodToOverride.returnType as? KSClassDeclaration
        val builder = FunSpec.builder(methodToOverride.simpleName.asString())
            .addAnnotation(AnnotationSpec.builder(JvmStatic::class).build())
            .addKdoc(processorUtil.generateSeeMethodJavadoc(methodToOverride))
            .addParameters(parameters)
        addReturnAnnotations(builder, methodToOverride)
        element?.let {
            builder.returns(it.toClassName())
        }
        var code = StringBuilder(element?.let { "return " } ?: "")
        code.append("%T.%N(")
        val args = mutableListOf<Any>()
        args.add(glideType.toClassName())
        args.add(methodToOverride.simpleName.asString())
        if (parameters.isNotEmpty()) {
            for (param in parameters) {
                code.append("%L, ")
                args.add(param.name)
            }
            code = StringBuilder(code.substring(0, code.length - 2))
        }
        code.append(")")
        builder.addStatement(code.toString(), *args.toTypedArray())
        return builder.build()
    }

    private fun addReturnAnnotations(
        builder: FunSpec.Builder,
        methodToOverride: KSFunctionDeclaration,
    ): FunSpec.Builder {
        val visibleForTestingTypeQualifiedName = processorUtil.visibleForTesting().reflectionName()
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
                // Suppress a lint warning if we're overriding a VisibleForTesting method.
                // See #1977.
                if (annotationQualifiedName == visibleForTestingTypeQualifiedName) {
                    builder.addAnnotation(
                        AnnotationSpec.builder(
                            ClassName(SUPPRESS_LINT_PACKAGE_NAME, SUPPRESS_LINT_CLASS_NAME))
                            .addMember("value = %S", "VisibleForTests")
                            .build())
                }
            }
        }
        return builder
    }

    private fun discoverGlideMethodsToOverride(): List<KSFunctionDeclaration> {
        return processorUtil.findStaticMethods(glideType)
    }

    private fun isGlideWithMethod(element: KSFunctionDeclaration): Boolean {
        return processorUtil.isReturnValueTypeMatching(element, requestManagerType)
    }

    private fun overrideGlideWithMethod(
        packageName: String,
        generatedRequestManager: TypeSpec,
        methodToOverride: KSFunctionDeclaration,
    ): FunSpec {
        val generatedRequestManagerClassName =
            ClassName(packageName, generatedRequestManager.name!!)
        val parameters = processorUtil.getParameters(methodToOverride)
        require(parameters.size == 1) {
            "Expected size of 1, but got $methodToOverride"
        }
        val parameter = parameters.iterator().next()
        val builder = FunSpec.builder(methodToOverride.simpleName.asString())
            .addKdoc(processorUtil.generateSeeMethodJavadoc(methodToOverride))
            .addAnnotation(AnnotationSpec.builder(JvmStatic::class).build())
            .addParameters(parameters)
            .returns(generatedRequestManagerClassName)
            .addStatement(
                "return %T.%N(%L) as %T ",
                glideType.toClassName(),
                methodToOverride.simpleName.asString(),
                parameter.name,
                generatedRequestManagerClassName)
        return addReturnAnnotations(builder, methodToOverride).build()
    }

    companion object {
        private const val GLIDE_QUALIFIED_NAME = "com.bumptech.glide.Glide"
        private const val REQUEST_MANAGER_QUALIFIED_NAME = "com.bumptech.glide.RequestManager"
        private const val SUPPRESS_LINT_PACKAGE_NAME = "android.annotation"
        private const val SUPPRESS_LINT_CLASS_NAME = "SuppressLint"
    }
}