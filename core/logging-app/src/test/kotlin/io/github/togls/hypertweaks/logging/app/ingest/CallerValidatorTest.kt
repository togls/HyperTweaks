package io.github.togls.hypertweaks.logging.app.ingest

import org.junit.Assert.assertTrue
import org.junit.Test

class CallerValidatorTest {
    @Test
    fun `accepts scoped package owned by calling uid`() {
        val validator = CallerValidator(
            packagesForUid = { arrayOf("com.google.android.apps.photos") },
        )

        assertTrue(
            validator.validate(
                uid = 20_000,
                callingPid = 123,
                senderPackage = "com.google.android.apps.photos",
                senderPid = 123,
            ).isSuccess,
        )
    }

    @Test
    fun `rejects spoofed or unrelated packages`() {
        val validator = CallerValidator(
            packagesForUid = { arrayOf("com.example.unrelated") },
        )

        assertTrue(validator.validate(20_000, 123, "com.google.android.apps.photos", 123).isFailure)
        assertTrue(validator.validate(20_000, 123, "com.example.unrelated", 123).isFailure)
    }

    @Test
    fun `rejects spoofed sender pid and untrusted system uid package`() {
        val validator = CallerValidator(
            packagesForUid = { arrayOf("com.google.android.apps.photos") },
        )

        assertTrue(
            validator.validate(20_000, 123, "com.google.android.apps.photos", 456).isFailure,
        )
        assertTrue(validator.validate(1_000, 123, "com.google.android.apps.photos", 123).isFailure)
        assertTrue(validator.validate(1_000, 123, "android", 123).isSuccess)
    }
}
