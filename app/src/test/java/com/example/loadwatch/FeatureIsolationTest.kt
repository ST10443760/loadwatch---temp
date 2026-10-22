package com.example.loadwatch

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// NFR-01: no feature package imports another feature package, and features reach Room and the API only
// through the data layer. Unit tests run with the app module as the working directory.
class FeatureIsolationTest {

    private val featureRoot = File("src/main/java/com/example/loadwatch/feature")
    private val importPattern = Regex("""^import\s+([\w.]+)""", RegexOption.MULTILINE)

    @Test
    fun featurePackagesExist() {
        assertTrue("Feature sources not found at ${featureRoot.absolutePath}", featureRoot.isDirectory)
    }

    @Test
    fun featuresDoNotImportOtherFeatures() {
        val violations = featureImports().filter { (feature, import) ->
            import.startsWith("com.example.loadwatch.feature.") &&
                !import.startsWith("com.example.loadwatch.feature.$feature.")
        }
        assertTrue(violations.joinToString("\n", "Cross-feature imports:\n") { it.describe() }, violations.isEmpty())
    }

    @Test
    fun featuresUseTheDataLayerInsteadOfDatabaseOrNetwork() {
        val violations = featureImports().filter { (_, import) ->
            import.startsWith("com.example.loadwatch.core.database.") ||
                import.startsWith("com.example.loadwatch.core.network.")
        }
        assertTrue(violations.joinToString("\n", "Features bypassing the data layer:\n") { it.describe() }, violations.isEmpty())
    }

    private data class FeatureImport(val feature: String, val import: String, val file: File) {
        fun describe() = "${file.name} in feature '$feature' imports $import"
    }

    private fun featureImports(): List<FeatureImport> =
        featureRoot.listFiles { file -> file.isDirectory }.orEmpty().flatMap { featureDir ->
            featureDir.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
                importPattern.findAll(file.readText()).map { FeatureImport(featureDir.name, it.groupValues[1], file) }
            }
        }
}
