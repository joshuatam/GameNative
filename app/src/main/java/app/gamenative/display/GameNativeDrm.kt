package app.gamenative.display

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.Surface
import app.gamenative.PluviaApp
import app.gamenative.events.AndroidEvent
import com.winlator.widget.FrameRating

/** JNI binding for `libgamenative_drm.so` (Gamescope display-server mode); failures are logged under `GN_DRM`. */
object GameNativeDrm {
    init {
        System.loadLibrary("gamenative_drm")
    }

    /** [nativeRetargetSurface] return when no daemon session is running. */
    const val E_NO_SESSION: Int = -2

    /**
     * Called from native (`libgamenative_drm.so`) on the `PRESENTED_FRAMES` 0->1
     * edge — the first real frame on the panel. Broadcasts [AndroidEvent.ClearBootingSplash]
     * so the booting splash is dismissed without the app polling the daemon.
     */
    @JvmStatic
    fun clearBootingSplash() {
        PluviaApp.events.emit(AndroidEvent.ClearBootingSplash)
    }

    /**
     * Tell the daemon the panel's real refresh rate in whole hertz, before [nativeStart]:
     * it becomes the rate of the mode the daemon exposes, i.e. the refresh gamescope
     * advertises to clients, while the session's vblank grid stays the daemon's own
     * constant. The daemon ignores an out-of-range value (0 when the panel could not be
     * read) and keeps its own default, so a failed read leaves the session running.
     */
    @JvmStatic
    external fun nativeSetPanelRefreshHz(refreshHz: Int)

    /** Start the daemon: create the SurfaceControl layer on [surface], then serve [socketName]. */
    @JvmStatic
    external fun nativeStart(socketName: String, surface: Surface, width: Int, height: Int): Int

    /** Stop the daemon (sockets shut down, server thread joined, publish layer released). */
    @JvmStatic
    external fun nativeStop()

    /**
     * Adopt a new display window for a RUNNING session without ending it: rebuild
     * the publish layer from [surface] and re-present the retained last frame, so a
     * display power cycle keeps the daemon, its sockets and the gamescope connection
     * alive. Returns 0, [E_NO_SESSION] when no session is running (call [nativeStart]),
     * or another negative code when the layer could not be rebuilt.
     */
    @JvmStatic
    external fun nativeRetargetSurface(surface: Surface, width: Int, height: Int): Int

    /**
     * The live widget the daemon's per-published-frame upcall feeds. Registered where the widget
     * is created (XServerScreen) and cleared on teardown, so a disposed view is never touched.
     * The same instance is separately handed to the renderer via `setFrameRating` (the X11 tick
     * holder) — one object, two explicit references, both cleared with the session.
     */
    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var frameRating: FrameRating? = null

    /** Registers (or, with `null`, clears) the widget that [onFramePublished] feeds. */
    @JvmStatic
    fun setFrameRating(frameRating: FrameRating?) {
        this.frameRating = frameRating
    }

    /** Main-thread handler the native per-frame upcall marshals through. */
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Called from native (`libgamenative_drm.so`) on each new frame the daemon publishes
     * (gamescope's frames never tick the X11 present hook). The callback arrives on a native
     * thread, so the tick is posted to the main thread before it reaches the View, and goes
     * through `FrameRating.update()` — the same feeder the X11 arm uses — so one mechanism keeps
     * the frame-time ring and FrameRating's own stats live on both display paths.
     *
     * The widget is constructed, set `GONE` and handed off — never attached to a window — so its
     * own `post()` would only queue the runnable until attachment and the tick would never run;
     * a main-thread `Handler` owned here is the right shape. No-op when no session has
     * registered a widget.
     */
    @JvmStatic
    fun onFramePublished() {
        if (frameRating == null) return
        mainHandler.post { frameRating?.update() }
    }

    // -- input injector lifecycle -------------------------------------------

    /** Starts the wayland injector thread under <xdgRuntimeDir>/<waylandDisplay>; idempotent, returns 0 or a negative code. */
    @JvmStatic
    external fun nativeInjectorStart(xdgRuntimeDir: String, waylandDisplay: String): Int

    /** Cap gamescope's frame rate for this app (0 = uncapped). */
    @JvmStatic
    external fun nativeSetFpsLimit(fps: Int)

    /** Run one allowlisted gamescope concommand ("name [value]"); 0 when queued, negative when not allowlisted. */
    @JvmStatic
    external fun nativeGamescopeCommand(command: String): Int

    /** gamescope's feature_support + active_display_info as "features=0x<hex>;connector=<name>;display_flags=0x<hex>;rates=<r>,<r>"; empty fields until gamescope answers. */
    @JvmStatic
    external fun nativeDisplayInfo(): String

    /** Stop the injector thread (no-op when not running). */
    @JvmStatic
    external fun nativeInjectorStop()

    /**
     * Mark the session suspended (`true`) or resumed (`false`). Under the
     * auto/manual suspend policy the session's processes — gamescope included —
     * are frozen while suspended, so the injector holds gamescope commands and
     * replays them on resume instead of sending them to a frozen compositor.
     */
    @JvmStatic
    external fun nativeInjectorSetSuspended(suspended: Boolean)

    // -- input injector (no-ops until the injector connects to gamescope) ---

    /** Absolute pointer position in output coordinates. */
    @JvmStatic
    external fun nativePointerAbsolute(x: Int, y: Int)

    @JvmStatic
    external fun nativePointerMotion(dx: Int, dy: Int)

    /** Pointer button (evdev BTN_* code). */
    @JvmStatic
    external fun nativePointerButton(button: Int, pressed: Boolean)

    /** Scroll axis: 0 = vertical, 1 = horizontal; value in detents. */
    @JvmStatic
    external fun nativePointerAxis(axis: Int, value: Float)

    /** evdev key code. */
    @JvmStatic
    external fun nativeKey(keyCode: Int, pressed: Boolean)
}
