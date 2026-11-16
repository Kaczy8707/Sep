package pl.septrener

import java.io.File

/** Dostęp do wbudowanych assetów w testach JVM (katalog roboczy testów = moduł app). */
object TestFiles {
    private val assetsDir: File = listOf(File("src/main/assets/content"), File("app/src/main/assets/content")).first { it.isDirectory }
    fun asset(name: String): String = File(assetsDir, name).readText()
    val materials: File = listOf(File("../materialy"), File("materialy")).first { it.isDirectory }
}
