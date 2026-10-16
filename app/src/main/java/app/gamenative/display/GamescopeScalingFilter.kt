package app.gamenative.display

import app.gamenative.R

/**
 * A `scaling_filter` value gamescope accepts, with the label the QuickMenu
 * shows. [feature] is the advertised `feature_support` bit that must be
 * present for the entry to be offered (null = always offered). [usesSharpness]
 * mirrors gamescope's own `UpscaleFilterUsesSharpness`: only these filters read
 * the `scaling_sharpness` value, so it is the gate for the Sharpness control.
 */
enum class GamescopeScalingFilter(
    val id: String,
    val labelRes: Int,
    val feature: GamescopeFeature?,
    val usesSharpness: Boolean = false,
) {
    // "Integer" is gamescope's own `pixel` filter (there is no `integer` string);
    // it is offered unconditionally — gamescope advertises the pixel filter
    // unconditionally, so a capability gate would always read true.
    LINEAR("linear", R.string.gamescope_filter_linear, null),
    NEAREST("nearest", R.string.gamescope_filter_nearest, null),
    PIXEL("pixel", R.string.gamescope_filter_integer, null),
    FSR("fsr", R.string.gamescope_filter_fsr, null, usesSharpness = true),
    // Our extra filter, appended after SteamOS's five in the QuickMenu's order:
    // gamescope accepts `sgsr` and it is a working filter. It carries the
    // SGSR_FILTER advertised bit, which the QuickMenu gates it on.
    SGSR("sgsr", R.string.gamescope_filter_sgsr, GamescopeFeature.SGSR_FILTER, usesSharpness = true),
    NIS("nis", R.string.gamescope_filter_nis, null, usesSharpness = true);

    companion object {
        /** The filter a stored/command id names, or null when it is unknown/absent. */
        fun fromId(id: String?): GamescopeScalingFilter? =
            id?.takeIf { it.isNotEmpty() }?.let { v -> entries.firstOrNull { it.id == v } }
    }
}
