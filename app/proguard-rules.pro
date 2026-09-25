# FreeScale R8 / ProGuard rules — keep lean; add keeps if release crashes after minify.

-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable

# Compose / Material reflection edges
-dontwarn androidx.compose.**

# Coroutines
-dontwarn kotlinx.coroutines.**

# Vendor body-fat JNI (symbol names tied to this package)
-keep class com.icomon.icbodyfatalgorithms.** { *; }
