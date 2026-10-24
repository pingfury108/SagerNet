rootProject.extra.apply {
    set("androidPluginVersion", "7.1.3")
    set("kotlinVersion", "1.6.21")
    set("hutoolVersion", "5.7.22")
}

repositories {
    // CN mirrors first, originals kept as fallback
    maven(url = "https://maven.aliyun.com/repository/google")
    maven(url = "https://maven.aliyun.com/repository/central")
    maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
    google()
    mavenCentral()
    gradlePluginPortal()
    maven(url = "https://jitpack.io")
}