package voice.core.scanner.matroska

import org.ebml.io.DataSource
import java.nio.ByteBuffer
import kotlin.math.max

internal class BytesSeekableDataSource(private val bytes: ByteArray) : DataSource,
  AutoCloseable {

  private var position = 0L
  private var closed = false

  override fun length(): Long = bytes.size.toLong()

  override fun getFilePointer(): Long {
    if (closed) return -1
    return position
  }

  override fun isSeekable(): Boolean = !closed

  override fun seek(pos: Long): Long {
    if (closed) return -1
    position = pos.coerceIn(0L, bytes.size.toLong())
    return position
  }

  override fun readByte(): Byte {
    check(!closed) { "DataSource is closed" }
    if (position >= bytes.size) {
      throw RuntimeException("End of stream reached")
    }
    return bytes[position.toInt()].also { position++ }
  }

  override fun read(buff: ByteBuffer): Int {
    if (closed) return -1
    val remaining = bytes.size - position.toInt()
    if (remaining <= 0) return -1
    val toRead = minOf(buff.remaining(), remaining)
    buff.put(bytes, position.toInt(), toRead)
    position += toRead
    return toRead
  }

  override fun skip(offset: Long): Long {
    if (closed) return 0
    val currentPos = position
    val newPos = max(0.0, (currentPos + offset).toDouble()).toLong()
      .coerceAtMost(bytes.size.toLong())
    position = newPos
    return newPos - currentPos
  }

  override fun close() {
    closed = true
  }
}
