package io.github.skrtk98.iroha.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageExtensionTest {
    @Test
    fun detectsSupportedFormatsFromTheirHeaders() {
        assertEquals("png", imageExtension(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0)))
        assertEquals("jpg", imageExtension(bytes(0xFF, 0xD8, 0xFF, 0xE0)))
        assertEquals("gif", imageExtension("GIF89a".toByteArray()))
        assertEquals("webp", imageExtension("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray()))
    }

    @Test
    fun rejectsOtherOrTruncatedData() {
        assertNull(imageExtension("RIFF\u0000\u0000\u0000\u0000WAVE".toByteArray()))
        assertNull(imageExtension(bytes(0xFF, 0xD8)))
        assertNull(imageExtension(ByteArray(0)))
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
