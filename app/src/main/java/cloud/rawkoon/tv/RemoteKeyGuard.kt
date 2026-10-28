package cloud.rawkoon.tv

/** A remote gesture belongs to the page where it started, including its repeats and release. */
internal class RemoteKeyGuard {
    private data class Gesture(val owner: Any, val downTime: Long)
    private val gestures = HashMap<Int, Gesture>()
    private var lastBack: Long? = null

    fun accepts(key: Int, down: Boolean, repeat: Int, downTime: Long, owner: Any): Boolean {
        if (down && repeat == 0) gestures[key] = Gesture(owner, downTime)
        val gesture = if (down) gestures[key] else gestures.remove(key)
        return gesture?.owner === owner && gesture.downTime == downTime
    }

    /** Let the current layer finish responding before another Back can dismiss the next one. */
    fun backAllowed(time: Long): Boolean {
        if (lastBack?.let { time - it < BACK_SETTLE_MS } == true) return false
        lastBack = time
        return true
    }

    companion object {
        private const val BACK_SETTLE_MS = 300L
    }
}
