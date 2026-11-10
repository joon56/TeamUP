package com.modo.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileRulesTest {

    private val uuid = "123e4567-e89b-12d3-a456-426614174000"

    @Test
    fun key_projectAndDmFolders_withSafeExtension() {
        assertEquals("projects/p1/$uuid.pdf", FileRules.key(FileRules.Folder.Project("p1"), "중간 발표.PDF", uuid))
        assertEquals("dm/d_a_b/$uuid.jpg", FileRules.key(FileRules.Folder.Direct("d_a_b"), "사진.jpg", uuid))
    }

    @Test
    fun key_dropsOddOrMissingExtensions() {
        assertEquals("projects/p1/$uuid", FileRules.key(FileRules.Folder.Project("p1"), "README", uuid))
        assertEquals("projects/p1/$uuid", FileRules.key(FileRules.Folder.Project("p1"), "보고서.한글", uuid))
        assertEquals("projects/p1/$uuid", FileRules.key(FileRules.Folder.Project("p1"), "a.toolongextension", uuid))
        assertEquals("projects/p1/$uuid.hwp", FileRules.key(FileRules.Folder.Project("p1"), "과제.hwp", uuid))
    }

    @Test
    fun key_onlyAllowsStorageSafeCharacters() {
        // 저장소 키에 쓸 수 없는 글자가 섞인 id는 거부
        assertTrue(FileRules.isSafeKey("projects/p1/$uuid.pdf"))
        assertFalse(FileRules.isSafeKey("projects/한글/$uuid.pdf"))
    }

    @Test
    fun sizeLimit_is50MB() {
        assertTrue(FileRules.fits(50L * 1024 * 1024))
        assertFalse(FileRules.fits(50L * 1024 * 1024 + 1))
        assertFalse(FileRules.fits(0))
    }

    @Test
    fun mime_fallsBackFromExtension() {
        assertEquals("application/pdf", FileRules.mimeOf(null, "a.pdf"))
        assertEquals("image/png", FileRules.mimeOf("image/png", "a.bin"))
        assertEquals("application/x-hwp", FileRules.mimeOf("application/octet-stream", "과제.hwp"))
        assertEquals("application/octet-stream", FileRules.mimeOf(null, "noext"))
    }
}
