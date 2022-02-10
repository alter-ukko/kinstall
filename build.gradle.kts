import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.10"
    application
}

group = "com.dimdarkevil"
version = "1.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.commons:commons-compress:1.21")
    implementation("com.offbytwo:docopt:0.6.0.20150202")
    implementation("org.slf4j:slf4j-simple:1.7.35")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<ProcessResources> {
    eachFile {
        if (name == "version.properties") {
            filter { line ->
                line.replace("%project.version%", "${project.version}")
            }
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClass.set("com.dimdarkevil.kinstall.Install")
}
