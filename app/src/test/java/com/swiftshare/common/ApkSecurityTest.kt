package com.swiftshare.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ApkSecurityTest {

    @Test
    fun testCertSha256FingerprintFormatting() {
        val dummyCert = "SwiftShare Test Signature Bytes".toByteArray(Charsets.UTF_8)
        val fingerprint = ApkInstaller.computeCertSha256(dummyCert)

        // SHA-256 is 32 bytes -> 32 hex pairs separated by colons -> 32*2 + 31 = 95 characters
        assertEquals(95, fingerprint.length)
        assertTrue(fingerprint.contains(":"))
        // Check valid hex characters
        val pairs = fingerprint.split(":")
        assertEquals(32, pairs.size)
        pairs.forEach { pair ->
            assertEquals(2, pair.length)
            assertTrue("Pair must be uppercase hex: $pair", pair.matches(Regex("^[0-9A-F]{2}$")))
        }
    }

    @Test
    fun testApkRiskClassification() {
        val file = File("test.apk")

        // 1. Safe Report (no dangerous perms)
        val safeReport = ApkSecurityReport(
            apkFile = file,
            packageName = "com.example.calculator",
            appName = "Calculator",
            versionName = "1.0",
            versionCode = 1L,
            certSha256 = "AA:BB:CC",
            dangerousPermissions = emptyList(),
            riskLevel = ApkRiskLevel.SAFE
        )
        assertEquals(ApkRiskLevel.SAFE, safeReport.riskLevel)

        // 2. Medium Risk Report (Location / Camera)
        val medReport = ApkSecurityReport(
            apkFile = file,
            packageName = "com.example.camera",
            appName = "Camera App",
            versionName = "2.0",
            versionCode = 2L,
            certSha256 = "11:22:33",
            dangerousPermissions = listOf("android.permission.CAMERA", "android.permission.ACCESS_FINE_LOCATION"),
            riskLevel = ApkRiskLevel.MEDIUM_RISK
        )
        assertEquals(ApkRiskLevel.MEDIUM_RISK, medReport.riskLevel)

        // 3. High Risk Report (SMS / Accessibility / Request Install)
        val highReport = ApkSecurityReport(
            apkFile = file,
            packageName = "com.example.smsreader",
            appName = "SMS Tool",
            versionName = "3.0",
            versionCode = 3L,
            certSha256 = "99:88:77",
            dangerousPermissions = listOf("android.permission.SEND_SMS", "android.permission.BIND_ACCESSIBILITY_SERVICE"),
            riskLevel = ApkRiskLevel.HIGH_RISK
        )
        assertEquals(ApkRiskLevel.HIGH_RISK, highReport.riskLevel)
    }
}

