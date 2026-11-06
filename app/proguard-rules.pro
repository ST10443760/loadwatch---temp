# R8 rules for the LoadWatch release build.
#
# Retrofit, OkHttp, kotlinx serialization, Room, Hilt, WorkManager and Firebase ship their own consumer rules in
# their AARs, which R8 applies automatically. This file only adds what those rules do not cover.

# Readable stack traces in crash reports, without exposing the original file names
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Retrofit reads the generic return types of the LoadWatchApi suspend functions at runtime, such as
# List<AreaSummaryDto>, so their signatures and annotations must survive R8 full mode
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keep,allowobfuscation,allowshrinking interface com.example.loadwatch.core.network.LoadWatchApi
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# kotlinx serialization: keep the generated serializers of the DTOs and of the type-safe navigation routes, which
# Navigation looks up from the route class at runtime
-keepclassmembers @kotlinx.serialization.Serializable class com.example.loadwatch.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.example.loadwatch.**$$serializer { *; }

# Credential Manager loads its Google Play services provider by reflection (Google sign-in)
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }

# The sync worker is created by WorkManager from its class name
-keep class com.example.loadwatch.data.sync.SyncWorker { public <init>(android.content.Context, androidx.work.WorkerParameters); }
