# The Breez Spark SDK (breez_sdk_spark:bindings-android) talks to its Rust core
# through JNA (Java Native Access). JNA's native libjnidispatch.so looks up Java
# members reflectively from native code via Native.initIDs() -- e.g.
# com.sun.jna.Native.fromNative(Class, Object) and the fields of every
# com.sun.jna.Structure subclass. R8 (release only; debug has minify disabled)
# renames/strips those members, so the lookup fails at runtime with:
#
#   java.lang.UnsatisfiedLinkError: Can't obtain static method
#     fromNative(Class, Object) from class com.sun.jna.Native
#       at com.sun.jna.Native.initIDs(Native Method)
#
# These keep rules preserve everything JNA and the uniffi-generated Breez
# bindings reach by reflection. Mirrors JNA's own recommended ProGuard config.

-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-dontwarn java.awt.**

# uniffi-generated Breez bindings: JNA Structure subclasses whose field order and
# names are mapped to the native ABI, plus the callback interfaces invoked from Rust.
-keep class breez_sdk_spark.** { *; }
-keepclassmembers class breez_sdk_spark.** { *; }
