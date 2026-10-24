package com.swiftshare.webshare

import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebAuthTest {

    @Test
    fun testPinVerificationAndSessionValidation() {
        val authManager = WebAuthManager()
        val pin = authManager.currentPin.value

        assertTrue(authManager.verifyPin(pin))
        assertFalse(authManager.verifyPin("wrong-pin"))

        val sessionId = authManager.createAuthorizedSession()
        assertTrue(authManager.isValidSession(sessionId))
        assertFalse(authManager.isValidSession("random-non-existent-session"))
        assertFalse(authManager.isValidSession(null))
    }

    @Test
    fun testPinRateLimitingAndLockout() {
        val authManager = WebAuthManager()
        val testIp = "192.168.1.55"

        assertFalse(authManager.isIpLockedOut(testIp))

        // First 4 failed attempts should NOT lock out
        for (i in 1..4) {
            val locked = authManager.recordFailedAttempt(testIp)
            assertFalse("Attempt $i should not lock out yet", locked)
            assertFalse(authManager.isIpLockedOut(testIp))
        }

        // 5th failed attempt MUST lock out
        val lockedOn5th = authManager.recordFailedAttempt(testIp)
        assertTrue("5th attempt must trigger lockout", lockedOn5th)
        assertTrue(authManager.isIpLockedOut(testIp))

        // Reset rate limit resets status
        authManager.resetRateLimit(testIp)
        assertFalse(authManager.isIpLockedOut(testIp))
    }

    @Test
    fun testKtorProtectedRoutesWithoutCookieReturns401() = testApplication {
        val authManager = WebAuthManager()

        routing {
            get("/api/manifest") {
                val cookie = call.request.cookies[WebAuthManager.COOKIE_NAME]
                if (!authManager.isValidSession(cookie)) {
                    call.respond(HttpStatusCode.Unauthorized, "{\"error\":\"Authentication required\"}")
                    return@get
                }
                call.respondText("{\"status\":\"ok\"}", ContentType.Application.Json)
            }
        }

        // Request without cookie -> 401 Unauthorized
        val unauthenticatedResponse = client.get("/api/manifest")
        assertEquals(HttpStatusCode.Unauthorized, unauthenticatedResponse.status)

        // Request with valid cookie -> 200 OK
        val validSession = authManager.createAuthorizedSession()
        val authenticatedResponse = client.get("/api/manifest") {
            cookie(WebAuthManager.COOKIE_NAME, validSession)
        }
        assertEquals(HttpStatusCode.OK, authenticatedResponse.status)
    }

    @Test
    fun testKtorPinAuthRateLimitEndpointReturns429OnLockout() = testApplication {
        val authManager = WebAuthManager()
        val testIp = "127.0.0.1"

        routing {
            post("/api/auth/pin") {
                if (authManager.isIpLockedOut(testIp)) {
                    call.respond(HttpStatusCode.TooManyRequests, "{\"error\":\"Too many attempts\"}")
                    return@post
                }
                val pin = call.receiveText()
                if (authManager.verifyPin(pin)) {
                    authManager.resetRateLimit(testIp)
                    call.respondText("{\"status\":\"authorized\"}", ContentType.Application.Json)
                } else {
                    val locked = authManager.recordFailedAttempt(testIp)
                    if (locked) {
                        call.respond(HttpStatusCode.TooManyRequests, "{\"error\":\"Too many attempts\"}")
                    } else {
                        call.respond(HttpStatusCode.Unauthorized, "{\"error\":\"Invalid PIN\"}")
                    }
                }
            }
        }

        // 4 failed attempts -> 401
        for (i in 1..4) {
            val res = client.post("/api/auth/pin") {
                contentType(ContentType.Application.Json)
                setBody("000000")
            }
            assertEquals(HttpStatusCode.Unauthorized, res.status)
        }

        // 5th failed attempt -> 429 Too Many Requests
        val lockedRes = client.post("/api/auth/pin") {
            contentType(ContentType.Application.Json)
            setBody("000000")
        }
        assertEquals(HttpStatusCode.TooManyRequests, lockedRes.status)

        // Subsequent attempt while locked out -> 429
        val subsequentRes = client.post("/api/auth/pin") {
            contentType(ContentType.Application.Json)
            setBody("000000")
        }
        assertEquals(HttpStatusCode.TooManyRequests, subsequentRes.status)
    }
}
