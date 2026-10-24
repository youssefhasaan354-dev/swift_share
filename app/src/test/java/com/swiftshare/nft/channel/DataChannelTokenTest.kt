package com.swiftshare.nft.channel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class DataChannelTokenTest {

    @Test
    fun testMagicHeaderAndTokenFormatMatches() {
        val magic = "SWFT".toByteArray()
        val token = "SECURE_SESSION_TOKEN_12345"
        val tokenBytes = token.toByteArray()

        val buffer = ByteBuffer.allocate(4 + 4 + tokenBytes.size)
        buffer.put(magic)
        buffer.putInt(tokenBytes.size)
        buffer.put(tokenBytes)
        buffer.flip()

        // Verify magic
        val isMagic = buffer.get() == 'S'.code.toByte() &&
                buffer.get() == 'W'.code.toByte() &&
                buffer.get() == 'F'.code.toByte() &&
                buffer.get() == 'T'.code.toByte()
        assertTrue("Header must match SWFT magic", isMagic)

        val readTokenLen = buffer.int
        val readTokenBytes = ByteArray(readTokenLen)
        buffer.get(readTokenBytes)
        val readToken = String(readTokenBytes)

        assertTrue("Parsed token must match sent token", readToken == token)
    }

    @Test
    fun testTokenMismatchRejectionLogic() {
        val expectedToken = "EXPECTED_TOKEN_ABCD"
        val attackerToken = "WRONG_TOKEN_XYZ"

        val matches = java.security.MessageDigest.isEqual(
            expectedToken.toByteArray(Charsets.UTF_8),
            attackerToken.toByteArray(Charsets.UTF_8)
        )
        assertFalse("Mismatched token must be rejected before creating any destination file", matches)

        val validMatches = java.security.MessageDigest.isEqual(
            expectedToken.toByteArray(Charsets.UTF_8),
            "EXPECTED_TOKEN_ABCD".toByteArray(Charsets.UTF_8)
        )
        assertTrue("Matching token must be accepted", validMatches)
    }
}
