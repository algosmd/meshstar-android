plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(21) }
java { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
}
dependencies {
    implementation(project(":rnslite"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
tasks.test { useJUnit() }
