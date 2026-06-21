package com.buzbuz.smartautoclicker.feature.throwlet

class HelperSessionManager(
    private val sessionFactory: (HelperMode, HelperLane) -> HelperSession = { mode, lane ->
        HelperSession(
            key = HelperSessionKey(lane),
            mode = mode,
            lane = lane,
            railController = NoopRailController(),
            detectionController = NoopDetectionController(),
            gestureController = DefaultGestureController(),
        )
    },
) {
    private val sessions = linkedMapOf<HelperLane, HelperSession>()
    val activeSessions: Map<HelperLane, HelperSession> get() = sessions.toMap()
    var onSessionStopped: ((HelperLane, HelperSession) -> Unit)? = null

    fun start(mode: HelperMode, lane: HelperLane, detectOnStart: Boolean = true, pokemonName: String? = null): HelperSession {
        ThrowletLog.i("manager start mode=$mode lane=$lane detectOnStart=$detectOnStart override=${pokemonName ?: "<none>"}")
        if (lane == HelperLane.FULL) {
            stop(HelperLane.SPLIT_TOP)
            stop(HelperLane.SPLIT_BOTTOM)
        } else {
            stop(HelperLane.FULL)
        }
        stop(lane)
        val session = sessionFactory(mode, lane)
        sessions[lane] = session
        if (detectOnStart) refresh(lane, pokemonName)
        session.railController.show()
        return session
    }

    fun stop(lane: HelperLane) {
        val session = sessions.remove(lane) ?: return
        ThrowletLog.i("manager stop lane=$lane")
        onSessionStopped?.invoke(lane, session)
        session.railController.stop()
    }

    fun stopAll() {
        HelperLane.entries.forEach(::stop)
    }

    fun refresh(lane: HelperLane, pokemonName: String? = null): CatchDetectionState? {
        ThrowletLog.i("manager refresh lane=$lane override=${pokemonName ?: "<none>"}")
        val session = sessions[lane] ?: run { ThrowletLog.w("manager refresh ignored missing session lane=$lane"); return null }
        session.railController.hide()
        val state = session.detectionController.detectOnce(session.mode, lane, pokemonName)
        session.detectionState = state
        session.railController.show()
        return state
    }

    fun isEmpty(): Boolean = sessions.isEmpty()
}
