pluginManagement {
    repositories {
        // 在非 CI 环境使用阿里云镜像加速
        if (System.getenv("CI") == null) {
            maven { url = uri("https://maven.aliyun.com/repository/public") }
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        // 在非 CI 环境使用阿里云镜像加速
        if (System.getenv("CI") == null) {
            maven { url = uri("https://maven.aliyun.com/repository/public") }
            maven { url = uri("https://maven.aliyun.com/repository/central") }
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/jcenter") }
        }
        mavenCentral()
        google()
    }
}

rootProject.name = "defargs-intellij-plugin"