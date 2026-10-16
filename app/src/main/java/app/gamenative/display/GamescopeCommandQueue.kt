package app.gamenative.display

import timber.log.Timber
import java.io.File

/**
 * The gamescope session's **desired state**, held app-side and re-applied
 * idempotently whenever the session becomes applicable — at session start, on
 * resume, after a retarget, and whenever a domain changes.
 *
 * The desired state is the full set of effects that should be in effect, one
 * value per domain (scaling mode, scaling filter, scaling sharpness, look,
 * frame-rate cap).
 * Re-applying the whole set (rather than firing only the change) means a command
 * that is lost, coalesced or superseded cannot leave the session in a wrong
 * state: the next re-apply restores every domain. [beginSession] seeds the set
 * from the container's stored effects, so a session starts with the effects the
 * container last used, not just the changes made during it.
 *
 * While the session is suspended under the `auto`/`manual` policy, gamescope is
 * frozen too, so setters only update the state and the resume re-apply sends it.
 * One-shot actions (screenshot, forced repaint) are events, not state: held
 * while not applicable and flushed once, in order, on the next apply.
 */
object GamescopeCommandQueue {
    private val lock = Any()

    // Desired state, one variable per effect domain; null = never specified.
    private var filter: GamescopeScalingFilter? = null
    private var sharpness: Int? = null
    private var look: GamescopeLook? = null
    private var fpsLimit: Int? = null

    /** Desired Scaling Mode (the scaler); applied by spawning the guest client. */
    private var scaler: GamescopeScalingMode? = null

    /**
     * The scaler value last written to the session's X property. The mode is a
     * one-shot effect that persists on the X server (unlike a concommand, it is
     * not lost when a command is dropped), so it is spawned only when it
     * changes — one process per committed selection, never per re-apply.
     */
    private var spawnedScaler: GamescopeScalingMode? = null

    /** Directory the bundled LUTs live under; set with the session. */
    private var gamescopeDir: File? = null

    /** The session's X socket the scaler client is pointed at; set with the session. */
    private var x11DisplayPath: File? = null

    /** Pending one-shot actions, in issue order (deduplicated by name). */
    private val actions = LinkedHashMap<String, GamescopeConVar>()

    /** Applicable = the injector is connected and the session is not frozen. */
    private var applicable = false

    /**
     * Seed the desired state for a new session from [state], remember where the
     * bundled LUTs live, and record the session's X socket ([x11DisplayPath],
     * the target of the live scaling-mode client). Pending actions are dropped
     * (a new session); if the session is already applicable the state is applied
     * immediately.
     */
    @JvmStatic
    fun beginSession(gamescopeDir: File, x11DisplayPath: File, state: GamescopeState) {
        synchronized(lock) {
            this.gamescopeDir = gamescopeDir
            this.x11DisplayPath = x11DisplayPath
            filter = state.filter
            sharpness = state.sharpness
            look = state.look
            scaler = state.scaler
            // The session starts with `--scaler`, so the property already holds
            // this value; no spawn until it changes.
            spawnedScaler = state.scaler
            actions.clear()
        }
        reapply()
    }

    /**
     * Mark the session applicable (`true`: the compositor accepts commands) or
     * not (`false`: suspended or torn down). Becoming applicable re-applies the
     * whole desired state.
     */
    @JvmStatic
    fun setApplicable(value: Boolean) {
        var became = false
        synchronized(lock) {
            if (value && !applicable) became = true
            applicable = value
        }
        if (became) reapply()
    }

    /** Drop the whole desired state and applicability (a session is ending). */
    @JvmStatic
    fun reset() {
        synchronized(lock) {
            filter = null
            sharpness = null
            look = null
            fpsLimit = null
            scaler = null
            spawnedScaler = null
            gamescopeDir = null
            x11DisplayPath = null
            actions.clear()
            applicable = false
        }
    }

    /** Set the scaling filter; re-applied with the rest of the state. */
    fun setFilter(value: GamescopeScalingFilter) = update { filter = value }

    /** Set the Scaling Mode; spawned live only when it changes (see [spawnedScaler]). */
    fun setScaler(value: GamescopeScalingMode) = update { scaler = value }

    /** Set the scaling sharpness (UI units 0..SHARPNESS_STEPS); re-applied with the rest of the state. */
    fun setSharpness(value: Int) =
        update { sharpness = value.coerceIn(0, GamescopeSettings.SHARPNESS_STEPS) }

    /** Set the look; re-applied with the rest of the state. */
    fun setLook(value: GamescopeLook) = update { look = value }

    /** Set the frame-rate cap (0 = uncapped); re-applied with the rest of the state. */
    fun setFpsLimit(fps: Int) = update { fpsLimit = fps }

    /** Queue a one-shot action; sent once on the next apply (immediately when applicable). */
    fun submitAction(action: GamescopeConVar) {
        val sendNow: Boolean
        synchronized(lock) {
            sendNow = applicable
            if (!sendNow) actions[action.id] = action
        }
        if (sendNow) exec(action)
    }

    /**
     * Apply the whole desired state and flush pending actions. Idempotent: a
     * no-op while the session is not applicable.
     */
    fun reapply() {
        val f: GamescopeScalingFilter?
        val s: Int?
        val l: GamescopeLook?
        val fps: Int?
        val dir: File?
        val xdp: File?
        val acts: List<GamescopeConVar>
        val scalerToSpawn: GamescopeScalingMode?
        synchronized(lock) {
            if (!applicable) return
            f = filter
            s = sharpness
            l = look
            fps = fpsLimit
            dir = gamescopeDir
            xdp = x11DisplayPath
            acts = actions.values.toList()
            actions.clear()
            // Spawn the scaler client only when the mode changed since it was
            // last written; mark it written under the lock so concurrent
            // re-applies cannot double-spawn.
            scalerToSpawn = scaler?.takeIf { it != spawnedScaler }
            if (scalerToSpawn != null) spawnedScaler = scalerToSpawn
        }
        f?.let { exec(GamescopeConVar.SCALING_FILTER, it.id) }
        // The stored sharpness is in UI units; gamescope's convar wants the raw
        // value (0 = maximum), so this is the only place it is converted.
        s?.let { exec(GamescopeConVar.SCALING_SHARPNESS, GamescopeSettings.sharpnessRawValue(it).toString()) }
        l?.let { look ->
            // A bare set_look clears the Gamma 2.2 look; otherwise name its LUT.
            val path = if (look == GamescopeLook.OFF || dir == null) "" else GamescopeSettings.lutPath(dir, look)
            exec(GamescopeConVar.SET_LOOK, path)
        }
        fps?.let { GameNativeDrm.nativeSetFpsLimit(it) }
        if (scalerToSpawn != null && dir != null && xdp != null) {
            GamescopeScalingCommand.apply(dir, xdp, scalerToSpawn)
        }
        acts.forEach { exec(it) }
    }

    private inline fun update(change: () -> Unit) {
        val now: Boolean
        synchronized(lock) {
            change()
            now = applicable
        }
        if (now) reapply()
    }

    private fun exec(action: GamescopeConVar, value: String = "") {
        val command = if (value.isEmpty()) action.id else "${action.id} $value"
        runCatching {
            GameNativeDrm.nativeGamescopeCommand(command)
        }
            .onSuccess { Timber.i("GamescopeCommandQueue: applied '%s' (rc=%d)", command, it) }
            .onFailure { Timber.w(it, "GamescopeCommandQueue: '%s' failed", action.id) }
    }
}
