package app.gamenative.display

import com.winlator.container.Container
import java.io.File

/**
 * The gamescope effects a session should have in effect: one value per effect
 * domain. A null value means the domain was never specified, so it is left
 * untouched on re-apply.
 */
data class GamescopeState(
    val filter: GamescopeScalingFilter? = null,
    val sharpness: Int? = null,
    val look: GamescopeLook? = null,
    val scaler: GamescopeScalingMode? = null,
)

/**
 * Where a container's last-used gamescope effects live, plus the bundled LUT
 * location. The stored keys are unchanged (existing containers keep working);
 * this is the single code reference for them.
 */
object GamescopeSettings {
    const val EXTRA_FILTER = "gamescopeScalingFilter"
    const val EXTRA_SHARPNESS = "gamescopeScalingSharpness"
    const val EXTRA_LOOK = "gamescopeLook"
    const val EXTRA_SCALER = "gamescopeScalingMode"

    // Sharpness is stored in the UI's own units: 0..SHARPNESS_STEPS, higher =
    // sharper — the scale the QuickMenu row shows. gamescope's own convar runs
    // the other way (0 = maximum sharpening), so the raw value is derived once,
    // only at the point the command is sent (see GamescopeCommandQueue).
    const val SHARPNESS_STEPS = 5
    const val SHARPNESS_DEFAULT = SHARPNESS_STEPS

    /** gamescope's raw `scaling_sharpness` (0 = maximum sharpening) for a stored UI value. */
    fun sharpnessRawValue(ui: Int): Int =
        (SHARPNESS_STEPS - ui.coerceIn(0, SHARPNESS_STEPS)) * 4

    /**
     * The `--scaler <mode>` argument for a session's launch command, or "" for the
     * default. `--scaler` seeds the initial scaler at launch; the QuickMenu's
     * Scaling Mode row then changes it live over the X property, so this is only
     * the session's starting value.
     */
    @JvmStatic
    fun scalerArg(container: Container?): String {
        val mode = GamescopeScalingMode.fromId(container?.getExtra(EXTRA_SCALER, ""))
            ?: GamescopeScalingMode.DEFAULT
        return if (mode == GamescopeScalingMode.DEFAULT) "" else " --scaler ${mode.id}"
    }

    /**
     * The session's X socket — gamescope's own Xwayland, whose root window the
     * live scaling-mode property is set on. Mirrors the launcher's
     * `GAMESCOPE_X11_DISPLAY` (`<imagefs>/usr/tmp/.X11-unix/X0`); the socket is
     * not at the usual `/tmp/.X11-unix` in the app sandbox.
     */
    @JvmStatic
    fun x11DisplayPath(imageFsRootDir: File): File =
        File(imageFsRootDir, "usr/tmp/.X11-unix/X0")

    /** Directory the bundled gamescope payload is extracted under (`filesDir/gamescope`). */
    @JvmStatic
    fun gamescopeDir(filesDir: File): File = File(filesDir, "gamescope")

    /** Absolute path of the bundled Gamma-2.2 LUT for [look] under [gamescopeDir]. */
    fun lutPath(gamescopeDir: File, look: GamescopeLook): String =
        File(gamescopeDir, "share/gamescope/looks/${look.lutFileName}").absolutePath

    /** The effects last used for [container], for seeding a session at start. */
    @JvmStatic
    fun fromContainer(container: Container?): GamescopeState {
        if (container == null) return GamescopeState()
        val filter = GamescopeScalingFilter.fromId(container.getExtra(EXTRA_FILTER, ""))
        // Sharpness is always seeded: the default is maximum sharpening, so a
        // container that never opened the tab still runs the value the tab shows
        // as its default instead of gamescope's own built-in 2.
        val sharpness = container.getExtra(EXTRA_SHARPNESS, "").orEmpty()
            .toIntOrNull()?.coerceIn(0, SHARPNESS_STEPS) ?: SHARPNESS_DEFAULT
        val look = container.getExtra(EXTRA_LOOK, "").orEmpty()
            .takeIf { it.isNotEmpty() }?.let(GamescopeLook::fromId)
        val scaler = GamescopeScalingMode.fromId(container.getExtra(EXTRA_SCALER, ""))
        return GamescopeState(filter = filter, sharpness = sharpness, look = look, scaler = scaler)
    }
}
