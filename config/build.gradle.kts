 plugins {
    kotlin("jvm")
    id("com.github.gmazzo.buildconfig") version "5.4.0"
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

val requestedTaskNames = gradle.startParameter.taskNames

val isPackagingBuild = requestedTaskNames.any { taskName ->
    val normalized = taskName.substringAfterLast(':')
    normalized in setOf(
        "packageCurrentOsWithBundledWebRuntime",
        "packageDmg",
        "packageMsi",
        "packageExe",
        "packageRpm",
        "packageDistributionForCurrentOS",
        "createDistributable"
    )
}

val isProVersion = providers.gradleProperty("isProVersion")
    .map(String::toBoolean)
    .orElse(isPackagingBuild)
    .get()

buildConfig {
    useKotlinOutput { topLevelConstants = true }
    useKotlinOutput { internalVisibility = false }   // adds `internal` modifier to all declarations

    buildConfigField("APP_NAME", project.name)
    buildConfigField("APP_VERSION", "${rootProject.extra["app.version"]}")
    buildConfigField("KOTLIN_VERSION", "${rootProject.extra["kotlin.version"]}")
    buildConfigField("COMPOSE_VERSION", "${rootProject.extra["compose.version"]}")
    buildConfigField("IS_PRO_VERSION", isProVersion)
    buildConfigField("BUILD_TIME", System.currentTimeMillis())
}

dependencies {
    testImplementation(kotlin("test"))
    implementation ("org.jetbrains.kotlin:kotlin-stdlib")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:${rootProject.extra["logback"]}")
    implementation("ch.qos.logback:logback-core:${rootProject.extra["logback"]}")
    
    // Gson for JSON serialization
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Domain module (for GeneralSettings)
    implementation(project(":domain"))
    
    // RxCache (for type definitions, instance will be provided by main project)
    implementation("com.github.fengzhizi715.RxCache:core:${rootProject.extra["rxcache"]}")
    implementation("com.github.fengzhizi715.RxCache:extension:${rootProject.extra["rxcache"]}")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
