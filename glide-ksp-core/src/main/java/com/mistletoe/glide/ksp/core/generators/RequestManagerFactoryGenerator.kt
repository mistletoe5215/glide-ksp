package com.mistletoe.glide.ksp.core.generators

import com.mistletoe.glide.ksp.core.ProcessorUtil
import com.mistletoe.glide.ksp.core.toClassName
import com.squareup.kotlinpoet.*


/**
 * @brief
 * @author mistletoe
 * @date 2022/2/28
 **/
internal class RequestManagerFactoryGenerator(private val processorUtil: ProcessorUtil) {
    companion object {
        private const val GLIDE_QUALIFIED_NAME = "com.bumptech.glide.Glide"
        private const val LIFECYCLE_QUALIFIED_NAME = "com.bumptech.glide.manager.Lifecycle"
        private const val REQUEST_MANAGER_TREE_NODE_QUALIFIED_NAME =
            "com.bumptech.glide.manager.RequestManagerTreeNode"
        private const val REQUEST_MANAGER_FACTORY_QUALIFIED_NAME =
            "com.bumptech.glide.manager.RequestManagerRetriever.RequestManagerFactory"
        private const val REQUEST_MANAGER_QUALIFIED_NAME = "com.bumptech.glide.RequestManager"
        private val CONTEXT_CLASS_NAME = ClassName("android.content", "Context")
        const val GENERATED_REQUEST_MANAGER_FACTORY_PACKAGE_NAME = "com.bumptech.glide"
        const val GENERATED_REQUEST_MANAGER_FACTORY_SIMPLE_NAME = "GeneratedRequestManagerFactory"
    }

    private val glideType by lazy {
        processorUtil.resolver.getClassDeclarationByName(processorUtil.resolver.getKSNameFromString(
            GLIDE_QUALIFIED_NAME))!!
    }
    private val lifecycleType by lazy {
        processorUtil.resolver.getClassDeclarationByName(processorUtil.resolver.getKSNameFromString(
            LIFECYCLE_QUALIFIED_NAME))!!
    }
    private val requestManagerTreeNodeType by lazy {
        processorUtil.resolver.getClassDeclarationByName(processorUtil.resolver.getKSNameFromString(
            REQUEST_MANAGER_TREE_NODE_QUALIFIED_NAME))!!
    }
    private val requestManagerFactoryInterface by lazy {
        processorUtil.resolver.getClassDeclarationByName(processorUtil.resolver.getKSNameFromString(
            REQUEST_MANAGER_FACTORY_QUALIFIED_NAME))!!
    }
    private val requestManagerClassName by lazy {
        processorUtil.resolver.getClassDeclarationByName(processorUtil.resolver.getKSNameFromString(
            REQUEST_MANAGER_QUALIFIED_NAME))!!.toClassName()
    }

    fun generate(
        generatedCodePackageName: String,
        generatedRequestManagerSpec: TypeSpec,
    ): TypeSpec {
        return TypeSpec.classBuilder(GENERATED_REQUEST_MANAGER_FACTORY_SIMPLE_NAME)
            .addSuperinterface(requestManagerFactoryInterface.toClassName())
            .addKdoc("Generated code, do not modify\n")
            .addFunction(
                FunSpec.builder("build")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(requestManagerClassName)
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
                    .addStatement(
                        "return %T(glide, lifecycle, treeNode, context)",
                        ClassName(generatedCodePackageName, generatedRequestManagerSpec.name!!))
                    .build())
            .build()
    }
}