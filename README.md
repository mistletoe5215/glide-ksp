# glide-ksp [![](https://jitpack.io/v/mistletoe5215/glide-ksp.svg)](https://jitpack.io/#mistletoe5215/glide-ksp)

> glide's ksp compiler ,use kotlin symbol processor

### usage

- import kotlin symbol processor plugin

```groovy
    // in root build.gradle
   plugins {
    id 'com.google.devtools.ksp' version "1.6.10-1.0.2" apply false
   }

```
 - apply kotlin symbol processor plugin in your modules which needs glide's annotation processor

```groovy
   // for example,my demo's application module uses glide's annotation processor
   plugins {
       //...
       id 'com.google.devtools.ksp'
   }
   //add ksp generated class path into sourceSets
   android {
       //...
       sourceSets {
           main {
               java.srcDirs += ['build/generated/ksp/debug/kotlin']
           }
       }
   }
   //add `glide-ksp` using ksp 
   dependencies {
       ksp "com.github.mistletoe5215:glide-ksp:0.0.1"
   }
   

```
 -  find classes  with `@GlideModule`/`@GlideExtension` in library module

 > mark the custom LibraryGlideModules,in java annotation processor or kapt these classes  can be found using `RoundEnvironment` to find class  with `@GlideModule` annotation
 > but now in ksp ,we can't find these classes with using ksp's `Resolver` or `SymbolProcessorEnvironment`.therefore,i can only put these class's qualifiedNames into ksp compile args
 > they're split by "|" separator,use key named `GlideModule`
 > the custom LibraryGlideExtensions,as well,use key named `GlideExtension`


```groovy
    //in application's build.gradle convention scope
   ksp {
       arg( "GlideModule","com.mistletoe.glide.ksp.demo.lib.ProgressLibraryGlideModule|com.bumptech.glide.integration.okhttp3.OkHttpLibraryGlideModule")
   }

```

### checking if Glide-KSP proceed successfully

 - make sure the necessary classes are generated ,they are `GeneratedAppGlideModuleImpl`,`GeneratedRequestManagerFactory`,`GlideApp`,`GlideOption`,`GlideRequest`,`GlideRequests`

![](./snapshots/generated_classes.png)

 - then,call Glide's init in Application, ,set break points in `GeneratedAppGlideModuleImpl`,run app as  debug mode to see whether it will block in these break points
   
 - finally,see if it keeps functioning properly,load image as usual
