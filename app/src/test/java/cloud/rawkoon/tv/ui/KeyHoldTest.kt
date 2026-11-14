package cloud.rawkoon.tv.ui

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyHoldTest {
    private val down = KeyEvent.ACTION_DOWN
    private val up = KeyEvent.ACTION_UP

    @Test
    fun `a single press is not a hold`() {
        KeyHold.track(KeyEvent.KEYCODE_DPAD_RIGHT, down, 0)
        assertFalse(KeyHold.held)
    }

    @Test
    fun `repeats are a hold until the key is released`() {
        KeyHold.track(KeyEvent.KEYCODE_DPAD_RIGHT, down, 0)
        KeyHold.track(KeyEvent.KEYCODE_DPAD_RIGHT, down, 3)
        assertTrue(KeyHold.held)
        KeyHold.track(KeyEvent.KEYCODE_DPAD_RIGHT, up, 0)
        assertFalse(KeyHold.held)
    }

    @Test
    fun `releasing an arrow pressed earlier does not end the newer hold`() {
        KeyHold.track(KeyEvent.KEYCODE_DPAD_RIGHT, down, 4)
        KeyHold.track(KeyEvent.KEYCODE_DPAD_DOWN, down, 0)
        KeyHold.track(KeyEvent.KEYCODE_DPAD_DOWN, down, 1)
        KeyHold.track(KeyEvent.KEYCODE_DPAD_RIGHT, up, 0)
        assertTrue(KeyHold.held)
        KeyHold.track(KeyEvent.KEYCODE_DPAD_DOWN, up, 0)
        assertFalse(KeyHold.held)
    }

    @Test
    fun `reset ends a hold whose release never came`() {
        KeyHold.track(KeyEvent.KEYCODE_DPAD_LEFT, down, 2)
        KeyHold.reset()
        assertFalse(KeyHold.held)
    }

    @Test
    fun `OK and other keys leave the hold alone`() {
        KeyHold.track(KeyEvent.KEYCODE_DPAD_DOWN, down, 2)
        KeyHold.track(KeyEvent.KEYCODE_DPAD_CENTER, up, 0)
        assertTrue(KeyHold.held)
        KeyHold.track(KeyEvent.KEYCODE_DPAD_DOWN, up, 0)
        assertFalse(KeyHold.held)
    }
}
