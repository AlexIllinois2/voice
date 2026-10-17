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
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import voice.core.zip.ZipArchiveProvider
import voice.core.zip.ZipUriCodec

@RunWith(AndroidJUnit4::class)
internal class CoverExtractorTest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private val app: Application = ApplicationProvider.getApplicationContext()

  // an unencrypted archive never asks for a password, so the stores are not needed
  private val zipArchiveProvider = ZipArchiveProvider(
    context = app,
    passwordsStore = mockk(relaxed = true),
    passwordRequester = mockk(relaxed = true),
  )

  private val coverExtractor = CoverExtractor(
    context = app,
    matroskaCoverExtractor = mockk(relaxed = true),
    zipArchiveProvider = zipArchiveProvider,
  )

  @Test
  fun `extracts an embedded cover out of a zip entry`() {
    // the entry is not readable through the content resolver, so it has to be streamed out
    // of the archive for the metadata to be retrievable at all
    val zipFile = zipContaining(resource = "auphonic_chapters_demo.mp3", entryName = "Chapter 1.mp3")
    val coverFile = temporaryFolder.newFile("cover.jpg")

    val extracted = runBlocking {
      coverExtractor.extractCover(
        input = ZipUriCodec.encode(zipFile.toUri(), "Chapter 1.mp3"),
        outputFile = coverFile,
      )
    }

    assertTrue(extracted)
    assertTrue(coverFile.isFile)
    assertTrue(coverFile.length() > 0)
  }

  private fun zipContaining(
    resource: String,
    entryName: String,
  ): File {
    val zipFile = temporaryFolder.newFile("book.zip")
    ZipOutputStream(zipFile.outputStream().buffered()).use { out ->
      out.putNextEntry(ZipEntry(entryName))
      javaClass.classLoader!!.getResourceAsStream(resource)!!.use { input ->
        input.copyTo(out)
      }
      out.closeEntry()
    }
    return zipFile
  }
}
