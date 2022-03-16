package com.mistletoe.glide.ksp.compiler

import android.content.Context
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule

/**
 * @brief
 * @author mistletoe
 * @date 2022/3/2
 **/
@GlideModule
class DemoAppGlideModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        super.registerComponents(context, glide, registry)
        //just do nothing
        Log.d("Mistletoe","Demo AppGlideModule Stub Class !!!")
    }
}