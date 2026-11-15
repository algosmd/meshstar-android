# Keep only the real analyzer entry point for synthetic camera-frame tests.
-keep class com.bitchat.android.ui.QRCodeAnalyzer {
    <init>(kotlin.jvm.functions.Function1);
    public void analyze(androidx.camera.core.ImageProxy);
}
# CameraX's public frame interfaces are implemented by the synthetic test source.
-keep interface androidx.camera.core.ImageProxy { *; }
-keep interface androidx.camera.core.ImageInfo { *; }

# Kotlin helpers used only by the separate test APK are otherwise removed by R8.
-keep class kotlin.Result** { *; }
-keep class kotlin.io.** { *; }
-keep class net.meshstar.voice.VoiceWav { *; }
-keep,allowoptimization class net.meshstar.identity.EncryptedFiles { *; }
-keep,allowoptimization class net.meshstar.rnslite.Identity { public byte[] exportPrivateKey(); }
-keep class net.meshstar.link.Contact$Companion { *; }
-keep class kotlin.collections.ArraysKt** { *; }
