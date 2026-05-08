plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
    application
}

dependencies {
    implementation(project(":mcp-imp"))

    implementation(libs.mcpKotlinSdk)
    implementation(libs.kotlinxCoroutines)
    implementation(libs.kotlinxSerializationJson)

    implementation(libs.ktorServerCore)
    implementation(libs.ktorServerCio)
    implementation(libs.ktorServerSse)
    implementation(libs.ktorServerContentNegotiation)
    implementation(libs.ktorClientCore)
    implementation(libs.ktorClientCio)
    implementation(libs.ktorClientContentNegotiation)
    implementation(libs.ktorSerializationKotlinxJson)

    implementation(libs.kotlinLogging)
    runtimeOnly(libs.logbackClassic)

    testImplementation(libs.junitJupiter)
    testImplementation(libs.kotestAssertionsCore)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

application {
    mainClass = "io.knowledgeos.MainKt"
    applicationDefaultJvmArgs = listOf(
        "--enable-preview",
        "--enable-native-access=ALL-UNNAMED",
        "--add-modules=jdk.incubator.vector"
    )
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "io.knowledgeos.MainKt"
        attributes["Multi-Release"] = "true"
    }
}
