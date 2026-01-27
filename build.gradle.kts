import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties

plugins {
    kotlin("jvm") version "2.2.21"
    id("fabric-loom") version "1.14.6"
    id("maven-publish")
}

// Load version-specific properties from versions/<mc_target>.properties
// Override with -Pmc=X on command line, or set mc_target in gradle.properties
val mcTarget = project.findProperty("mc")?.toString()
    ?: project.findProperty("mc_target")?.toString()
    ?: "1.21.11"
val versionProps = Properties().apply {
    file("versions/$mcTarget.properties").inputStream().use { load(it) }
}

// Make version properties available
val minecraftVersion: String = versionProps.getProperty("minecraft_version")
val minecraftVersionRange: String = versionProps.getProperty("minecraft_version_range")
val fabricVersion: String = versionProps.getProperty("fabric_version")
val nlibVersion: String = versionProps.getProperty("nlib_version")
val modVersion: String = versionProps.getProperty("mod_version")

version = modVersion
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

val targetJavaVersion = 21
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this line, sources will not be generated.
    withSourcesJar()
}

loom {
    splitEnvironmentSourceSets()

    mods {
        register("civutils") {
            sourceSet("main")
            sourceSet("client")
        }
    }
}


repositories {
    // Add repositories to retrieve artifacts from in here.
    // You should only use this when depending on other mods because
    // Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
    // See https://docs.gradle.org/current/userguide/declaring_repositories.html
    // for more information about repositories.
    mavenLocal()
}

dependencies {
    // To change the versions see versions/*.properties files
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${project.property("kotlin_loader_version")}")

    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricVersion")

    // NLib UI library
    modImplementation("xyz.nim:nlib:$nlibVersion")
    include("xyz.nim:nlib:$nlibVersion")

    // Kotlin reflection for config and event systems
    implementation(kotlin("reflect"))

    // YAML parsing for MDX frontmatter
    implementation("org.yaml:snakeyaml:2.2")
    include("org.yaml:snakeyaml:2.2")
}

// Generate items-manifest.json from MDX files in handbook/items/
val generateItemManifest = tasks.register("generateItemManifest") {
    val itemsDir = file("src/client/resources/assets/civutils/handbook/items")
    val manifestFile = file("src/client/resources/assets/civutils/handbook/items-manifest.json")

    inputs.dir(itemsDir)
    outputs.file(manifestFile)

    doLast {
        if (!itemsDir.exists()) {
            logger.warn("Items directory does not exist, skipping manifest generation")
            return@doLast
        }

        val categories = itemsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name }
            ?.map { categoryDir ->
                val files = categoryDir.listFiles()
                    ?.filter { it.extension == "mdx" }
                    ?.map { it.name }
                    ?.sorted()
                    ?: emptyList()
                mapOf("folder" to categoryDir.name, "files" to files)
            }
            ?.filter { (it["files"] as List<*>).isNotEmpty() }
            ?: emptyList()

        val manifest = mapOf(
            "version" to 2,
            "categories" to categories
        )

        val json = groovy.json.JsonBuilder(manifest).toPrettyString()
        manifestFile.writeText(json + "\n")
        logger.lifecycle("Generated items-manifest.json with ${categories.size} categories")
    }
}

tasks.processResources {
    dependsOn(generateItemManifest)
    inputs.property("version", project.version)
    inputs.property("minecraft_version", minecraftVersion)
    inputs.property("minecraft_version_range", minecraftVersionRange)
    inputs.property("loader_version", project.property("loader_version"))
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version_range" to minecraftVersionRange,
            "loader_version" to project.property("loader_version")!!,
            "kotlin_loader_version" to project.property("kotlin_loader_version")!!
        )
    }
}

tasks.named("processClientResources") {
    dependsOn(generateItemManifest)
}

tasks.named("sourcesJar") {
    dependsOn(generateItemManifest)
}

tasks.withType<JavaCompile>().configureEach {
    // ensure that the encoding is set to UTF-8, no matter what the system default is
    // this fixes some edge cases with special characters not displaying correctly
    // see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
    // If Javadoc is generated, this must be specified in that task too.
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}

// configure the maven publication
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }

    // See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
    repositories {
        // Add repositories to publish to here.
        // Notice: This block does NOT have the same function as the block in the top level.
        // The repositories here will be used for publishing your artifact, not for
        // retrieving dependencies.
    }
}
