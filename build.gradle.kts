plugins {
    kotlin("jvm") version "2.3.20"
    `java-library`
    id("com.gradleup.shadow") version "9.4.1"
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
    `maven-publish`
}

group = "me.albert"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://repo.tcoded.com/releases") {
        name = "tcoded-releases"
    }
    // Vault 的 JitPack 仓库
    maven("https://jitpack.io")


    maven("https://repo.rosewooddev.io/repository/public/")

}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"]) // Java 项目，或 components["kotlin"] 对于 Kotlin Multiplatform
            artifactId = "corelib"
        }
    }

    repositories {
        google()
        mavenLocal() // 发布到本地仓库
    }
}

dependencies {
    paperweight.foliaDevBundle("26.1.2.build.+")

    // 运行时依赖全部由 CoreLibRuntime 插件提供（见 runtime 子模块），
    // 这里降为 compileOnly：编译期可见、不打进 CoreLib 主 jar，从而把主 jar 压到最小。
    // 运行时依赖统一由 :runtime 子模块声明（全部 api）。
    // compileOnlyApi：编译期能用、不打进 CoreLib 主 jar；同时把 runtime 的传递依赖
    // 透传给下游插件的编译期。运行时这些类由 CoreLibRuntime 插件经共享 classloader 提供。
    compileOnlyApi(project(":runtime"))

    // Vault API
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")

    // PlayerPoints API (使用 CodeMC 仓库的最新常见版本，以 3.2.7 为例)
    compileOnly("org.black_ixx:playerpoints:3.2.7")
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.21")
    }
    shadowJar {
//        relocate("com.tcoded.folialib", "me.albert.core.folialib")
    }
}

val targetJavaVersion = 25

java {
    // 关键：必须加上这行，发布时才会生成并附带源码！
    withSourcesJar()
}

kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.build {
    dependsOn("shadowJar")
}


tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
