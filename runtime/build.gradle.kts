plugins {
    kotlin("jvm")
    id("com.gradleup.shadow")
}

group = "me.albert"
version = rootProject.version

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.rosewooddev.io/repository/public/")
}

dependencies {
    // Paper API 仅编译期需要（服务器自带），不打包
    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")

    // 以下全部 shadow 进 CoreLibRuntime.jar，供 CoreLib 及其它插件运行时共享。
    // 不做 relocate —— 重定位会改变包名，下游插件就找不到这些类了。
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.3.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("com.github.jarod:qqwry-java:0.10.1")
    implementation("com.github.shynixn.mccoroutine:mccoroutine-folia-api:2.22.0")
    implementation("com.github.shynixn.mccoroutine:mccoroutine-folia-core:2.22.0")
}

kotlin {
    jvmToolchain(25)
}

tasks {
    // 让 build 直接产出 shadowJar
    build { dependsOn(shadowJar) }

    shadowJar {
        archiveBaseName.set("CoreLibRuntime")
        archiveClassifier.set("")
    }

    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") { expand(props) }
    }
}
