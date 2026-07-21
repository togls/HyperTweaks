package io.github.togls.hypertweaks.logging.app.ingest

import org.junit.Assert.assertTrue
import org.junit.Test

class CallerValidatorTest {
    @Test
    fun `accepts scoped package owned by calling uid`() {
        val validator = CallerValidator(
            packagesForUid = { arrayOf("com.google.android.apps.photos") },
        )

        assertTrue(validator.validate(20_000, "com.google.android.apps.photos").isSuccess)
    }

    @Test
    fun `rejects spoofed or unrelated packages`() {
        val validator = CallerValidator(
            packagesForUid = { arrayOf("com.example.unrelated") },
        )

        assertTrue(validator.validate(20_000, "com.google.android.apps.photos").isFailure)
        assertTrue(validator.validate(20_000, "com.example.unrelated").isFailure)
    }
}
