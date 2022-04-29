package com.mistletoe.glide.ksp.core

import com.squareup.kotlinpoet.ClassName

/**
 * @brief some constants for writing classes
 * @author mistletoe
 * @date 2022/2/25
 **/
internal const val GLIDE_MODULE_PACKAGE_NAME = "com.bumptech.glide.module"
internal const val APP_GLIDE_MODULE_SIMPLE_NAME = "AppGlideModule"
internal const val LIBRARY_GLIDE_MODULE_SIMPLE_NAME = "LibraryGlideModule"
internal const val APP_GLIDE_MODULE_QUALIFIED_NAME =
    "$GLIDE_MODULE_PACKAGE_NAME.$APP_GLIDE_MODULE_SIMPLE_NAME"
internal const val LIBRARY_GLIDE_MODULE_QUALIFIED_NAME =
    "$GLIDE_MODULE_PACKAGE_NAME.$LIBRARY_GLIDE_MODULE_SIMPLE_NAME"
internal const val COMPILER_PACKAGE_NAME =
    "com.bumptech.glide.annotation.compiler" // GlideAnnotationProcessor.java package name
internal val SUPPORT_NONNULL_ANNOTATION = ClassName("android.support.annotation", "NonNull")
internal val JETBRAINS_NOTNULL_ANNOTATION = ClassName("org.jetbrains.annotations", "NotNull")
internal val ANDROIDX_NONNULL_ANNOTATION = ClassName("androidx.annotation", "NonNull")
internal val SUPPORT_CHECK_RESULT_ANNOTATION =
    ClassName("android.support.annotation", "CheckResult")
internal val ANDROIDX_CHECK_RESULT_ANNOTATION = ClassName("androidx.annotation", "CheckResult")
internal val SUPPORT_VISIBLE_FOR_TESTING =
    ClassName("android.support.annotation", "VisibleForTesting")
internal val ANDROIDX_VISIBLE_FOR_TESTING = ClassName("androidx.annotation", "VisibleForTesting")
internal val JET_BRAINS_DEPRECATED = ClassName("kotlin", "Deprecated")
internal val GLIDE_TRANSITION_OPTION_CLASS_NAME =
    ClassName("com.bumptech.glide", "TransitionOptions")
internal val GLIDE_REQUEST_BUILDER_CLASS_NAME = ClassName("com.bumptech.glide", "RequestBuilder")
internal const val TRANS_CODE_TYPE_NAME = "TranscodeType"
internal val KOTLIN_LIST_CLASS_NAME = ClassName("kotlin.collections", "List")
internal val KOTLIN_MUTABLE_LIST_CLASS_NAME = ClassName("kotlin.collections", "MutableList")
internal const val GLIDE_MODULES_KEY = "GlideModule"
internal const val GLIDE_MODULES_KSP_PACKAGE_NAME = "com.bumptech.glide.ksp"
internal const val GLIDE_EXTENSIONS_KEY = "GlideExtension"