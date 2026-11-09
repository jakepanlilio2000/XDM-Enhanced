package xdm.app.power

/**
 * Action to take when all active downloads and queues have finished (upstream issue #1430).
 */
enum class CompletionAction(val id: Int) {
    DO_NOTHING(0),
    SLEEP(1),
    SHUTDOWN(2),
    HIBERNATE(3);

    companion object {
        fun fromId(id: Int): CompletionAction = entries.firstOrNull { it.id == id } ?: DO_NOTHING
    }
}

