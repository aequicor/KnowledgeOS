plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(libs.kotlinxCoroutines)
    implementation(libs.kotlinxSerializationJson)

    implementation(libs.bundles.ktorClient)
    implementation(libs.ktorClientLogging)

    implementation(libs.bundles.lucene)

    implementation(libs.kaml)
    implementation(libs.onnxruntime)

    implementation(libs.kotlinLogging)
    runtimeOnly(libs.slf4jSimple)

    testImplementation(libs.junitJupiter)
    testImplementation(libs.kotestAssertionsCore)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
