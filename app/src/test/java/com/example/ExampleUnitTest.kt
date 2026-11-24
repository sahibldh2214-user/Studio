package com.example

import com.example.data.remote.LanNetworkScanner
import com.example.model.FileTypeClassifier
import com.example.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun fileTypeClassifier_identifiesMediaAndDocuments() {
        assertEquals(MediaKind.FOLDER, FileTypeClassifier.classify("Movies", isDirectory = true))
        assertEquals(MediaKind.VIDEO, FileTypeClassifier.classify("trailer_4k.mkv", isDirectory = false))
        assertEquals(MediaKind.AUDIO, FileTypeClassifier.classify("soundtrack.flac", isDirectory = false))
        assertEquals(MediaKind.PDF, FileTypeClassifier.classify("manual.pdf", isDirectory = false))
        assertEquals(MediaKind.IMAGE, FileTypeClassifier.classify("poster.webp", isDirectory = false))
        assertEquals("1.5 MB", FileTypeClassifier.formatSize(1_572_864L))
    }

    @Test
    fun lanNetworkScanner_computesSubnetHostsWithCommonsNet() {
        val scanner = LanNetworkScanner()
        assertEquals("192.168.1.50/24", scanner.computeScanCidr("192.168.1.50", 16))
        assertEquals("10.0.0.12/28", scanner.computeScanCidr("10.0.0.12", 28))

        val hosts = scanner.enumerateSubnetHosts("192.168.1.50/24", excludeIp = "192.168.1.50")
        assertEquals(253, hosts.size)
        assertFalse(hosts.contains("192.168.1.50"))
        assertTrue(hosts.contains("192.168.1.1"))
        assertTrue(hosts.contains("192.168.1.254"))
    }
}
