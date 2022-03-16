package com.mistletoe.glide.ksp.core

import com.google.devtools.ksp.*
import com.google.devtools.ksp.ExceptionMessage
import com.google.devtools.ksp.findActualType
import com.google.devtools.ksp.isLocal
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.symbol.ClassKind.CLASS
import com.google.devtools.ksp.symbol.Origin.KOTLIN
import com.google.devtools.ksp.symbol.Visibility.*
import com.google.devtools.ksp.visitor.KSValidateVisitor
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.jetbrains.kotlin.com.google.common.base.Joiner
import org.jetbrains.kotlin.com.google.common.collect.FluentIterable
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import javax.lang.model.element.TypeElement
import kotlin.reflect.KClass

/**
 * Created by mistletoe
 * on 2021/11/11
 **/


fun String.toAbsoluteName() = replace('.', '/')

internal fun String.toClassName(): ClassName {
    val packageIndex = lastIndexOf(".")
    val packageName = if (packageIndex >= 0) {
        substring(0, packageIndex)
    } else {
        ""
    }
    val names = substring(packageIndex + 1).split("$")
    require(names.isNotEmpty() && names.none {
        it.isEmpty()
    }) {
        throw IllegalArgumentException("Illegal class name: $this")
    }
    return ClassName(packageName, names[0], *names.subList(1, names.size).toTypedArray())
}

internal fun KSType.toClassName(): ClassName {
    check(declaration is KSClassDeclaration)
    return (declaration as KSClassDeclaration).toClassName()
}

internal fun KSValueParameter.getParameterTypeName(
    environment: SymbolProcessorEnvironment,
    nullableType: KSType,
): TypeName {
    val originParentType = type.resolve()
    val isNullable = annotations.any { a -> a.annotationType.resolve() == nullableType }
    //is type variable
    val realType = if (originParentType.declaration !is KSClassDeclaration) {
        val origin = TypeVariableName(originParentType.toString())
        origin.copy(nullable = isNullable)
    } else {
        //1.just a specific class  type  2. class with type variable
        val origin = type.element?.let { typeArgumentElement ->
            if (typeArgumentElement.typeArguments.isNotEmpty()) {
                //find varargs
                if (originParentType.toClassName() == ClassName("kotlin", "Array")) {
                    if (originParentType.innerArguments.firstOrNull() == null) {
                        TypeVariableName(originParentType.declaration.simpleName.asString())
                    } else {
                        val args = FluentIterable.from(originParentType.innerArguments)
                            .transform {
                                if (it!!.variance == Variance.STAR) {
                                    it.variance.label
                                } else {
                                    if (it.type?.resolve()?.innerArguments?.firstOrNull() == null) {
                                        TypeVariableName(it.type?.resolve()?.declaration?.simpleName?.asString()!!)
                                    } else {
                                        val args =
                                            FluentIterable.from(it.type?.resolve()?.innerArguments!!)
                                                .transform { kst ->
                                                    when (kst!!.variance) {
                                                        Variance.STAR -> {
                                                            kst.variance.label
                                                        }
                                                        else -> {
                                                            kst.type.toString()
                                                        }
                                                    }
                                                }.join(Joiner.on(","))
                                        TypeVariableName(it.type!!.resolve().declaration.simpleName.asString() + "<" + args + ">")
                                    }
                                }
                            }.join(Joiner.on(","))
                        TypeVariableName(args)
                    }
                } else {
                    originParentType.toClassName()
                        .parameterizedBy(*typeArgumentElement.typeArguments.map { t ->
                            val innerArgument = t.type?.resolve()
                            if (innerArgument !is KSClassDeclaration) {
                                if (innerArgument!!.innerArguments.firstOrNull() == null) {
                                    if ("Any" == innerArgument.declaration.simpleName.asString() && originParentType.toClassName() == Class::class.asClassName()) {
                                        TypeVariableName(Variance.STAR.label)
                                    } else {
                                        TypeVariableName(innerArgument.declaration.simpleName.asString())
                                    }
                                } else {
                                    //avoid to meet same type variable
                                    val args =
                                        FluentIterable.from(FluentIterable.from(innerArgument.innerArguments)
                                            .transform {
                                                when (it!!.variance) {
                                                    Variance.STAR -> {
                                                        it.variance.label
                                                    }
                                                    else -> {
                                                        it.type.toString()
                                                    }
                                                }
                                            }.toSet()).join(Joiner.on(","))
                                    environment.logger.warn("originParentType:${originParentType}")
                                    TypeVariableName(args)
                                }
                            } else {
                                (innerArgument as KSClassDeclaration).toClassName()
                            }
                        }.toTypedArray())
                }
            } else {
                originParentType.toClassName()
            }
        } ?: run {
            originParentType.toClassName()
        }
        origin.copy(nullable = isNullable)
    }
    return realType
}


internal fun KSClassDeclaration.toClassName(): ClassName {
    require(!isLocal()) {
        "Local/anonymous classes are not supported!"
    }
    val pkgName = packageName.asString()
    val typesString = qualifiedName!!.asString().removePrefix("$pkgName.")
    val simpleNames = typesString
        .split(".")
    return ClassName(
        pkgName,
        simpleNames[0],
        *simpleNames.subList(1, simpleNames.size).toTypedArray()
    )
}


internal fun KSClassDeclaration.asType() = asType(emptyList())

internal fun KSClassDeclaration.superclass(resolver: Resolver): KSType {
    return getAllSuperTypes().firstOrNull {
        val decl = it.declaration
        decl is KSClassDeclaration && decl.classKind == CLASS
    } ?: resolver.builtIns.anyType
}

internal fun KSClassDeclaration.isKotlinClass(resolver: Resolver): Boolean {
    return origin == KOTLIN ||
            resolver.getClassDeclarationByName<Metadata>()
                ?.let { hasAnnotation(it.asType()) } == true
}

internal fun KSAnnotated.hasAnnotation(target: KSType): Boolean {
    return findAnnotationWithType(target) != null
}

internal inline fun <reified T : Annotation> KSAnnotated.findAnnotationWithType(
    resolver: Resolver,
): KSAnnotation? {
    return resolver.getClassDeclarationByName<T>()?.let { findAnnotationWithType(it.asType()) }
}

internal fun KSAnnotated.findAnnotationWithType(target: KSType): KSAnnotation? {
    return annotations.find { it.annotationType.resolve() == target }
}

internal inline fun <reified T> KSAnnotation.getMember(name: String): T {
    val matchingArg = arguments.find { it.name?.asString() == name }
        ?: error(
            "No member name found for '$name'. All arguments: ${arguments.map { it.name?.asString() }}"
        )
    return when (val argValue = matchingArg.value) {
        is List<*> -> {
            if (argValue.isEmpty()) {
                argValue as T
            } else {
                val first = argValue[0]
                if (first is KSType) {
                    argValue.map { (it as KSType).toClassName() } as T
                } else {
                    argValue as T
                }
            }
        }
        is KSType -> argValue.toClassName() as T
        else -> {
            argValue as? T ?: error("No value found for $name. Was ${matchingArg.value}")
        }
    }
}


/**
 * TODO:FIXME
 */
internal fun KSAnnotation.getMemberKSTypeList(name: String): List<KSType> {
    val matchingArg = arguments.find { it.name?.asString() == name }
        ?: error(
            "No member name found for '$name'. All arguments: ${arguments.map { it.name?.asString() }}"
        )
    return when (val argValue = matchingArg.value) {
        is List<*> -> {
            if (argValue.isEmpty()) {
                argValue as List<KSType>
            } else {
                val first = argValue[0]
                if (first is KSType) {
                    argValue.map { (it as KSType) }
                } else {
                    argValue as List<KSType>
                }
            }
        }
        is KSType -> listOf(argValue)
        else -> {
            argValue as? List<KSType> ?: error("No value found for $name. Was ${matchingArg.value}")
        }
    }
}

internal fun KSAnnotation.getMemberKSType(name: String): KSType {
    val matchingArg = arguments.find { it.name?.asString() == name }
        ?: error(
            "No member name found for '$name'. All arguments: ${arguments.map { it.name?.asString() }}"
        )
    return when (val argValue = matchingArg.value) {
        is KSType -> argValue
        else -> {
            argValue as? KSType ?: error("No value found for $name. Was ${matchingArg.value}")
        }
    }
}

internal fun Visibility.asKModifier(): KModifier {
    return when (this) {
        PUBLIC -> KModifier.PUBLIC
        PRIVATE -> KModifier.PRIVATE
        PROTECTED -> KModifier.PROTECTED
        INTERNAL -> KModifier.INTERNAL
        JAVA_PACKAGE -> KModifier.PUBLIC
        LOCAL -> error("Local is unsupported")
    }
}

//following copy from ksp

/**
 * get all super types for a class declaration
 * Calling [getAllSuperTypes] requires type resolution therefore is expensive and should be avoided if possible.
 */
internal fun KSClassDeclaration.getAllSuperTypes(): Sequence<KSType> {

    fun KSTypeParameter.getTypesUpperBound(): Sequence<KSClassDeclaration> =
        this.bounds.asSequence().flatMap {
            when (val resolvedDeclaration = it.resolve().declaration) {
                is KSClassDeclaration -> sequenceOf(resolvedDeclaration)
                is KSTypeAlias -> sequenceOf(resolvedDeclaration.findActualType())
                is KSTypeParameter -> resolvedDeclaration.getTypesUpperBound()
                else -> throw IllegalStateException("unhandled type parameter bound, $ExceptionMessage")
            }
        }

    return this.superTypes
        .asSequence()
        .map { it.resolve() }
        .plus(
            this.superTypes
                .asSequence()
                .mapNotNull { it.resolve().declaration }
                .flatMap {
                    when (it) {
                        is KSClassDeclaration -> it.getAllSuperTypes()
                        is KSTypeAlias -> it.findActualType().getAllSuperTypes()
                        is KSTypeParameter -> it.getTypesUpperBound()
                            .flatMap { it.getAllSuperTypes() }
                        else -> throw IllegalStateException("unhandled super type kind, $ExceptionMessage")
                    }
                }
        )
        .distinct()
}

internal val TypeElement.javaClassName: String
    get() = asClassName().run {
        this.packageName + "." + this.simpleNames.joinToString(
            separator = "$"
        )
    }

internal fun KSType.asClass() = try {
    Class.forName(this.declaration.qualifiedName!!.asString())
} catch (e: Exception) {
    throw KSTypeNotPresentException(this, e)
}

class KSTypeNotPresentException(val ksType: KSType, cause: Throwable) : RuntimeException(cause)

/**
 * Try to resolve the [KSClassDeclaration] for a class using its fully qualified name.
 *
 * @param T The class to resolve a [KSClassDeclaration] for.
 * @return Resolved [KSClassDeclaration] if found, `null` otherwise.
 *
 * @see [Resolver.getClassDeclarationByName]
 */
inline fun <reified T> Resolver.getClassDeclarationByName(): KSClassDeclaration? {
    return T::class.qualifiedName?.let { fqcn ->
        getClassDeclarationByName(getKSNameFromString(fqcn))
    }
}

/**
 * Find a class in the compilation classpath for the given name.
 *
 * @param name fully qualified name of the class to be loaded; using '.' as separator.
 * @return a KSClassDeclaration, or null if not found.
 */
fun Resolver.getClassDeclarationByName(name: String): KSClassDeclaration? =
    getClassDeclarationByName(getKSNameFromString(name))

/**
 * Find functions in the compilation classpath for the given name.
 *
 * @param name fully qualified name of the function to be loaded; using '.' as separator.
 * @param includeTopLevel a boolean value indicate if top level functions should be searched. Default false. Note if top level functions are included, this operation can be expensive.
 * @return a Sequence of KSFunctionDeclaration.
 */
fun Resolver.getFunctionDeclarationsByName(
    name: String,
    includeTopLevel: Boolean = false,
): Sequence<KSFunctionDeclaration> =
    getFunctionDeclarationsByName(getKSNameFromString(name), includeTopLevel)

/**
 * Find a property in the compilation classpath for the given name.
 *
 * @param name fully qualified name of the property to be loaded; using '.' as separator.
 * @param includeTopLevel a boolean value indicate if top level properties should be searched. Default false. Note if top level properties are included, this operation can be expensive.
 * @return a KSPropertyDeclaration, or null if not found.
 */
fun Resolver.getPropertyDeclarationByName(
    name: String,
    includeTopLevel: Boolean = false,
): KSPropertyDeclaration? =
    getPropertyDeclarationByName(getKSNameFromString(name), includeTopLevel)

/**
 * Get functions directly declared inside the class declaration.
 *
 * What are included: member functions, constructors, extension functions declared inside it, etc.
 * What are NOT included: inherited functions, extension functions declared outside it.
 */
fun KSClassDeclaration.getDeclaredFunctions(): Sequence<KSFunctionDeclaration> {
    return this.declarations.filterIsInstance<KSFunctionDeclaration>()
}

/**
 * Get properties directly declared inside the class declaration.
 *
 * What are included: member properties, extension properties declared inside it, etc.
 * What are NOT included: inherited properties, extension properties declared outside it.
 */
fun KSClassDeclaration.getDeclaredProperties(): Sequence<KSPropertyDeclaration> {
    return this.declarations.filterIsInstance<KSPropertyDeclaration>()
}

fun KSClassDeclaration.getConstructors(): Sequence<KSFunctionDeclaration> {
    return getDeclaredFunctions().filter {
        it.isConstructor()
    }
}

/**
 * Check whether this is a local declaration, or namely, declared in a function.
 */
fun KSDeclaration.isLocal(): Boolean {
    return this.parentDeclaration != null && this.parentDeclaration !is KSClassDeclaration
}

/**
 * Perform a validation on a given symbol to check if all interested types in symbols enclosed scope are valid, i.e. resolvable.
 * @param predicate: A lambda for filtering interested symbols for performance purpose. Default checks all.
 */
fun KSNode.validate(predicate: (KSNode?, KSNode) -> Boolean = { _, _ -> true }): Boolean {
    return this.accept(KSValidateVisitor(predicate), null)
}

/**
 * Find the KSClassDeclaration that the alias points to recursively.
 */
fun KSTypeAlias.findActualType(): KSClassDeclaration {
    val resolvedType = this.type.resolve().declaration
    return if (resolvedType is KSTypeAlias) {
        resolvedType.findActualType()
    } else {
        resolvedType as KSClassDeclaration
    }
}

/**
 * Determine [Visibility] of a [KSDeclaration].
 */
fun KSDeclaration.getVisibility(): Visibility {
    return when {
        this.modifiers.contains(Modifier.PUBLIC) -> Visibility.PUBLIC
        this.modifiers.contains(Modifier.OVERRIDE) -> {
            when (this) {
                is KSFunctionDeclaration -> this.findOverridee()?.getVisibility()
                is KSPropertyDeclaration -> this.findOverridee()?.getVisibility()
                else -> null
            } ?: Visibility.PUBLIC
        }
        this.isLocal() -> Visibility.LOCAL
        this.modifiers.contains(Modifier.PRIVATE) -> Visibility.PRIVATE
        this.modifiers.contains(Modifier.PROTECTED) ||
                this.modifiers.contains(Modifier.OVERRIDE) -> Visibility.PROTECTED
        this.modifiers.contains(Modifier.INTERNAL) -> Visibility.INTERNAL
        else -> if (this.origin != Origin.JAVA && this.origin != Origin.JAVA_LIB)
            Visibility.PUBLIC else Visibility.JAVA_PACKAGE
    }
}

fun KSClassDeclaration.isAbstract() =
    this.classKind == ClassKind.INTERFACE || this.modifiers.contains(Modifier.ABSTRACT)

fun KSPropertyDeclaration.isAbstract(): Boolean {
    if (modifiers.contains(Modifier.ABSTRACT)) {
        return true
    }
    val parentClass = parentDeclaration as? KSClassDeclaration ?: return false
    if (parentClass.classKind != ClassKind.INTERFACE) return false
    // this is abstract if it does not have setter/getter or setter/getter have abstract modifiers
    return (getter?.modifiers?.contains(Modifier.ABSTRACT) ?: true) &&
            (setter?.modifiers?.contains(Modifier.ABSTRACT) ?: true)
}

fun KSDeclaration.isOpen() = !this.isLocal() &&
        (
                (this as? KSClassDeclaration)?.classKind == ClassKind.INTERFACE ||
                        this.modifiers.contains(Modifier.OVERRIDE) ||
                        this.modifiers.contains(Modifier.ABSTRACT) ||
                        this.modifiers.contains(Modifier.OPEN) ||
                        (this.parentDeclaration as? KSClassDeclaration)?.classKind == ClassKind.INTERFACE ||
                        (!this.modifiers.contains(Modifier.FINAL) && this.origin == Origin.JAVA)
                )

fun KSDeclaration.isPublic() = this.getVisibility() == Visibility.PUBLIC

fun KSDeclaration.isProtected() = this.getVisibility() == Visibility.PROTECTED

fun KSDeclaration.isInternal() = this.modifiers.contains(Modifier.INTERNAL)

fun KSDeclaration.isPrivate() = this.modifiers.contains(Modifier.PRIVATE)

fun KSDeclaration.isJavaPackagePrivate() = this.getVisibility() == Visibility.JAVA_PACKAGE

fun KSDeclaration.closestClassDeclaration(): KSClassDeclaration? {
    if (this is KSClassDeclaration) {
        return this
    } else {
        return this.parentDeclaration?.closestClassDeclaration()
    }
}

// TODO: cross module visibility is not handled
fun KSDeclaration.isVisibleFrom(other: KSDeclaration): Boolean {
    fun KSDeclaration.isSamePackage(other: KSDeclaration): Boolean =
        this.packageName == other.packageName

    // lexical scope for local declaration.
    fun KSDeclaration.parentDeclarationsForLocal(): List<KSDeclaration> {
        val parents = mutableListOf<KSDeclaration>()

        var parentDeclaration = this.parentDeclaration!!

        while (parentDeclaration.isLocal()) {
            parents.add(parentDeclaration)
            parentDeclaration = parentDeclaration.parentDeclaration!!
        }

        parents.add(parentDeclaration)

        return parents
    }

    fun KSDeclaration.isVisibleInPrivate(other: KSDeclaration) =
        (other.isLocal() && other.parentDeclarationsForLocal().contains(this.parentDeclaration)) ||
                this.parentDeclaration == other.parentDeclaration ||
                this.parentDeclaration == other || (
                this.parentDeclaration == null &&
                        other.parentDeclaration == null &&
                        this.containingFile == other.containingFile
                )

    return when {
        // locals are limited to lexical scope
        this.isLocal() -> this.parentDeclarationsForLocal().contains(other)
        // file visibility or member
        // TODO: address nested class.
        this.isPrivate() -> this.isVisibleInPrivate(other)
        this.isPublic() -> true
        this.isInternal() && other.containingFile != null && this.containingFile != null -> true
        this.isJavaPackagePrivate() -> this.isSamePackage(other)
        this.isProtected() -> this.isVisibleInPrivate(other) || this.isSamePackage(other) ||
                other.closestClassDeclaration()?.let {
                    this.closestClassDeclaration()!!.asStarProjectedType()
                        .isAssignableFrom(it.asStarProjectedType())
                } ?: false
        else -> false
    }
}

/**
 * Returns `true` if this is a constructor function.
 */
fun KSFunctionDeclaration.isConstructor() = this.simpleName.asString() == "<init>"

const val ExceptionMessage = "please file a bug at https://github.com/google/ksp/issues/new"

val KSType.outerType: KSType?
    get() {
        if (Modifier.INNER !in declaration.modifiers)
            return null
        val outerDecl = declaration.parentDeclaration as? KSClassDeclaration ?: return null
        return outerDecl.asType(arguments.subList(declaration.typeParameters.size, arguments.size))
    }

val KSType.innerArguments: List<KSTypeArgument>
    get() = arguments.subList(0, declaration.typeParameters.size)


@KspExperimental
fun Resolver.getKotlinClassByName(name: KSName): KSClassDeclaration? {
    val kotlinName = mapJavaNameToKotlin(name) ?: name
    return getClassDeclarationByName(kotlinName)
}


@KspExperimental
fun Resolver.getKotlinClassByName(name: String): KSClassDeclaration? =
    getKotlinClassByName(getKSNameFromString(name))


@KspExperimental
fun Resolver.getJavaClassByName(name: KSName): KSClassDeclaration? {
    val javaName = mapKotlinNameToJava(name) ?: name
    return getClassDeclarationByName(javaName)
}


@KspExperimental
fun Resolver.getJavaClassByName(name: String): KSClassDeclaration? =
    getJavaClassByName(getKSNameFromString(name))


@KspExperimental
fun <T : Annotation> KSAnnotated.getAnnotationsByType(annotationKClass: KClass<T>): Sequence<T> {
    return this.annotations.filter {
        it.shortName.getShortName() == annotationKClass.simpleName && it.annotationType.resolve().declaration
            .qualifiedName?.asString() == annotationKClass.qualifiedName
    }.map { it.toAnnotation(annotationKClass.java) }
}


@KspExperimental
fun <T : Annotation> KSAnnotated.isAnnotationPresent(annotationKClass: KClass<T>): Boolean =
    getAnnotationsByType(annotationKClass).firstOrNull() != null


@KspExperimental
@Suppress("UNCHECKED_CAST")
private fun <T : Annotation> KSAnnotation.toAnnotation(annotationClass: Class<T>): T {
    return Proxy.newProxyInstance(
        annotationClass.classLoader,
        arrayOf(annotationClass),
        createInvocationHandler(annotationClass)
    ) as T
}


@KspExperimental
@Suppress("TooGenericExceptionCaught")
private fun KSAnnotation.createInvocationHandler(clazz: Class<*>): InvocationHandler {
    val cache = ConcurrentHashMap<Pair<Class<*>, Any>, Any>(arguments.size)
    return InvocationHandler { proxy, method, _ ->
        if (method.name == "toString" && arguments.none { it.name?.asString() == "toString" }) {
            clazz.canonicalName +
                    arguments.map { argument: KSValueArgument ->
                        // handles default values for enums otherwise returns null
                        val methodName = argument.name?.asString()
                        val value = proxy.javaClass.methods.find { m -> m.name == methodName }
                            ?.invoke(proxy)
                        "$methodName=$value"
                    }.toList()
        } else {
            val argument = arguments.first { it.name?.asString() == method.name }
            when (val result = argument.value ?: method.defaultValue) {
                is Proxy -> result
                is List<*> -> {
                    val value = { result.asArray(method) }
                    cache.getOrPut(Pair(method.returnType, result), value)
                }
                else -> {
                    when {
                        method.returnType.isEnum -> {
                            val value = { result.asEnum(method.returnType) }
                            cache.getOrPut(Pair(method.returnType, result), value)
                        }
                        method.returnType.isAnnotation -> {
                            val value = { (result as KSAnnotation).asAnnotation(method.returnType) }
                            cache.getOrPut(Pair(method.returnType, result), value)
                        }
                        method.returnType.name == "java.lang.Class" -> {
                            cache.getOrPut(Pair(method.returnType, result)) {
                                when (result) {
                                    is KSType -> result.asClass()
                                    // Handles com.intellij.psi.impl.source.PsiImmediateClassType using reflection
                                    // since api doesn't contain a reference to this
                                    else -> Class.forName(
                                        result.javaClass.methods
                                            .first { it.name == "getCanonicalText" }
                                            .invoke(result, false) as String
                                    )
                                }
                            }
                        }
                        method.returnType.name == "byte" -> {
                            val value = { result.asByte() }
                            cache.getOrPut(Pair(method.returnType, result), value)
                        }
                        method.returnType.name == "short" -> {
                            val value = { result.asShort() }
                            cache.getOrPut(Pair(method.returnType, result), value)
                        }
                        method.returnType.name == "long" -> {
                            val value = { result.asLong() }
                            cache.getOrPut(Pair(method.returnType, result), value)
                        }
                        method.returnType.name == "float" -> {
                            val value = { result.asFloat() }
                            cache.getOrPut(Pair(method.returnType, result), value)
                        }
                        method.returnType.name == "double" -> {
                            val value = { result.asDouble() }
                            cache.getOrPut(Pair(method.returnType, result), value)
                        }
                        else -> result // original value
                    }
                }
            }
        }
    }
}


@KspExperimental
@Suppress("UNCHECKED_CAST")
private fun KSAnnotation.asAnnotation(
    annotationInterface: Class<*>,
): Any {
    return Proxy.newProxyInstance(
        annotationInterface.classLoader, arrayOf(annotationInterface),
        this.createInvocationHandler(annotationInterface)
    ) as Proxy
}


@KspExperimental
@Suppress("UNCHECKED_CAST")
private fun List<*>.asArray(method: Method) =
    when (method.returnType.componentType.name) {
        "boolean" -> (this as List<Boolean>).toBooleanArray()
        "byte" -> (this as List<Byte>).toByteArray()
        "short" -> (this as List<Short>).toShortArray()
        "char" -> (this as List<Char>).toCharArray()
        "double" -> (this as List<Double>).toDoubleArray()
        "float" -> (this as List<Float>).toFloatArray()
        "int" -> (this as List<Int>).toIntArray()
        "long" -> (this as List<Long>).toLongArray()
        "java.lang.Class" -> (this as List<KSType>).asClasses().toTypedArray()
        "java.lang.String" -> (this as List<String>).toTypedArray()
        else -> { // arrays of enums or annotations
            when {
                method.returnType.componentType.isEnum -> {
                    this.toArray(method) { result -> result.asEnum(method.returnType.componentType) }
                }
                method.returnType.componentType.isAnnotation -> {
                    this.toArray(method) { result ->
                        (result as KSAnnotation).asAnnotation(method.returnType.componentType)
                    }
                }
                else -> throw IllegalStateException("Unable to process type ${method.returnType.componentType.name}")
            }
        }
    }

@Suppress("UNCHECKED_CAST")
private fun List<*>.toArray(method: Method, valueProvider: (Any) -> Any): Array<Any?> {
    val array: Array<Any?> = java.lang.reflect.Array.newInstance(
        method.returnType.componentType,
        this.size
    ) as Array<Any?>
    for (r in this.indices) {
        array[r] = this[r]?.let { valueProvider.invoke(it) }
    }
    return array
}

@Suppress("UNCHECKED_CAST")
private fun <T> Any.asEnum(returnType: Class<T>): T =
    returnType.getDeclaredMethod("valueOf", String::class.java)
        .invoke(
            null,
            if (this is KSType) {
                this.declaration.simpleName.getShortName()
            } else {
                this.toString()
            }
        ) as T

private fun Any.asByte(): Byte = if (this is Int) this.toByte() else this as Byte

private fun Any.asShort(): Short = if (this is Int) this.toShort() else this as Short

private fun Any.asLong(): Long = if (this is Int) this.toLong() else this as Long

private fun Any.asFloat(): Float = if (this is Int) this.toFloat() else this as Float

private fun Any.asDouble(): Double = if (this is Int) this.toDouble() else this as Double

@KspExperimental
private fun List<KSType>.asClasses() = try {
    this.map(KSType::asClass)
} catch (e: Exception) {
    throw KSTypesNotPresentException(this, e)
}

class KSTypesNotPresentException(val ksTypes: List<KSType>, cause: Throwable) :
    RuntimeException(cause)
