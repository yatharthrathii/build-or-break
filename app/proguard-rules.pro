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

# Hilt entry points and ViewModels are found by generated code; the consumer
# rules cover them. The alarm activity is started by action, so nothing in
# the app references it by class and R8 must not drop it.
-keep class com.buildorbreak.app.feature.alarm.AlarmActivity { *; }

# Glance instantiates an action callback by name when somebody taps a widget
# button, so R8 cannot see the constructor being called. Glance ships a consumer
# rule for this; ours is narrower and states the constructor explicitly, because
# a widget button that silently does nothing in release and works in debug is
# the hardest kind of bug to be told about.
-keep class com.buildorbreak.widget.** implements androidx.glance.appwidget.action.ActionCallback {
    <init>();
}
