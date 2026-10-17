package voice.core.scanner

import android.app.Application
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import voice.core.logging.api.LogWriter
import voice.core.logging.api.Logger
import voice.core.scanner.matroska.MatroskaCoverExtractor
import voice.core.zip.ZipArchiveProvider
import voice.core.zip.ZipUriCodec

@RunWith(AndroidJUnit4::class)
class MatroskaCoverExtractorTest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun mka() {
    val coverExtractor = matroskaCoverExtractor()
    val testFile = resource("mka_with_cover.mka")
    val coverFile = temporaryFolder.newFile("cover.jpg")
    val extracted = runBlocking {
      coverExtractor.extract(
        input = testFile.toUri(),
        outputFile = coverFile,
      )
    }
    assertTrue(extracted)
    assertTrue(coverFile.isFile)
  }

  @Test
  fun `mka inside of a zip`() {
    // the content resolver cannot serve a zip entry, so the entry is buffered as a whole
    val coverExtractor = matroskaCoverExtractor()
    val zipFile = temporaryFolder.newFile("book.zip")
    zipContaining(zipFile, resource("mka_with_cover.mka"), entryName = "chapter.mka")
    val coverFile = temporaryFolder.newFile("cover-from-zip.jpg")

    val extracted = runBlocking {
      coverExtractor.extract(
        input = ZipUriCodec.encode(zipFile.toUri(), "chapter.mka"),
        outputFile = coverFile,
      )
    }

    assertTrue(extracted)
    assertTrue(coverFile.isFile)
    assertTrue(coverFile.length() > 0)
  }

  private fun matroskaCoverExtractor(): MatroskaCoverExtractor {
    val context = ApplicationProvider.getApplicationContext<Application>()
    return MatroskaCoverExtractor(
      context = context,
      zipArchiveProvider = ZipArchiveProvider(
        context = context,
        passwordsStore = mockk(relaxed = true),
        passwordRequester = mockk(relaxed = true),
      ),
    )
  }

  private fun resource(name: String): File {
    return File(javaClass.classLoader!!.getResource(name)!!.file)
  }

  private fun zipContaining(
    zipFile: File,
    content: File,
    entryName: String,
  ) {
    ZipOutputStream(zipFile.outputStream().buffered()).use { out ->
      out.putNextEntry(ZipEntry(entryName))
      content.inputStream().use { input ->
        input.copyTo(out)
      }
      out.closeEntry()
    }
  }

  companion object {

    @BeforeClass
    @JvmStatic
    fun setup() {
      Logger.install(
        object : LogWriter {
          override fun log(
            severity: Logger.Severity,
            message: String,
            throwable: Throwable?,
          ) {
            println(
              buildString {
                append("${severity.name}: ")
                append(message)
                if (throwable != null) {
                  append(", $throwable")
                }
              },
            )
          }
        },
      )
    }
  }
}
