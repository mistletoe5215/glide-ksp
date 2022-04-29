package com.bumptech.glide.ksp

import android.content.Context
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.module.LibraryGlideModule

/**
 * @brief
 * @author mistletoe
 * @date 2022/3/4
 **/
class ProgressLibraryGlideModule : LibraryGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        super.registerComponents(context, glide, registry)
        Log.d("Mistletoe","ProgressLibraryGlideModule Stub Class !!!")
    }
}