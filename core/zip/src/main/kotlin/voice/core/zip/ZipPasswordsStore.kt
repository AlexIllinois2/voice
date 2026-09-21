package voice.core.zip

import android.app.Application
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

@Inject
@SingleIn(AppScope::class)
class ZipPasswordsStore(private val context: Application) {

  private val json = Json { ignoreUnknownKeys = true }

  private val store: DataStore<Map<String, String>> by lazy {
    DataStoreFactory.create(
      serializer = ZipPasswordsSerializer(json),
    ) {
      File(context.filesDir, "datastore/zipPasswords")
    }
  }

  suspend fun password(zipUri: String): String? {
    return store.data.first()[zipUri]
  }

  suspend fun store(zipUri: String, password: String) {
    store.updateData { it + (zipUri to password) }
  }

  private class ZipPasswordsSerializer(private val json: Json) : Serializer<Map<String, String>> {

    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    override val defaultValue: Map<String, String> = emptyMap()

    override suspend fun readFrom(input: InputStream): Map<String, String> {
      return try {
        json.decodeFromString(mapSerializer, input.readBytes().decodeToString())
      } catch (e: Exception) {
        throw CorruptionException("Failed to read zip passwords", e)
      }
    }

    override suspend fun writeTo(
      t: Map<String, String>,
      output: OutputStream,
    ) {
      output.write(json.encodeToString(mapSerializer, t).encodeToByteArray())
    }
  }
}
