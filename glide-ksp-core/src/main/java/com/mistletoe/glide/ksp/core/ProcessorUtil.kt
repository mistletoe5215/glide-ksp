package com.mistletoe.glide.ksp.core

import com.bumptech.glide.annotation.GlideExtension
import com.bumptech.glide.annotation.GlideOption
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import com.mistletoe.glide.ksp.core.KSPGlideProcessor.Companion.DEBUG
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.jetbrains.kotlin.com.google.common.base.Joiner
import org.jetbrains.kotlin.com.google.common.collect.FluentIterable
import java.lang.reflect.InvocationTargetException
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.type.TypeVariable

/**
 * @brief
 * @author mistletoe
 * @date 2022/2/25
 **/
class ProcessorUtil(
    val environment: SymbolProcessorEnvironment,
    val resolver: Resolver,
) {
    private var _round: Int = 0
    private val appGlideModuleType by lazy {
        resolver.getClassDeclarationByName(APP_GLIDE_MODULE_QUALIFIED_NAME)
    }
    private val libraryGlideModuleType by lazy {
        resolver.getClassDeclarationByName(LIBRARY_GLIDE_MODULE_QUALIFIED_NAME)
    }

    fun process() {
        _round++
    }

    fun isAppGlideModule(ksClassDeclaration: KSClassDeclaration): Boolean {
        return appGlideModuleType?.asType()?.isAssignableFrom(ksClassDeclaration.asType()) ?: false
    }

    fun isLibraryGlideModule(ksClassDeclaration: KSClassDeclaration): Boolean {
        return libraryGlideModuleType?.asType()?.isAssignableFrom(ksClassDeclaration.asType())
            ?: false
    }

    @OptIn(KspExperimental::class)
    fun isExtension(ksClassDeclaration: KSClassDeclaration): Boolean {
        return ksClassDeclaration.getAnnotationsByType(GlideExtension::class).firstOrNull() != null
    }

    @OptIn(KspExperimental::class)
    fun getOverrideType(ksFunctionDeclaration: KSFunctionDeclaration): Int {
        val glideOption =
            ksFunctionDeclaration.getAnnotationsByType(GlideOption::class).firstOrNull()
        return glideOption?.override ?: GlideOption.OVERRIDE_NONE
    }

    fun writeIndexer(indexer: TypeSpec) {
        writeClass(COMPILER_PACKAGE_NAME, indexer)
    }

    fun writeClass(packageName: String, clazz: TypeSpec) {
        try {
            debugLog("Writing class:\n$clazz")
            val clazzName = clazz.name ?: return
            val fileSpecBuilder = FileSpec.builder(packageName, clazzName)
            fileSpecBuilder.addType(clazz)
            val fileSpec = fileSpecBuilder.build()
            val file = environment.codeGenerator.createNewFile(Dependencies.ALL_FILES,
                fileSpec.packageName,
                fileSpec.name)
            file.use {
                val content = fileSpec.toString().toByteArray()
                it.write(content)
            }
        } catch (e: Exception) {
          //  throw RuntimeException(e)
        }
    }

    @OptIn(KspExperimental::class)
    fun findAnnotatedElementsInClasses(
        classNames: Set<String>,
        annotationClass: Class<out Annotation>,
    ): List<KSFunctionDeclaration> {
        val result: MutableList<KSFunctionDeclaration> = ArrayList()
        for (glideExtensionClassName in classNames) {
            val glideExtension = resolver.getClassDeclarationByName(glideExtensionClassName)
            glideExtension ?: continue
            glideExtension.declarations.forEach { ksDeclaration ->
                ksDeclaration.getAnnotationsByType(annotationClass.kotlin).firstOrNull()?.let {
                    result.add(ksDeclaration as KSFunctionDeclaration)
                }
            }
        }
        return result
    }

    /**
     * @param clazz specific annotation
     * @return KSClassDeclaration list
     */
    fun getElementsFor(clazz: Class<out Annotation>): List<KSClassDeclaration> {
        val annotatedElements = resolver.getSymbolsWithAnnotation(clazz.name)
        return annotatedElements.filterIsInstance<KSClassDeclaration>().toList()
    }

    /**
     * Generates a Javadoc code block for generated methods that delegate to methods in {@link
     * GlideExtension}s.
     *
     * <p>The generated block looks something like this:
     *
     * <pre>
     * <code>
     *   {@literal @see} com.extension.package.name.ExtensionClassName#extensionMethod(arg1, argN)
     * </code>
     * </pre>
     *
     * @param method The method from the {@link GlideExtension} annotated class that the generated
     *     method this Javadoc will be attached to delegates to.
     */
    fun generateSeeMethodJavadoc(method: KSFunctionDeclaration): CodeBlock {
        return generateSeeMethodJavadoc(
            getJavadocSafeName(method.parentDeclaration!!),
            method.simpleName.asString(),
            method.parameters.map { it.type.resolve() })
    }

    /**
     * Generates a Javadoc block for generated methods that delegate to other methods.
     *
     * <p>The generated block looks something like this:
     *
     * <pre>
     * <code>
     *     {@literal @see} com.package.ClassContainingMethod.methodSimpleName(
     *         methodParam1, methodParamN)
     * </code>
     * </pre>
     *
     * @param nameOfClassContainingMethod The simple class name of the class containing the method
     *     without any generic types like {@literal <T>}.
     * @param methodSimpleName The name of the method.
     * @param methodParameters A maybe empty list of all the parameters for the method in question.
     */
    fun generateSeeMethodJavadoc(
        nameOfClassContainingMethod: TypeName,
        methodSimpleName: String,
        methodParameters: List<KSType>,
    ): CodeBlock {
        debugLog("nameOfClassContainingMethod:$nameOfClassContainingMethod  methodSimpleName:$methodSimpleName  methodParameters:$methodParameters")
        return generateSeeMethodJavadocInternal(
            nameOfClassContainingMethod,
            methodSimpleName,
            methodParameters
        )
    }

    fun generateSeeMethodJavadoc(
        nameOfClassContainingMethod: TypeName,
        methodSpec: FunSpec,
    ): CodeBlock {
        return generateSeeMethodJavadocInternal(
            nameOfClassContainingMethod,
            methodSpec.name,
            methodSpec.parameters
        )
    }

    private fun generateSeeMethodJavadocInternal(
        nameOfClassContainingMethod: TypeName,
        methodName: String,
        safeParameterNames: List<Any>,
    ): CodeBlock {
        var javadocString = StringBuilder("@see %T.%L(")
        val javadocArgs = mutableListOf<Any>()
        javadocArgs.add(nameOfClassContainingMethod)
        javadocArgs.add(methodName)
        for (param in safeParameterNames) {
            javadocString.append("%L, ")
            javadocArgs.add(param)
        }
        if (javadocArgs.size > 2) {
            javadocString =
                StringBuilder(javadocString.substring(0, javadocString.length - 2))
        }
        javadocString.append(")\n")
        debugLog("javadocArgs size:${javadocArgs.size} javadocString:$javadocString")
        return CodeBlock.of(javadocString.toString(), *javadocArgs.toTypedArray())
    }

    /**
     * Returns a safe String to use in a Javadoc that will function in a link.
     *
     * <p>This method exists because by Javadoc doesn't handle type parameters({@literal <T>} in
     * {@literal RequestOptions<T>} for example).
     */
    fun getJavadocSafeName(declaration: KSDeclaration): TypeName {
        return ClassName(declaration.packageName.asString(), declaration.simpleName.asString())
    }

    /**
     *
     * copy from FunSpec
     */
    @Deprecated("use overriding(method: KSFunctionDeclaration)")
    fun overriding(method: ExecutableElement): FunSpec.Builder {
        var modifiers: Set<Modifier> = method.modifiers
        require(Modifier.PRIVATE !in modifiers
                && Modifier.FINAL !in modifiers
                && Modifier.STATIC !in modifiers) {
            "cannot override method with modifiers: $modifiers"
        }

        val methodName = method.simpleName.toString()
        val funBuilder = FunSpec.builder(methodName)

        funBuilder.addModifiers(KModifier.OVERRIDE)

        modifiers = modifiers.toMutableSet()
        modifiers.remove(Modifier.ABSTRACT)
        funBuilder.jvmModifiers(modifiers)

        method.typeParameters
            .map { it.asType() as TypeVariable }
            .map { it.asTypeVariableName() }
            .forEach { funBuilder.addTypeVariable(it) }

        funBuilder.returns(method.returnType.asTypeName())
        funBuilder.addParameters(ParameterSpec.parametersOf(method))
        if (method.isVarArgs) {
            funBuilder.parameters[funBuilder.parameters.lastIndex] = funBuilder.parameters.last()
                .toBuilder()
                .build()
        }
        if (method.thrownTypes.isNotEmpty()) {
            val throwsValueString = method.thrownTypes.joinToString { "%T::class" }
            funBuilder.addAnnotation(AnnotationSpec.builder(Throws::class)
                .addMember(throwsValueString, *method.thrownTypes.toTypedArray())
                .build())
        }
        return funBuilder
    }

    fun overriding(method: KSFunctionDeclaration): FunSpec.Builder {
        return FunSpec.builder(method.simpleName.asString()).addModifiers(KModifier.OVERRIDE)
            .apply {
                addTypeVariables(method.typeParameters.map { TypeVariableName(it.simpleName.asString()) })
                //TODO:handle special  type variance method ,cuz ksTypeJavaImpl's  type variances has some problems
                when {
                    method.simpleName.asString() == "transition" -> {
                        method.parameters.forEach {
                            val wildcardTypeNames =
                                TypeVariableName(String.format("in %s",
                                    it.type.resolve().declaration.typeParameters[1].name.asString()))
                            val parameterTypeName =
                                GLIDE_TRANSITION_OPTION_CLASS_NAME.parameterizedBy(
                                    TypeVariableName(Variance.STAR.label),
                                    wildcardTypeNames)
                            addParameter(ParameterSpec.builder(it.name!!.asString(),
                                parameterTypeName)
                                .build())
                        }
                    }
                    method.simpleName.asString() == "thumbnail" && method.parameters.any { it.type.resolve().declaration is KSClassDeclaration && (it.type.resolve().declaration as KSClassDeclaration).toClassName() == KOTLIN_MUTABLE_LIST_CLASS_NAME } -> {
                        method.parameters.forEach {
                            val wildcardTypeNames =
                                GLIDE_REQUEST_BUILDER_CLASS_NAME.parameterizedBy(TypeVariableName(
                                    TRANS_CODE_TYPE_NAME))
                            val parameterTypeName = KOTLIN_LIST_CLASS_NAME.parameterizedBy(
                                wildcardTypeNames).copy(nullable = true)
                            addParameter(ParameterSpec.builder(it.name!!.asString(),
                                parameterTypeName)
                                .build())
                        }
                    }
                    else -> {
                        val parameters = getParameters(method)
                        addParameters(parameters)
                    }
                }

            }
    }

    fun getParameters(method: KSFunctionDeclaration): List<ParameterSpec> {
        return getParameters(method.parameters)
    }

    fun getParameters(parameters: List<KSValueParameter>): List<ParameterSpec> {
        return parameters.map {
            val kModifier = arrayListOf<KModifier>()
            kModifier.apply {
                if (it.isVararg) {
                    add(KModifier.VARARG)
                }
                if (it.isCrossInline) {
                    add(KModifier.CROSSINLINE)
                }
                if (it.isNoInline) {
                    add(KModifier.NOINLINE)
                }
            }
            val nullableType =
                resolver.getClassDeclarationByName("androidx.annotation.Nullable")!!.asType()
            val realType = it.getParameterTypeName(environment, nullableType)
            ParameterSpec.builder(
                name = it.name!!.asString(),
                type = realType
            ).addModifiers(kModifier).build()
        }
    }

    fun findClassValuesFromAnnotationOnClassAsNames(
        clazz: KSDeclaration, annotationClass: Class<out Annotation>,
    ): Set<String> {
        val annotationClassName = annotationClass.name
        var excludedModuleAnnotationValue: Any? = null
        for (annotationMirror in clazz.annotations) {
            // Two different AnnotationMirrors the same class might not be equal, so compare Strings
            // instead. This check is necessary because a given class may have multiple Annotations.
            if (annotationClassName != annotationMirror.annotationType.toString()) {
                continue
            }
            val values = annotationMirror.arguments
            // Excludes has only one value. If we ever change that, we'd need to iterate over all
            // values in the entry set and compare the keys to whatever our Annotation's attribute is
            // (usually value).
            if (values.size != 1) {
                throw IllegalArgumentException("Expected single value, but found: $values")
            }
            excludedModuleAnnotationValue = values.iterator().next().value
        }
        if (excludedModuleAnnotationValue == null) {
            return emptySet()
        }
        return if (excludedModuleAnnotationValue is List<*>) {
            val result: MutableSet<String> = HashSet(excludedModuleAnnotationValue.size)
            for (current in excludedModuleAnnotationValue) {
                result.add(getExcludedModuleClassFromAnnotationAttribute(clazz,
                    current!!))
            }
            result
        } else {
            val classType = excludedModuleAnnotationValue as KSType
            setOf(classType.toString())
        }
    }

    private fun getExcludedModuleClassFromAnnotationAttribute(
        clazz: KSDeclaration, attribute: Any,
    ): String {
        if (attribute.javaClass.simpleName == "UnresolvedClass") {
            throw IllegalArgumentException(
                "Failed to parse @Excludes for: "
                        + clazz
                        + ", one or more excluded Modules could not be found at compile time. Ensure that all"
                        + "excluded Modules are included in your classpath.")
        }
        val methods = attribute.javaClass.declaredMethods
        if (methods.isNullOrEmpty()) {
            throw IllegalArgumentException(
                "Failed to parse @Excludes for: $clazz, invalid exclude: $attribute")
        }
        for (method in methods) {
            if ((method.name == "getValue")) {
                try {
                    return method.invoke(attribute).toString()
                } catch (e: IllegalAccessException) {
                    throw IllegalArgumentException("Failed to parse @Excludes for: $clazz", e)
                } catch (e: InvocationTargetException) {
                    throw IllegalArgumentException("Failed to parse @Excludes for: $clazz", e)
                }
            }
        }
        throw IllegalArgumentException("Failed to parse @Excludes for: $clazz")
    }

    fun visibleForTesting(): ClassName {
        return findAnnotationClassName(ANDROIDX_VISIBLE_FOR_TESTING, SUPPORT_VISIBLE_FOR_TESTING)
    }

    fun kotlinDeprecated(): ClassName {
        return findAnnotationClassName(JET_BRAINS_DEPRECATED, JET_BRAINS_DEPRECATED)
    }

    fun nonNull(): ClassName {
        return findAnnotationClassName(ANDROIDX_NONNULL_ANNOTATION, SUPPORT_NONNULL_ANNOTATION)
    }

    fun checkResult(): ClassName {
        return findAnnotationClassName(ANDROIDX_CHECK_RESULT_ANNOTATION,
            ANDROIDX_CHECK_RESULT_ANNOTATION)
    }

    fun findAnnotationClassName(androidxName: ClassName, supportName: ClassName): ClassName {
        val symbols = resolver.getSymbolsWithAnnotation(androidxName.reflectionName())
        if (symbols.iterator().hasNext()) {
            return androidxName
        }
        return supportName
    }


    fun isReturnValueTypeMatching(
        method: KSFunctionDeclaration,
        expectedReturnType: KSClassDeclaration,
    ): Boolean {
        return isReturnValueTypeMatching(method, expectedReturnType.asType())
    }

    private fun isReturnValueTypeMatching(
        method: KSFunctionDeclaration, expectedReturnType: KSType,
    ): Boolean {
        return method.returnType?.resolve()?.let { expectedReturnType.isAssignableFrom(it) }
            ?: false
    }

    fun findInstanceMethodsReturning(
        ksClassDeclaration: KSClassDeclaration,
        ksType: KSType,
    ): List<KSFunctionDeclaration> {
        return ksClassDeclaration.declarations.filterNotNull().filter {
            it is KSFunctionDeclaration && it.functionKind != FunctionKind.STATIC && isReturnValueTypeMatching(
                it,
                ksType) && !it.isConstructor() && it.isPublic()
        }.map { it as KSFunctionDeclaration }.toList()
    }

    fun findStaticMethodsReturning(
        ksClassDeclaration: KSClassDeclaration,
        ksType: KSType,
    ): List<KSFunctionDeclaration> {
        return ksClassDeclaration.declarations.filterNotNull().filter {
            it is KSFunctionDeclaration && it.functionKind == FunctionKind.STATIC && isReturnValueTypeMatching(
                it,
                ksType) && !it.isConstructor() && it.isPublic()
        }.map { it as KSFunctionDeclaration }.toList()
    }

    /**
     * in kotlin object class and java  instance class
     */
    fun findStaticMethods(ksClassDeclaration: KSClassDeclaration): List<KSFunctionDeclaration> {
        return ksClassDeclaration.declarations.filter {
            it is KSFunctionDeclaration && !it.isConstructor() && it.functionKind == FunctionKind.STATIC && it.isPublic()
        }.map { it as KSFunctionDeclaration }.toList()
    }


    companion object {

        fun generateCastingSuperCall(toReturn: TypeName, method: FunSpec): CodeBlock {
            return CodeBlock.builder()
                .add("return super.%N(", method.name)
                .add(FluentIterable.from(method.parameters).transform {
                    it!!.name
                }.join(Joiner.on(",")))
                .add(")")
                .add(" as %T\n", toReturn)
                .build()
        }

    }

    fun nonNulls(): List<ClassName> {
        return listOf(SUPPORT_NONNULL_ANNOTATION,
            JETBRAINS_NOTNULL_ANNOTATION,
            ANDROIDX_NONNULL_ANNOTATION)
    }

    /**
     * log functions
     */
    fun debugLog(toLog: String) {
        if (DEBUG) {
            infoLog(toLog)
        }
    }

    fun infoLog(toLog: String) {
        environment.logger.warn("round[$_round]:$toLog")
    }

}