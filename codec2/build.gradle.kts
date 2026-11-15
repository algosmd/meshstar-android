plugins { alias(libs.plugins.android.library) }

val prepareCodec = tasks.register<Exec>("prepareCodec") {
    inputs.files("prepare.py", "upstream/codec2-1.2.0.tar.gz")
    outputs.dir(layout.buildDirectory.dir("native"))
    commandLine("python3", "prepare.py")
}
android {
    namespace = "net.meshstar.codec2"
    compileSdk = libs.versions.compileSdk.get().toInt()
    ndkVersion = "28.2.13676358"
    defaultConfig {
        minSdk = 26
        externalNativeBuild { cmake { arguments += "-DANDROID_STL=none" } }
        consumerProguardFiles("consumer-rules.pro")
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
}
tasks.configureEach {
    if (name.startsWith("configureCMake") || name.startsWith("buildCMake") || name == "preBuild") dependsOn(prepareCodec)
}
