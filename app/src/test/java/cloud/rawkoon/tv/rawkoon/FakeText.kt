package cloud.rawkoon.tv.rawkoon

/** Fixed English words for the row logic, which only asks [RawkoonText] for them. */
object FakeText : RawkoonText {
    override fun upcomingTitle() = "Coming soon"
    override fun requestsTitle() = "My requests"
    override fun today() = "Today"
    override fun tomorrow() = "Tomorrow"
    override fun episodes(count: Int) = "$count episodes"
    override fun inLibrary() = "in library"
    override fun inProgress() = "in progress"

    override fun stateLabel(state: TitleState): String? = when (state) {
        TitleState.Requestable -> null
        TitleState.Pending -> "pending"
        is TitleState.Processing -> if (state.downloading) "downloading ${state.percent ?: ""}".trim() else "approved"
        TitleState.Partial -> "partial"
        TitleState.Available -> "available"
        is TitleState.Refused -> "refused"
        TitleState.Skipped -> "skipped"
    }
}
