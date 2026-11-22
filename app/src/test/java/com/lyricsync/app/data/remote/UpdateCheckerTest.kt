package com.lyricsync.app.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `isNewerVersion compares tags across length differences`() {
        assertTrue(UpdateChecker.isNewerVersion("v2.0", "1.9.9"))
        assertTrue(UpdateChecker.isNewerVersion("1.2.10", "v1.2.9"))
        assertFalse(UpdateChecker.isNewerVersion("v1.2", "1.2.0"))
        assertFalse(UpdateChecker.isNewerVersion("v1.2.3", "1.2.4"))
    }

    @Test
    fun `isTrustedGithubUrl rejects spoofed hosts and non-https schemes`() {
        assertTrue(UpdateChecker.isTrustedGithubUrl("https://github.com/MamiZ35/LyricSync/releases/download/v1.0/app.apk"))
        assertTrue(UpdateChecker.isTrustedGithubUrl("https://api.github.com/repos/MamiZ35/LyricSync/releases/latest"))
        assertTrue(UpdateChecker.isTrustedGithubUrl("https://objects.githubusercontent.com/file.apk"))
        assertFalse(UpdateChecker.isTrustedGithubUrl("http://github.com/MamiZ35/LyricSync/releases/latest"))
        assertFalse(UpdateChecker.isTrustedGithubUrl("https://github.com.evil.com/app.apk"))
        assertFalse(UpdateChecker.isTrustedGithubUrl("https://notgithub.com/app.apk"))
        assertFalse(UpdateChecker.isTrustedGithubUrl("not a url"))
    }
}
