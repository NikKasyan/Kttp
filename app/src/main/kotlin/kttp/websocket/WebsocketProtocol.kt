package kttp.websocket

import kttp.io.DefaultInputStream
import kttp.io.IOStream
import kttp.log.Logger
import java.io.InputStream
import java.nio.charset.Charset
import java.security.SecureRandom
import kotlin.experimental.or
import kotlin.experimental.xor

private val EMPTY = byteArrayOf()

private val RANDOM_SOURCE = SecureRandom()

private val EMPTY_PAYLOAD = WebsocketPayload(ByteArray(0), false, EMPTY)

class WebsocketFrame(
	val isFinalFrame: Boolean,
	val opcode: Int,
	private val masked: Boolean,
	private val maskingKey: ByteArray = if (masked) createMaskingKey() else EMPTY,
	val payload: WebsocketPayload = EMPTY_PAYLOAD
) {

	constructor(
		isFinalFrame: Boolean,
		opcode: Int,
		masked: Boolean,
		maskingKey: ByteArray,
		payload: ByteArray
	) : this(isFinalFrame, opcode, masked, maskingKey, WebsocketPayload(payload, masked, maskingKey))

	constructor(
		isFinalFrame: Boolean,
		opcode: Int,
		masked: Boolean,
		maskingKey: ByteArray = if (masked) createMaskingKey() else EMPTY,
		payload: ByteArray,
		offset: Int,
		length: Int
	) : this(isFinalFrame, opcode, masked, maskingKey, WebsocketPayload(payload, offset, length, masked, maskingKey))



	companion object {

		fun readFrom(ioStream: IOStream): WebsocketFrame {
			val firstByte = ioStream.readByte()
			val isFinalFrame = firstByte.toInt() and 0x80 != 0
			val opcode = firstByte.toInt() and 0x0F
			val secondByte = ioStream.readByte()
			val masked = secondByte.toInt() and 0x80 != 0
			val payloadLength = when (val length = secondByte.toInt() and 0x7F) {
				in 0..125 -> length
				126 -> ioStream.readShort().toInt()
				127 -> ioStream.readLong().toInt()
				else -> throw IllegalArgumentException("Invalid payload length")
			}
			val maskingKey = if (masked) ioStream.readNBytes(4) else EMPTY

			val payload = WebsocketPayload(ioStream, masked, maskingKey, payloadLength.toLong())

			return WebsocketFrame(isFinalFrame, opcode, masked, maskingKey, payload)
		}

	}

	init {
		require(opcode in 0..15) { "Invalid opcode" }
		require(!masked || maskingKey.size == 4) { "Masking key must be present if mask is set and must have a length of 4 bytes" }
//		require(!masked || maskingKey.any { it.toInt() == 0 }) { "Masking key must not be empty" }
		require(opcode != WebsocketOpCodes.CONTINUATION || !isFinalFrame) { "Continuation frame must not be final" }
		require(isFinalFrame || opcode == WebsocketOpCodes.CONTINUATION) { "Non final frame must be continuation frame" }
	}

	fun writeTo(ioStream: IOStream) {
		val firstByte = (if (isFinalFrame) 0x80 else 0) or opcode
		ioStream.writeByteToBuffer(firstByte.toByte())
		writeLength(ioStream)
		if (masked) {
			ioStream.writeBytesToBuffer(maskingKey)
			ioStream.writeFromStream(payload)
		} else {
			ioStream.writeBytesToBuffer(payload)
		}
		ioStream.flush()
	}

	private fun writeLength(ioStream: IOStream) {
		val length = payload.length
		val maskBit = if (masked) 0x80 else 0
		when {
			length < 126 -> ioStream.writeByteToBuffer(maskBit.toByte() or length.toByte())
			length < 65536 -> {
				ioStream.writeByteToBuffer(maskBit.toByte() or 126.toByte())
				ioStream.writeBytesToBuffer(shortToBytes(length.toShort()))
			}

			else -> {
				ioStream.writeByteToBuffer(maskBit.toByte() or 127.toByte())
				ioStream.writeBytesToBuffer(longToBytes(length))
			}
		}
	}

	private fun shortToBytes(short: Short): ByteArray {
		return byteArrayOf(
			(short.toInt() shr 8).toByte(),
			short.toByte()
		)
	}

	private fun longToBytes(long: Long): ByteArray {
		return byteArrayOf(
			(long.toInt() shr 56).toByte(),
			(long.toInt() shr 48).toByte(),
			(long.toInt() shr 40).toByte(),
			(long.toInt() shr 32).toByte(),
			(long.toInt() shr 24).toByte(),
			(long.toInt() shr 16).toByte(),
			(long.toInt() shr 8).toByte(),
			long.toByte()
		)
	}
}

class WebsocketPayload(inputStream: InputStream, masked: Boolean, maskingKey: ByteArray, val length: Long) :
	DefaultInputStream() {

	constructor(
		bytes: ByteArray,
		offset: Int = 0,
		length: Int = bytes.size,
		masked: Boolean,
		maskingKey: ByteArray = if (masked) createMaskingKey() else EMPTY
	) : this(bytes.inputStream(offset, length), masked, maskingKey, length.toLong())

	constructor(
		bytes: ByteArray,
		masked: Boolean,
		maskingKey: ByteArray = if (masked) createMaskingKey() else EMPTY
	) : this(bytes.inputStream(), masked, maskingKey, bytes.size.toLong())

	private val inputStream: WebsocketInputStream = WebsocketInputStream(inputStream, masked, maskingKey)

	override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
		return inputStream.read(bytes, offset, length)
	}

	fun toString(charset: Charset = Charsets.UTF_8): String {
		return inputStream.readNBytes(length.toInt()).toString(charset)
	}
}

class WebsocketInputStream(
	private val inputStream: InputStream,
	private val masked: Boolean = true,
	private val maskingKey: ByteArray = if (masked) createMaskingKey() else EMPTY
) : DefaultInputStream() {


	override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
		val read = inputStream.read(bytes, offset, length)
		if (!masked)
			return read
		for (i in 0 until read) {
			bytes[offset + i] = (bytes[offset + i] xor maskingKey[(offset + i) % 4])
		}
		return read
	}
}

private fun createMaskingKey(): ByteArray {
	val key = ByteArray(4)
	RANDOM_SOURCE.nextBytes(key)
	return key
}

object WebsocketOpCodes {
	const val CONTINUATION = 0x0
	const val TEXT = 0x1
	const val BINARY = 0x2

	// 0x3-7 are reserved for further non-control frames
	const val CLOSE = 0x8
	const val PING = 0x9
	const val PONG = 0xA
	// 0xB-F are reserved for further control frames
}

/*
      0                   1                   2                   3
      0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     +-+-+-+-+-------+-+-------------+-------------------------------+
     |F|R|R|R| opcode|M| Payload len |    Extended payload length    |
     |I|S|S|S|  (4)  |A|     (7)     |             (16/64)           |
     |N|V|V|V|       |S|             |   (if payload len==126/127)   |
     | |1|2|3|       |K|             |                               |
     +-+-+-+-+-------+-+-------------+ - - - - - - - - - - - - - - - +
     |     Extended payload length continued, if payload len == 127  |
     + - - - - - - - - - - - - - - - +-------------------------------+
     |                               |Masking-key, if MASK set to 1  |
     +-------------------------------+-------------------------------+
     | Masking-key (continued)       |          Payload Data         |
     +-------------------------------- - - - - - - - - - - - - - - - +
     :                     Payload Data continued ...                :
     + - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - +
     |                     Payload Data continued ...                |
     +---------------------------------------------------------------+
 */
