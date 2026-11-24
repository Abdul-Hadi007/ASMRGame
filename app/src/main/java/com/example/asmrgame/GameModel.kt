package com.example.asmrgame

import android.graphics.RectF

enum class GameScreen {
    HOME,
    LEVEL_SELECT,
    GAMEPLAY,
    SETTINGS
}

enum class ToolType(val displayName: String) {
    NAIL_GRINDER("Nail Grinder"),
    WET_WIPE("Wet Wipe"),
    TWEEZERS("Tweezers"),
    CREAM_TUBE("Cream Tube")
}

enum class GameStep(
    val stepNumber: Int,
    val title: String,
    val subtitle: String,
    val recommendedTool: ToolType
) {
    CLEAN_DIRT(
        stepNumber = 1,
        title = "Wipe off the mud",
        subtitle = "Drag the wet wipe over each muddy spot.",
        recommendedTool = ToolType.WET_WIPE
    ),
    PLUCK_SPLINTERS(
        stepNumber = 2,
        title = "Pluck the splinters",
        subtitle = "Drag the tweezers to pinch and pull out each splinter.",
        recommendedTool = ToolType.TWEEZERS
    ),
    TRIM_NAILS(
        stepNumber = 3,
        title = "Buff damaged nails",
        subtitle = "Drag the nail grinder across every overgrown nail.",
        recommendedTool = ToolType.NAIL_GRINDER
    ),
    APPLY_CREAM(
        stepNumber = 4,
        title = "Apply & peel mask",
        subtitle = "Spread cream over the foot, let dry, then peel off!",
        recommendedTool = ToolType.CREAM_TUBE
    ),
    COMPLETED(
        stepNumber = 4,
        title = "Toes are restored!",
        subtitle = "All cleaned, manicured and glowing!",
        recommendedTool = ToolType.CREAM_TUBE
    )
}

/**
 * Shared normalized foot coordinate definitions verified against the 500x899 visible
 * bounds of foot_clean.png / foot_dirty.png (cropped from 516x915 at [8,8,508,907])
 * and aligned 1:1 with the 475x850 visible foot bounds of the cream/peel 1024x1024 frames
 * (cropped at [274,87,749,937]).
 */
object FootAnchors {

    data class NailAnchor(
        val index: Int,
        val name: String,
        val normLeft: Float,
        val normTop: Float,
        val normRight: Float,
        val normBottom: Float,
        val normCenterX: Float,
        val normCenterY: Float,
        val rotationDeg: Float
    )

    data class MudAnchor(
        val id: Int,
        val name: String,
        val normX: Float,
        val normY: Float,
        val radiusRatio: Float
    )

    data class SplinterAnchor(
        val id: Int,
        val normX: Float,
        val normY: Float,
        val angleDeg: Float,
        val lengthRatio: Float
    )

    // 5 Nails ordered Pinky (left) -> Big (right), toes pointing downward
    val NAILS = listOf(
        NailAnchor(0, "Pinky Toe", 0.0540f, 0.8142f, 0.1420f, 0.8732f, 0.0980f, 0.8437f, -8f),
        NailAnchor(1, "Ring Toe", 0.1760f, 0.8665f, 0.2720f, 0.9344f, 0.2240f, 0.9004f, -4f),
        NailAnchor(2, "Middle Toe", 0.3240f, 0.8977f, 0.4420f, 0.9722f, 0.3830f, 0.9349f, 0f),
        NailAnchor(3, "Index Toe", 0.5040f, 0.9199f, 0.6320f, 0.9956f, 0.5680f, 0.9577f, 3f),
        NailAnchor(4, "Big Toe", 0.6740f, 0.8865f, 0.9120f, 0.9900f, 0.7930f, 0.9383f, 6f)
    )

    // 6 Mud Spots verified against foot_dirty.png - foot_clean.png difference clusters
    val MUD_SPOTS = listOf(
        MudAnchor(1, "Heel Main", 0.4820f, 0.1891f, 0.140f),
        MudAnchor(2, "Midfoot Large", 0.4340f, 0.4461f, 0.180f),
        MudAnchor(3, "Outer Arch", 0.6900f, 0.4449f, 0.095f),
        MudAnchor(4, "Right Ball", 0.6900f, 0.6185f, 0.130f),
        MudAnchor(5, "Center Toe Base", 0.5020f, 0.6796f, 0.105f),
        MudAnchor(6, "Big Toe Mud", 0.7080f, 0.6941f, 0.105f)
    )

    // 3 Splinters positioned cleanly on skin in shared foot space
    val SPLINTERS = listOf(
        SplinterAnchor(1, 0.41f, 0.27f, 35f, 0.055f),
        SplinterAnchor(2, 0.58f, 0.51f, -40f, 0.052f),
        SplinterAnchor(3, 0.33f, 0.59f, 60f, 0.055f)
    )

    /**
     * Exact tool contact tip inside each 1024x1024 in-use tool bitmap (normalized 0..1).
     */
    fun getToolContactNormalized(tool: ToolType): Pair<Float, Float> = when (tool) {
        ToolType.WET_WIPE -> 0.50f to 0.50f        // Center of unfolded wipe cloth
        ToolType.TWEEZERS -> 0.845f to 0.90f       // Precision pinching tips at bottom-right
        ToolType.NAIL_GRINDER -> 0.75f to 0.90f    // Spinning grinder bit tip at bottom-right
        ToolType.CREAM_TUBE -> 0.40f to 0.90f      // Cream nozzle & dollop tip at bottom
    }
}

data class MudSpot(
    val id: Int,
    val name: String,
    var x: Float,
    var y: Float,
    var radius: Float,
    var cleanProgress: Float = 0f // 0f = muddy, 1f = wiped clean
)

data class Splinter(
    val id: Int,
    var x: Float,
    var y: Float,
    var angleDeg: Float,
    var length: Float = 42f,
    var isRemoved: Boolean = false,
    var pullDistance: Float = 0f,
    var isPinching: Boolean = false
)

data class ToeNail(
    val index: Int,
    val name: String,
    var x: Float,
    var y: Float,
    var bounds: RectF,
    var radiusX: Float,
    var radiusY: Float,
    var rotationDeg: Float,
    var trimProgress: Float = 0f // 0f = full damaged/overgrown, 1f = fully buffed
)

enum class ParticleType {
    SPARK,
    DUST,
    SPARKLE,
    CREAM_DROP,
    CONFETTI,
    MOISTURE
}

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var size: Float,
    var color: Int,
    var alpha: Float = 1f,
    var type: ParticleType = ParticleType.SPARKLE,
    var rotation: Float = 0f,
    var rotationSpeed: Float = 0f,
    var life: Float = 1f,
    val decay: Float = 0.03f
) {
    fun update(): Boolean {
        x += vx
        y += vy
        rotation += rotationSpeed
        life -= decay
        alpha = life.coerceIn(0f, 1f)
        if (type == ParticleType.DUST || type == ParticleType.CONFETTI) {
            vy += 0.35f // Gravity
        } else if (type == ParticleType.SPARKLE) {
            vy -= 0.15f // Float upward
            size *= 0.98f
        }
        return life > 0f
    }
}
