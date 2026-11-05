package com.focusinbox.app

enum class WidgetAction { START, RESUME, ANALYZE, END, SETUP }

data class WidgetState(
    val timerState: String, val token: Long, val end: Long, val remaining: Long,
    val minutes: Int, val loop: Boolean, val pending: Long, val busy: Boolean,
    val ready: Boolean, val lastState: String=""
) {
    val active get()=timerState=="RUNNING" || timerState=="PAUSED"
    val primary: WidgetAction get()=when {
        !ready -> WidgetAction.SETUP
        timerState=="PAUSED" -> WidgetAction.RESUME
        timerState=="RUNNING" -> WidgetAction.ANALYZE
        else -> WidgetAction.START
    }
    val primaryEnabled get()=primary!=WidgetAction.ANALYZE || (!busy && pending>0)
    fun allows(action: WidgetAction,expectedToken: Long): Boolean = token==expectedToken && when(action) {
        WidgetAction.START -> ready && timerState=="IDLE"
        WidgetAction.RESUME -> ready && timerState=="PAUSED"
        WidgetAction.ANALYZE -> ready && timerState=="RUNNING" && !busy && pending>0
        WidgetAction.END -> active
        WidgetAction.SETUP -> false // Only the explicit settings PendingIntent opens an Activity.
    }
    fun remainingAt(now: Long)=when(timerState) {
        "RUNNING" -> (end-now).coerceAtLeast(0)
        "PAUSED" -> remaining.coerceAtLeast(0)
        else -> minutes*60_000L
    }
    companion object {
        fun time(millis: Long): String {
            val seconds=(millis.coerceAtLeast(0)+999)/1000
            return "%02d:%02d".format(java.util.Locale.ROOT,seconds/60,seconds%60)
        }
    }
}
