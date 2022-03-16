package com.mistletoe.gilde.ksp.buildsrc

/**
 * Created by mistletoe
 * on 2022/1/12
 **/

object Repos {
    const val jitpack = "https://jitpack.io"
}

object Dependencies {
    const val androidMavenGradlePlugin = "com.github.dcendents:android-maven-gradle-plugin:2.1"
    const val androidGradlePlugin = "com.android.tools.build:gradle:${Versions.agp}"
    const val glide_annotation = "com.github.bumptech.glide:annotations:${Versions.glide}"
    const val glide_core = "com.github.bumptech.glide:glide:${Versions.glide}"
    const val glide_okhttp3_integration =
        "com.github.bumptech.glide:okhttp3-integration:${Versions.glide}"
    const val glide_transformations =
        "jp.wasabeef:glide-transformations:${Versions.glide_transform}"
}

object GlideKSP {
    const val debug_mode = true
    const val core = "com.mistletoe:glide-ksp:0.0.1"
}

object Kotlin {
    const val gradle_plugin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}"
    const val std = "org.jetbrains.kotlin:kotlin-stdlib:${Versions.kotlin}"
    const val kcp_embeddable = "org.jetbrains.kotlin:kotlin-compiler-embeddable:${Versions.kotlin}"
}

object Square {
    const val kotlin_poet = "com.squareup:kotlinpoet:${Versions.kotlin_poet}"
}

object Test {
    const val junit = "junit:junit:4.+"
    const val android_test_ext = "androidx.test.ext:junit:1.1.2"
    const val android_test_espresso = "androidx.test.espresso:espresso-core:3.3.0"
}

object Google {
    const val ksp = "com.google.devtools.ksp:symbol-processing-api:${Versions.ksp}"
}

object UIKit {
    const val material = "com.google.android.material:material:1.3.0"
    const val constraint_layout = "androidx.constraintlayout:constraintlayout:2.0.4"
}

object AndroidX {
    const val core = "androidx.core:core:1.5.0"
    const val core_ktx = "androidx.core:core-ktx:1.3.2"
    const val appcompat = "androidx.appcompat:appcompat:1.2.0"
    const val lifecycle_extensions = "androidx.lifecycle:lifecycle-extensions:2.2.0"
}

object Versions {
    const val compileSdkVersion = 30
    const val minSdkVersion = 23
    const val targetSdkVersion = 30
    const val kotlin = "1.6.10"
    const val agp = "4.1.3"
    const val glide = "4.12.0"
    const val glide_transform = "4.3.0"
    const val kotlin_poet = "1.2.0"
    const val ksp = "1.6.10-1.0.2"
}
