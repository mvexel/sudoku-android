import javax.inject.Inject

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Puzzle bank: every puzzle the app offers is generated at build time by the :engine module's
// BankBuilder and packaged as assets (puzzles/hard.txt, expert.txt, extreme.txt). Output is
// deterministic for a given count and seed, so the task is up-to-date/cached after the first build.
// Override with -PpuzzleCount=<n per level> -PpuzzleSeed=<n> -PpuzzleThreads=<n>.
fun prop(name: String, default: String) = providers.gradleProperty(name).orElse(default).get()

@CacheableTask
abstract class GeneratePuzzleBank : DefaultTask() {
    @get:Classpath abstract val toolClasspath: ConfigurableFileCollection
    @get:Input abstract val count: Property<Int>
    @get:Input abstract val seed: Property<Long>
    // Thread count doesn't change the output, so it isn't an input.
    @get:Internal abstract val threads: Property<Int>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @get:Inject abstract val execOps: ExecOperations

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        execOps.javaexec {
            classpath(toolClasspath)
            mainClass.set("net.vanexel.sudoku.engine.BankBuilder")
            maxHeapSize = "1g"
            args(
                "--out", out.absolutePath,
                "--count", count.get().toString(),
                "--seed", seed.get().toString(),
                "--threads", threads.get().toString(),
            )
        }
    }
}

val bankTool: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    // Ask for :engine's runtime jar explicitly instead of relying on the legacy no-attributes fallback.
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements::class.java, LibraryElements.JAR))
    }
}

val generatePuzzleBank = tasks.register<GeneratePuzzleBank>("generatePuzzleBank") {
    toolClasspath.from(bankTool)
    count.set(prop("puzzleCount", "500").toInt())
    seed.set(prop("puzzleSeed", "20261027").toLong())
    threads.set(prop("puzzleThreads", Runtime.getRuntime().availableProcessors().toString()).toInt())
    outputDir.set(layout.buildDirectory.dir("generated/puzzleBank"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(generatePuzzleBank, GeneratePuzzleBank::outputDir)
    }
}

android {
    namespace = "net.vanexel.sudoku"
    compileSdk = 36
    defaultConfig {
        applicationId = "net.vanexel.sudoku"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":engine"))
    bankTool(project(":engine"))
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    val composeBom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")

    testImplementation("junit:junit:4.13.2")
}
