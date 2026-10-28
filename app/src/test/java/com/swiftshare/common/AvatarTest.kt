package com.swiftshare.common

import com.swiftshare.nft.channel.models.AvatarUpdateMessage
import com.swiftshare.nft.channel.models.ControlMessage
import com.swiftshare.nft.channel.models.MessageType
import com.swiftshare.nft.discovery.models.ConnectionPayload
import com.swiftshare.nft.discovery.models.PeerConnectionType
import com.swiftshare.nft.discovery.models.PeerDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarTest {

    @Test
    fun testBase64EncodingAndDecodingRoundTrip() {
        val testData = "SwiftShare Avatar Test Payload \uD83D\uDE80\u26A1".toByteArray(Charsets.UTF_8)
        val encoded = AvatarUtils.encodeBase64(testData)
        assertTrue(encoded.isNotBlank())
        val decoded = AvatarUtils.decodeBase64(encoded)
        assertEquals(String(testData, Charsets.UTF_8), String(decoded, Charsets.UTF_8))
    }

    @Test
    fun testAvatarSizeLimitEnforcement() {
        // 32 KB payload
        val validBytes = ByteArray(32 * 1024) { 0x5A }
        val validB64 = AvatarUtils.encodeBase64(validBytes)
        assertTrue("32 KB payload must be within limit", AvatarUtils.isUnderMaxLimit(validB64))

        // 33 KB payload (over the 32 KB limit)
        val oversizedBytes = ByteArray(33 * 1024) { 0x5A }
        val oversizedB64 = AvatarUtils.encodeBase64(oversizedBytes)
        assertFalse("33 KB payload must exceed limit", AvatarUtils.isUnderMaxLimit(oversizedB64))
    }

    @Test
    fun testAvatarUpdateControlMessageSerialization() {
        val avatarMsg = AvatarUpdateMessage(
            deviceName = "Pixel 8 Pro",
            avatarEmoji = "⚡",
            customAvatarBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
        )
        val controlMsg = ControlMessage(
            type = MessageType.AVATAR_UPDATE,
            avatarUpdate = avatarMsg
        )

        val json = controlMsg.toJson()
        val parsed = ControlMessage.fromJson(json)

        assertNotNull(parsed)
        assertEquals(MessageType.AVATAR_UPDATE, parsed?.type)
        assertEquals("Pixel 8 Pro", parsed?.avatarUpdate?.deviceName)
        assertEquals("⚡", parsed?.avatarUpdate?.avatarEmoji)
        assertEquals(avatarMsg.customAvatarBase64, parsed?.avatarUpdate?.customAvatarBase64)
    }

    @Test
    fun testPeerDeviceWithAvatarAndDeviceType() {
        val peer = PeerDevice(
            id = "192.168.1.50:2999",
            deviceName = "iPhone 15",
            ip = "192.168.1.50",
            port = 2999,
            avatarEmoji = "🍏",
            deviceType = "iOS",
            connectionType = PeerConnectionType.SAME_LAN_NSD
        )

        assertEquals("iOS", peer.deviceType)
        assertEquals("🍏", peer.avatarEmoji)
        assertEquals("iPhone 15", peer.deviceName)
    }

    @Test
    fun testConnectionPayloadWithAvatarAndDeviceType() {
        val payload = ConnectionPayload(
            ssid = "DIRECT-SS-Test",
            preSharedKey = "secret123",
            sessionToken = "tok_abc",
            deviceName = "Galaxy S24",
            avatarEmoji = "🦊",
            customAvatarBase64 = "dGVzdA==",
            deviceType = "Android"
        )

        val json = payload.toJson()
        val parsed = ConnectionPayload.fromJson(json)

        assertNotNull(parsed)
        assertEquals("🦊", parsed?.avatarEmoji)
        assertEquals("dGVzdA==", parsed?.customAvatarBase64)
        assertEquals("Android", parsed?.deviceType)
    }
}
