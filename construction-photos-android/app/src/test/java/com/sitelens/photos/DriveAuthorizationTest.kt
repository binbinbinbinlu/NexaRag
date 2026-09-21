package com.sitelens.photos

import org.junit.Assert.*
import org.junit.Test

class DriveAuthorizationTest {
    @Test fun resultPayloadIsParsedRatherThanDiscardedAsCancellation() {
        assertEquals("access-token", DriveAuthorization.readToken(true) { "access-token" })
        val providerFailure = RuntimeException("provider failure")
        val actual = assertThrows(RuntimeException::class.java) {
            DriveAuthorization.readToken(true) { throw providerFailure }
        }
        assertSame(providerFailure, actual)
    }
    @Test fun missingResultDoesNotFalselyClaimUserCanceled() {
        val error = assertThrows(IllegalStateException::class.java) {
            DriveAuthorization.readToken(false) { fail("No intent should be parsed"); null }
        }
        assertEquals(DriveAuthorization.NO_RESULT, error.message)
    }
    @Test fun blankTokenCannotContinueToDrive() {
        assertThrows(IllegalStateException::class.java) { DriveAuthorization.readToken(true) { " " } }
        assertThrows(IllegalStateException::class.java) { DriveAuthorization.readToken(true) { null } }
    }
    @Test fun configurationAndNetworkErrorsGiveDifferentRecoverySteps() {
        assertTrue(DriveAuthorization.failure(10).contains("signing SHA-1"))
        assertTrue(DriveAuthorization.failure(7).contains("internet"))
        assertTrue(DriveAuthorization.failure(1234).contains("1234"))
    }
}
