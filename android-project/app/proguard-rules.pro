# LocalMind ProGuard / R8 Rules
# Keep Kotlinx Serialization generated serializers for model manifests, personas, and RAG metadata
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class app.localmind.assistant.**$$serializer { *; }
-keepclassmembers class app.localmind.assistant.** {
    *** Companion;
}
-keepclasseswithmembers class app.localmind.assistant.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Google AI Edge / LiteRT-LM / MediaPipe JNI bindings
-keep class com.google.mediapipe.** { *; }
-keepclassmembers class com.google.mediapipe.** { *; }

# Keep Room entities & DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Strip verbose debug logs in release builds to prevent accidental leakage
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
