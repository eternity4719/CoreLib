plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
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

    // 运行时依赖的唯一声明处。用 api 暴露：
    //   - shadowJar 把它们打进 CoreLibRuntime.jar 供服务器运行时共享
    //   - 发布到 maven 后，作为传递依赖暴露给 CoreLib 及所有下游插件的编译期
    api("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    api("org.jetbrains.exposed:exposed-jdbc:1.3.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    api("com.zaxxer:HikariCP:5.1.0")
    api("com.github.jarod:qqwry-java:0.10.1")
    api("com.github.shynixn.mccoroutine:mccoroutine-folia-api:2.22.0")
    api("com.github.shynixn.mccoroutine:mccoroutine-folia-core:2.22.0")
    val ktorVersion = "3.2.3"
    api("io.ktor:ktor-client-core:${ktorVersion}")
    api("io.ktor:ktor-client-okhttp:${ktorVersion}")
    api("io.ktor:ktor-server-core:${ktorVersion}")
    api("io.ktor:ktor-server-netty:${ktorVersion}")
    api("io.ktor:ktor-server-default-headers:${ktorVersion}")
    api("io.ktor:ktor-server-status-pages:${ktorVersion}")
    api("io.ktor:ktor-server-cors:${ktorVersion}")
    api("io.ktor:ktor-server-content-negotiation:${ktorVersion}")
    api("io.ktor:ktor-serialization-kotlinx-json:${ktorVersion}")
}

kotlin {
    jvmToolchain(25)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"]) // 普通 jar + 含全部 api 依赖的 POM
            artifactId = "corelib-runtime"
        }
    }
    repositories { mavenLocal() }
}

tasks {
    build { dependsOn(shadowJar) }

    shadowJar {
        archiveBaseName.set("CoreLibRuntime")
        // 部署到服务器的胖 jar：CoreLibRuntime-<version>-all.jar
        archiveClassifier.set("all")

        // Netty 由 Minecraft 服务端自带，无需重复打包（已实测去掉可正常运行）。
        // ktor-server-netty 会传递引入 io.netty，这里整组排除以缩小运行时 jar。
        dependencies {
            exclude(dependency("io.netty:.*:.*"))
        }
    }

    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") { expand(props) }
    }
}
