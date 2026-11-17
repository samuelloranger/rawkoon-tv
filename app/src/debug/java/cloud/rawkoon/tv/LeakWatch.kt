package cloud.rawkoon.tv

import leakcanary.AppWatcher

/** Debug builds: LeakCanary reports a screen, or its views, still in memory after it left the stack. */
fun watchRemoved(screen: Screen) {
    val reason = "${screen::class.simpleName} left the screen stack"
    AppWatcher.objectWatcher.expectWeaklyReachable(screen, reason)
    AppWatcher.objectWatcher.expectWeaklyReachable(screen.view, "$reason (its view)")
}
