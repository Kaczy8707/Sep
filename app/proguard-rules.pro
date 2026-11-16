# kotlinx.serialization – modele treści
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class pl.septrener.content.**$$serializer { *; }
-keepclassmembers class pl.septrener.content.** { *** Companion; }
-keepclasseswithmembers class pl.septrener.content.** { kotlinx.serialization.KSerializer serializer(...); }
