package cloud.rawkoon.tv.data

import java.io.File
import java.util.concurrent.PriorityBlockingQueue
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class ImagePipelineTest {
    @Test
    fun visibleTasksRunBeforeBackgroundThenFirstComeFirstServed() {
        val queue = PriorityBlockingQueue<PriorityTask>()
        val ran = mutableListOf<String>()
        queue += PriorityTask(true, 0) { ran += "bg0" }
        queue += PriorityTask(false, 1) { ran += "fg1" }
        queue += PriorityTask(true, 2) { ran += "bg2" }
        queue += PriorityTask(false, 3) { ran += "fg3" }
        while (queue.isNotEmpty()) queue.take().run()
        assertEquals(listOf("fg1", "fg3", "bg0", "bg2"), ran)
    }

    @Test
    fun failureIsRememberedUntilItExpires() {
        var now = 1_000L
        val memory = FailureMemory(30_000, 10) { now }
        assertFalse(memory.recent("a"))
        memory.add("a")
        assertTrue(memory.recent("a"))
        now += 29_999
        assertTrue(memory.recent("a"))
        now += 2
        assertFalse(memory.recent("a"))
    }

    @Test
    fun failureMemoryIsBounded() {
        val memory = FailureMemory(30_000, 2)
        listOf("a", "b", "c", "d").forEach(memory::add)
        assertTrue(memory.recent("d"))
        assertFalse(memory.recent("a"))
    }

    @Test
    fun trimDropsLeastRecentlyUsedAndSkipsDownloads() {
        val dir = kotlin.io.path.createTempDirectory("trim").toFile()
        try {
            fun make(name: String, age: Long) = File(dir, name).apply {
                writeBytes(ByteArray(10))
                setLastModified(10_000_000L - age)
            }
            make("new", 1_000)
            make("mid", 2_000)
            make("old", 3_000)
            val fresh = make("fresh.tmp", 0)
            val stale = make("stale.tmp", 9_000_000)
            trimDir(dir, 10_000_000L, budget = 20, staleTmpMs = 1_000_000)
            assertTrue(File(dir, "new").exists())
            assertTrue(File(dir, "mid").exists())
            assertFalse(File(dir, "old").exists())
            assertTrue(fresh.exists())
            assertFalse(stale.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
