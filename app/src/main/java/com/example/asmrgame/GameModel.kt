package com.example.asmrgame
import kotlin.random.Random

enum class ToolType(val displayName: String) {
    NAIL_GRINDER("Nail Grinder"),
    WET_WIPE("Wet Wipe"),
    TWEEZERS("Tweezers"),
    CREAM_TUBE("Cream Tube")
}

enum class GameStep(
    val title: String,
    val subtitle: String,
    val recommendedTool: ToolType
) {
    CLEAN_DIRT(
        title = "Fix the toes: Clean the dirt",
        subtitle = "Wipe off all mud and dirt with the wet wipe",
        recommendedTool = ToolType.WET_WIPE
    ),
    PLUCK_SPLINTERS(
        title = "Fix the toes: Remove splinters",
        subtitle = "Use tweezers to pluck the sharp splinters out",
        recommendedTool = ToolType.TWEEZERS
    ),
    TRIM_NAILS(
        title = "Fix the toes: Trim overgrown nails",
        subtitle = "Grind down overgrown nails with the nail grinder",
        recommendedTool = ToolType.NAIL_GRINDER
    ),
    APPLY_CREAM(
        title = "Fix the toes: Apply soothing cream",
        subtitle = "Rub lotion over the foot until it glows",
        recommendedTool = ToolType.CREAM_TUBE
    ),
    COMPLETED(
        title = "Toes are perfectly restored!",
        subtitle = "All cleaned, manicured and moisturized!",
        recommendedTool = ToolType.CREAM_TUBE
    )
}

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
    var radiusX: Float,
    var radiusY: Float,
    var rotationDeg: Float,
    var trimProgress: Float = 0f // 0f = full overgrown, 1f = fully trimmed
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
