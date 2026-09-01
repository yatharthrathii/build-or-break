# Build or Break - R8 rules
#
# techspec.md section 9. R8 full mode is on. Keep this file small: every keep
# rule is code that cannot be shrunk, and rules.md section 5 budgets the release
# APK at under 12 MB.
#
# Before adding a rule here, check whether the library ships its own consumer
# rules. Most modern AndroidX and Kotlin libraries do.

# kotlinx.serialization. Keeps generated serializers reachable.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Navigation 3 route types are serializable objects resolved by type.
-keep,allowobfuscation,allowshrinking class com.buildorbreak.app.navigation.** { *; }

# Room. Entities are constructed reflectively by generated code.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Keep line numbers so a crash report from an opted in user is readable.
# techspec.md keeps crash reporting opt in, but when it is on it needs to be
# useful.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
