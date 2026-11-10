package xdm.app.skins

import kotlinx.serialization.json.Json
import java.io.File
import kotlin.system.exitProcess

/**
 * Command-line tool for developers and modders to validate .xdmskin packages
 * or unpacked skin directories against the specification and security requirements.
 */
object SkinValidatorCli {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @JvmStatic
    fun validateTarget(target: File): SkinPackageValidator.ValidationResult {
        if (!target.exists()) {
            return SkinPackageValidator.ValidationResult(false, listOf("Target path does not exist: ${target.absolutePath}"))
        }

        return if (target.isFile) {
            SkinPackageValidator.validateArchive(target)
        } else if (target.isDirectory) {
            val manifestFile = File(target, "skin.json")
            if (!manifestFile.exists()) {
                SkinPackageValidator.ValidationResult(false, listOf("Directory is missing required root 'skin.json'."))
            } else {
                try {
                    val manifest = json.decodeFromString<SkinManifest>(manifestFile.readText())
                    SkinPackageValidator.validateManifest(manifest, skinRoot = target)
                } catch (e: Throwable) {
                    SkinPackageValidator.ValidationResult(false, listOf("Failed to parse 'skin.json': ${e.message}"))
                }
            }
        } else {
            SkinPackageValidator.ValidationResult(false, listOf("Path is neither a regular file nor a directory."))
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            println("XDM Enhanced Skin Validator")
            println("Usage: java -cp xdm-app.jar xdm.app.skins.SkinValidatorCli <path-to-xdmskin-or-folder>")
            exitProcess(1)
        }

        val target = File(args[0])
        val result = validateTarget(target)

        if (result.isValid) {
            val m = result.manifest
            println("==================================================")
            println("VALIDATION PASSED: ${m?.name} (${m?.id})")
            println("Version:     ${m?.version}")
            println("Author:      ${m?.author}")
            println("Min App:     ${m?.minAppVersion}")
            println("License:     ${m?.license}")
            println("Arcs:        Component=${m?.geometry?.componentArc}px, Button=${m?.geometry?.buttonArc}px")
            println("Row Height:  ${m?.geometry?.rowHeight}px")
            println("Sidebar W:   ${m?.geometry?.sidebarWidth}px")
            println("Toolbar Pos: ${m?.layout?.toolbarPosition}")
            println("Sidebar Pos: ${m?.layout?.sidebarPosition}")
            println("==================================================")
            if (result.warnings.isNotEmpty()) {
                println("Warnings:")
                result.warnings.forEach { println(" - $it") }
            }
            exitProcess(0)
        } else {
            System.err.println("==================================================")
            System.err.println("VALIDATION FAILED with ${result.errors.size} error(s):")
            result.errors.forEach { System.err.println(" [ERROR] $it") }
            System.err.println("==================================================")
            exitProcess(2)
        }
    }
}

