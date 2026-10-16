package app.gamenative.display

/**
 * gamescope `feature_support` bits the QuickMenu gates settings on. The wire
 * value is a bitmask whose bit N is feature id N; each entry carries its own
 * mask so the UI never spells the hex literal inline.
 */
enum class GamescopeFeature(val mask: Int) {
    LOOK(0x40),
    SGSR_FILTER(0x200);

    companion object {
        /** Whether [feature] is advertised in a `features=0x<hex>` mask. */
        fun advertised(mask: Int, feature: GamescopeFeature): Boolean = mask and feature.mask != 0
    }
}
