# kotlinx.serialization: згенеровані серіалізатори моделей даних
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class ua.x44to.kopiyka.**$$serializer { *; }
-keepclassmembers class ua.x44to.kopiyka.** { *** Companion; }
-keepclasseswithmembers class ua.x44to.kopiyka.** { kotlinx.serialization.KSerializer serializer(...); }
