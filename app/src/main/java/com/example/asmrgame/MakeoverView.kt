package com.example.asmrgame

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class MakeoverView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // --- Sound and Haptics ---
    val soundManager = SoundManager(context)

    // --- Game State ---
    var currentStep: GameStep = GameStep.CLEAN_DIRT
        private set
    var activeTool: ToolType = ToolType.WET_WIPE

    // Listeners for UI updates
    var onStepProgressChanged: ((GameStep, Float) -> Unit)? = null
    var onStepCompletedListener: ((GameStep) -> Unit)? = null
    var onLevelCompletedListener: (() -> Unit)? = null

    // --- Layers ---
    private var dirtBitmap: Bitmap? = null
    private var dirtCanvas: Canvas? = null
    private var dirtFadeAlpha: Float = 1f

    private var creamBitmap: Bitmap? = null
    private var creamCanvas: Canvas? = null
    private var creamFadeAlpha: Float = 1f
    private var creamAbsorbed = false

    // Foot base geometry
    private val footPath = Path()
    private val footBounds = RectF()
    private val nails = mutableListOf<ToeNail>()
    private val splinters = mutableListOf<Splinter>()
    private val dirtSamplePoints = mutableListOf<PointF>()
    private val cleanedSampleIndices = mutableSetOf<Int>()

    private val creamSamplePoints = mutableListOf<PointF>()
    private val coveredCreamIndices = mutableSetOf<Int>()

    // Particles
    private val particles = mutableListOf<Particle>()

    // Touch & Tool Tracking
    private var touchX = -100f
    private var touchY = -100f
    private var lastTouchX = -100f
    private var lastTouchY = -100f
    private var isTouching = false
    private var grinderRotation = 0f

    // Hint Tracking
    private var isHintActive = false
    private var hintProgress = 0f
    private var hintAnimator: ValueAnimator? = null

    // Paints
    private val footPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val footContourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#E4B49F")
    }
    private val toeCreasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.parseColor("#DF9D87")
    }
    private val nailBedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFC4D0")
    }
    private val nailTipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFFFFF")
    }
    private val overgrownNailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#C9B273")
    }
    private val overgrownNailDetailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#8E753E")
    }
    private val erasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.STROKE
        strokeWidth = 110f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val creamDepositPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 95f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.argb(220, 255, 253, 245)
    }
    private val creamHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 35f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.argb(160, 255, 255, 255)
    }
    private val splinterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 3f
        color = Color.parseColor("#4E342E")
    }
    private val splinterHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(120, 229, 115, 115)
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val toolShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(60, 0, 0, 0)
    }
    private val layerAlphaPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var activeSplinter: Splinter? = null

    init {
        // Continuous animation loop for particles and tool effects
        post(object : Runnable {
            override fun run() {
                updateFrame()
                invalidate()
                postDelayed(this, 16)
            }
        })
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        SpriteManager.load(context)
        buildFootGeometry(w.toFloat(), h.toFloat())
        initDirtLayer(w, h)
        initCreamLayer(w, h)
    }

    private fun buildFootGeometry(w: Float, h: Float) {
        // Center the foot nicely in the canvas
        val centerX = w * 0.5f
        val centerY = h * 0.48f
        val footWidth = minOf(w * 0.72f, 440f)
        val footHeight = footWidth * 1.72f

        footBounds.set(
            centerX - footWidth * 0.5f,
            centerY - footHeight * 0.5f,
            centerX + footWidth * 0.5f,
            centerY + footHeight * 0.5f
        )

        footPath.reset()
        // Top view of a stylized right foot with spread toes
        val left = footBounds.left
        val right = footBounds.right
        val top = footBounds.top + footHeight * 0.16f // toe base line
        val bottom = footBounds.bottom

        // Heel at bottom center
        footPath.moveTo(centerX - footWidth * 0.22f, bottom - footHeight * 0.05f)
        // Outer edge towards pinky side (right side)
        footPath.cubicTo(
            right + footWidth * 0.04f, bottom - footHeight * 0.35f,
            right + footWidth * 0.02f, top + footHeight * 0.18f,
            right - footWidth * 0.05f, top
        )
        // Ball of foot curve under toes
        footPath.cubicTo(
            centerX + footWidth * 0.2f, top - footHeight * 0.04f,
            centerX - footWidth * 0.2f, top - footHeight * 0.04f,
            left + footWidth * 0.05f, top
        )
        // Inner arch (left side)
        footPath.cubicTo(
            left - footWidth * 0.02f, top + footHeight * 0.22f,
            left + footWidth * 0.14f, bottom - footHeight * 0.35f,
            centerX - footWidth * 0.22f, bottom - footHeight * 0.05f
        )
        // Round heel
        footPath.cubicTo(
            centerX - footWidth * 0.15f, bottom,
            centerX + footWidth * 0.15f, bottom,
            centerX + footWidth * 0.18f, bottom - footHeight * 0.05f
        )
        footPath.close()

        // Configure smooth foot skin gradient
        footPaint.shader = LinearGradient(
            footBounds.left, footBounds.top,
            footBounds.right, footBounds.bottom,
            intArrayOf(
                Color.parseColor("#FFF0E6"),
                Color.parseColor("#FCE4D6"),
                Color.parseColor("#F8CDBA")
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )

        // Generate 5 Toes with precise nail beds and coordinates
        nails.clear()
        val toeBaseY = top
        val toeConfigs = listOf(
            // Big toe (Index 0, Left)
            ToeConfig("Big Toe", left + footWidth * 0.15f, toeBaseY - footHeight * 0.10f, 40f, 52f, -8f),
            // Index toe
            ToeConfig("Index Toe", left + footWidth * 0.35f, toeBaseY - footHeight * 0.11f, 31f, 43f, -4f),
            // Middle toe
            ToeConfig("Middle Toe", left + footWidth * 0.53f, toeBaseY - footHeight * 0.10f, 29f, 41f, 2f),
            // Ring toe
            ToeConfig("Ring Toe", left + footWidth * 0.70f, toeBaseY - footHeight * 0.08f, 27f, 38f, 7f),
            // Pinky toe
            ToeConfig("Pinky Toe", left + footWidth * 0.86f, toeBaseY - footHeight * 0.06f, 24f, 33f, 12f)
        )

        for ((idx, cfg) in toeConfigs.withIndex()) {
            nails.add(
                ToeNail(
                    index = idx,
                    name = cfg.name,
                    x = cfg.x,
                    y = cfg.y,
                    radiusX = cfg.rx,
                    radiusY = cfg.ry,
                    rotationDeg = cfg.rot,
                    trimProgress = 0f
                )
            )
        }

        // Setup 3 splinters for the Tweezers step
        splinters.clear()
        splinters.add(
            Splinter(
                id = 1,
                x = left + footWidth * 0.28f,
                y = footBounds.centerY() - footHeight * 0.12f,
                angleDeg = 35f,
                length = 42f
            )
        )
        splinters.add(
            Splinter(
                id = 2,
                x = footBounds.centerX() + footWidth * 0.12f,
                y = footBounds.centerY() + footHeight * 0.08f,
                angleDeg = -45f,
                length = 38f
            )
        )
        splinters.add(
            Splinter(
                id = 3,
                x = left + footWidth * 0.22f,
                y = footBounds.bottom - footHeight * 0.22f,
                angleDeg = 70f,
                length = 45f
            )
        )

        // Setup sample points for dirt cleaning progress
        dirtSamplePoints.clear()
        val gridStep = 28f
        var sx = footBounds.left
        while (sx <= footBounds.right) {
            var sy = footBounds.top - footHeight * 0.12f
            while (sy <= footBounds.bottom) {
                // Keep points reasonably inside the foot/toes area
                val dx = (sx - footBounds.centerX()) / (footWidth * 0.52f)
                val dy = (sy - footBounds.centerY()) / (footHeight * 0.55f)
                if (dx * dx + dy * dy <= 1.05f) {
                    dirtSamplePoints.add(PointF(sx, sy))
                }
                sy += gridStep
            }
            sx += gridStep
        }
        cleanedSampleIndices.clear()

        // Setup sample points for cream application
        creamSamplePoints.clear()
        creamSamplePoints.addAll(dirtSamplePoints)
        coveredCreamIndices.clear()
    }

    private data class ToeConfig(
        val name: String,
        val x: Float,
        val y: Float,
        val rx: Float,
        val ry: Float,
        val rot: Float
    )

    private fun initDirtLayer(w: Int, h: Int) {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cvs = Canvas(bmp)

        // Draw realistic dirt stains asset directly over the foot
        val stainsSprite = SpriteManager.dirtStains
        if (stainsSprite != null) {
            // Scale to cover foot sole, arch, and toes
            val stainsRect = RectF(
                footBounds.left - footBounds.width() * 0.05f,
                footBounds.top - footBounds.height() * 0.04f,
                footBounds.right + footBounds.width() * 0.05f,
                footBounds.bottom + footBounds.height() * 0.04f
            )
            cvs.drawBitmap(stainsSprite, null, stainsRect, null)
        } else {
            // Fallback to sprite sheet dirt overlay
            SpriteManager.dirtOverlay?.let { dirtSprite ->
                cvs.drawBitmap(dirtSprite, null, footBounds, null)
            }
        }

        // Additional mud splatters to ensure rich wipe coverage on toes and sole
        val mudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        val mudColors = intArrayOf(
            Color.parseColor("#4A2E18"),
            Color.parseColor("#5D4037"),
            Color.parseColor("#6D4C41"),
            Color.parseColor("#795548")
        )

        val rng = Random(42)
        for (i in 0 until 28) {
            val cx = footBounds.centerX() + (rng.nextFloat() - 0.5f) * footBounds.width() * 0.85f
            val cy = footBounds.centerY() + (rng.nextFloat() - 0.5f) * footBounds.height() * 0.90f
            val rad = 25f + rng.nextFloat() * 45f

            mudPaint.color = mudColors[rng.nextInt(mudColors.size)]
            mudPaint.alpha = 160 + rng.nextInt(70)

            cvs.drawCircle(cx, cy, rad, mudPaint)
        }

        dirtBitmap = bmp
        dirtCanvas = cvs
        dirtFadeAlpha = 1f
    }

    private fun initCreamLayer(w: Int, h: Int) {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        creamBitmap = bmp
        creamCanvas = Canvas(bmp)
        creamFadeAlpha = 1f
        creamAbsorbed = false
    }

    // --- Frame Update (Particles, Grinder Spin, Animations) ---

    private fun updateFrame() {
        if (activeTool == ToolType.NAIL_GRINDER && isTouching) {
            grinderRotation = (grinderRotation + 35f) % 360f
        }

        // Update particles
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            if (!p.update()) {
                iter.remove()
            }
        }
    }

    // --- Main Rendering ---

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw Clean Base Foot & Toes
        drawBaseFoot(canvas)

        // 2. Draw Overgrown Nails Layer (drawn on top of clean nail beds)
        drawOvergrownNails(canvas)

        // 3. Draw Splinters (Tweezers target)
        drawSplinters(canvas)

        // 4. Draw Dirt Layer (with PorterDuff.Mode.CLEAR erase holes and fade-out)
        dirtBitmap?.let { bmp ->
            if (dirtFadeAlpha > 0f) {
                val alphaInt = (dirtFadeAlpha * 255).toInt().coerceIn(0, 255)
                layerAlphaPaint.alpha = alphaInt
                canvas.drawBitmap(bmp, 0f, 0f, layerAlphaPaint)
            }
        }

        // 5. Draw Cream Layer (with soothing absorb fade into sparkle glow)
        creamBitmap?.let { bmp ->
            if (creamFadeAlpha > 0f) {
                val alphaInt = (creamFadeAlpha * 255).toInt().coerceIn(0, 255)
                layerAlphaPaint.alpha = alphaInt
                canvas.drawBitmap(bmp, 0f, 0f, layerAlphaPaint)
            }
        }

        // 6. Draw Particles (dust, sparks, sparkles, bubbles, confetti)
        drawParticles(canvas)

        // 7. Draw Hint Guide (if active)
        if (isHintActive) {
            drawHintGuidance(canvas)
        }

        // 8. Draw Active Dragged Tool (follows touch naturally)
        if (isTouching) {
            drawActiveTool(canvas, touchX, touchY)
        }
    }

    // --- Layer 1: Base Foot & Toes ---

    private fun drawBaseFoot(canvas: Canvas) {
        val footSprite = SpriteManager.cleanFoot
        if (footSprite != null) {
            // Soft drop shadow under the foot
            val shadowRect = RectF(footBounds).apply { offset(10f, 16f) }
            canvas.drawBitmap(footSprite, null, shadowRect, toolShadowPaint)
            // Main clean foot sprite
            canvas.drawBitmap(footSprite, null, footBounds, null)
            return
        }

        // Procedural fallback
        canvas.save()
        canvas.translate(8f, 14f)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(35, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawPath(footPath, shadowPaint)
        for (nail in nails) {
            canvas.drawOval(
                nail.x - nail.radiusX * 1.3f,
                nail.y - nail.radiusY * 1.3f,
                nail.x + nail.radiusX * 1.3f,
                nail.y + nail.radiusY * 1.3f,
                shadowPaint
            )
        }
        canvas.restore()

        // Main foot sole & top
        canvas.drawPath(footPath, footPaint)
        canvas.drawPath(footPath, footContourPaint)

        // Toe bases & toe pads
        for (nail in nails) {
            canvas.save()
            canvas.rotate(nail.rotationDeg, nail.x, nail.y)

            // Toe Flesh Pad
            val toePadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    nail.x, nail.y,
                    nail.radiusY * 1.4f,
                    Color.parseColor("#FFF3EC"),
                    Color.parseColor("#F7CEBA"),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawOval(
                nail.x - nail.radiusX * 1.25f,
                nail.y - nail.radiusY * 1.25f,
                nail.x + nail.radiusX * 1.25f,
                nail.y + nail.radiusY * 1.25f,
                toePadPaint
            )
            canvas.drawOval(
                nail.x - nail.radiusX * 1.25f,
                nail.y - nail.radiusY * 1.25f,
                nail.x + nail.radiusX * 1.25f,
                nail.y + nail.radiusY * 1.25f,
                footContourPaint
            )

            // Toe Knuckle / Crease Lines
            canvas.drawLine(
                nail.x - nail.radiusX * 0.7f, nail.y + nail.radiusY * 0.75f,
                nail.x + nail.radiusX * 0.7f, nail.y + nail.radiusY * 0.75f,
                toeCreasePaint
            )
            canvas.drawLine(
                nail.x - nail.radiusX * 0.5f, nail.y + nail.radiusY * 0.95f,
                nail.x + nail.radiusX * 0.5f, nail.y + nail.radiusY * 0.95f,
                toeCreasePaint
            )

            // Clean, pristine manicured nail bed (Base Layer)
            val nailBedRect = RectF(
                nail.x - nail.radiusX * 0.85f,
                nail.y - nail.radiusY * 0.95f,
                nail.x + nail.radiusX * 0.85f,
                nail.y + nail.radiusY * 0.35f
            )
            canvas.drawRoundRect(nailBedRect, 18f, 18f, nailBedPaint)

            // White clean French tip
            val tipRect = RectF(
                nail.x - nail.radiusX * 0.85f,
                nail.y - nail.radiusY * 0.95f,
                nail.x + nail.radiusX * 0.85f,
                nail.y - nail.radiusY * 0.65f
            )
            canvas.drawRoundRect(tipRect, 14f, 14f, nailTipPaint)

            // Glossy nail highlight reflection
            val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(140, 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = 3f
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(
                nail.x - nail.radiusX * 0.4f, nail.y - nail.radiusY * 0.5f,
                nail.x - nail.radiusX * 0.4f, nail.y + nail.radiusY * 0.1f,
                glossPaint
            )

            canvas.restore()
        }

        // Subtle skin wrinkles & sole contour lines
        val soleWrinklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.argb(60, 220, 140, 120)
        }
        val cx = footBounds.centerX()
        val cy = footBounds.centerY()
        canvas.drawArc(RectF(cx - 80f, cy - 30f, cx + 80f, cy + 30f), 30f, 120f, false, soleWrinklePaint)
        canvas.drawArc(RectF(cx - 60f, cy + 60f, cx + 60f, cy + 110f), 20f, 130f, false, soleWrinklePaint)
    }

    // --- Layer 2: Overgrown Nails Layer ---

    private fun drawOvergrownNails(canvas: Canvas) {
        val nailSprites = SpriteManager.overgrownNails
        for ((idx, nail) in nails.withIndex()) {
            val remain = 1f - nail.trimProgress
            if (remain <= 0.01f) continue // fully trimmed!

            val sprite = nailSprites.getOrNull(idx)
            if (sprite != null) {
                canvas.save()
                canvas.rotate(nail.rotationDeg, nail.x, nail.y)
                val nw = nail.radiusX * 2.3f
                val nh = nw * (sprite.height.toFloat() / sprite.width.toFloat()) * remain
                val nailRect = RectF(
                    nail.x - nw * 0.5f,
                    nail.y - nail.radiusY * 0.8f - nh * 0.35f,
                    nail.x + nw * 0.5f,
                    nail.y + nail.radiusY * 0.5f + nh * 0.65f
                )
                layerAlphaPaint.alpha = (remain * 255).toInt().coerceIn(0, 255)
                canvas.drawBitmap(sprite, null, nailRect, layerAlphaPaint)
                canvas.restore()
            } else {
                canvas.save()
                canvas.rotate(nail.rotationDeg, nail.x, nail.y)
                val overgrownHeight = (nail.radiusY * 1.1f) * remain
                val topY = nail.y - nail.radiusY * 0.95f - overgrownHeight
                val nailW = nail.radiusX * 0.88f

                val overgrownPath = Path().apply {
                    moveTo(nail.x - nailW, nail.y - nail.radiusY * 0.65f)
                    lineTo(nail.x - nailW * 1.15f, topY + overgrownHeight * 0.4f)
                    lineTo(nail.x - nailW * 0.5f, topY - 4f)
                    lineTo(nail.x - nailW * 0.1f, topY + 6f)
                    lineTo(nail.x + nailW * 0.4f, topY - 6f)
                    lineTo(nail.x + nailW * 1.12f, topY + overgrownHeight * 0.35f)
                    lineTo(nail.x + nailW, nail.y - nail.radiusY * 0.65f)
                    close()
                }

                canvas.drawPath(overgrownPath, overgrownNailPaint)
                canvas.drawPath(overgrownPath, overgrownNailDetailPaint)
                canvas.restore()
            }
        }
    }

    // --- Layer 3: Splinters Layer ---

    private fun drawSplinters(canvas: Canvas) {
        val hairSprite = SpriteManager.hairOverlay
        if (hairSprite != null) {
            val remainSplinters = splinters.count { !it.isRemoved }
            if (remainSplinters > 0) {
                layerAlphaPaint.alpha = ((remainSplinters / splinters.size.toFloat()) * 255).toInt().coerceIn(0, 255)
                canvas.drawBitmap(hairSprite, null, footBounds, layerAlphaPaint)
            }
        }

        for (splinter in splinters) {
            if (splinter.isRemoved) continue

            canvas.save()
            canvas.translate(splinter.x, splinter.y)
            canvas.rotate(splinter.angleDeg)

            // Red irritated skin halo around puncture
            val haloRadius = 18f * (1f - (splinter.pullDistance / 80f)).coerceAtLeast(0.3f)
            canvas.drawCircle(0f, 0f, haloRadius, splinterHaloPaint)

            // Splinter body shifting along pull distance
            val currentOffset = -splinter.pullDistance
            val splinterPath = Path().apply {
                moveTo(-3.5f, currentOffset)
                lineTo(3.5f, currentOffset)
                lineTo(0f, currentOffset - splinter.length)
                close()
            }
            canvas.drawPath(splinterPath, splinterPaint)

            // Tiny barb hook on splinter
            canvas.drawLine(
                1f, currentOffset - splinter.length * 0.5f,
                5f, currentOffset - splinter.length * 0.6f,
                splinterPaint
            )

            canvas.restore()
        }
    }

    // --- Layer 6: Particles ---

    private fun drawParticles(canvas: Canvas) {
        for (p in particles) {
            particlePaint.color = p.color
            particlePaint.alpha = (p.alpha * 255).toInt().coerceIn(0, 255)

            when (p.type) {
                ParticleType.SPARK -> {
                    canvas.drawCircle(p.x, p.y, p.size, particlePaint)
                }
                ParticleType.DUST -> {
                    canvas.drawCircle(p.x, p.y, p.size, particlePaint)
                }
                ParticleType.SPARKLE -> {
                    drawStar(canvas, p.x, p.y, p.size, p.rotation, particlePaint)
                }
                ParticleType.CREAM_DROP -> {
                    canvas.drawCircle(p.x, p.y, p.size, particlePaint)
                }
                ParticleType.CONFETTI -> {
                    canvas.save()
                    canvas.translate(p.x, p.y)
                    canvas.rotate(p.rotation)
                    canvas.drawRect(-p.size, -p.size * 0.5f, p.size, p.size * 0.5f, particlePaint)
                    canvas.restore()
                }
                ParticleType.MOISTURE -> {
                    canvas.drawCircle(p.x, p.y, p.size, particlePaint)
                }
            }
        }
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, radius: Float, rot: Float, paint: Paint) {
        val path = Path()
        val numPoints = 4
        val innerRadius = radius * 0.35f
        for (i in 0 until numPoints * 2) {
            val r = if (i % 2 == 0) radius else innerRadius
            val angle = rot + (i * Math.PI / numPoints).toFloat()
            val x = cx + cos(angle) * r
            val y = cy + sin(angle) * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    // --- Hint Visual Guidance ---

    private fun drawHintGuidance(canvas: Canvas) {
        val target = getHintTargetPoint()
        if (target != null) {
            val pulseRadius = 35f + 20f * hintProgress
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 6f
                color = Color.parseColor("#FFCA28")
                alpha = ((1f - hintProgress) * 255).toInt().coerceIn(0, 255)
            }
            canvas.drawCircle(target.x, target.y, pulseRadius, ringPaint)

            // Little animated star indicator
            val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#FFA000")
            }
            drawStar(canvas, target.x, target.y - 45f + 10f * hintProgress, 18f, hintProgress * 180f, starPaint)
        }
    }

    private fun getHintTargetPoint(): PointF? {
        return when (currentStep) {
            GameStep.CLEAN_DIRT -> {
                // Find first uncleaned dirt sample point
                dirtSamplePoints.firstOrNull { !cleanedSampleIndices.contains(dirtSamplePoints.indexOf(it)) }
                    ?: PointF(footBounds.centerX(), footBounds.centerY())
            }
            GameStep.PLUCK_SPLINTERS -> {
                splinters.firstOrNull { !it.isRemoved }?.let { PointF(it.x, it.y) }
            }
            GameStep.TRIM_NAILS -> {
                nails.firstOrNull { it.trimProgress < 1f }?.let { PointF(it.x, it.y) }
            }
            GameStep.APPLY_CREAM -> {
                creamSamplePoints.firstOrNull { !coveredCreamIndices.contains(creamSamplePoints.indexOf(it)) }
                    ?: PointF(footBounds.centerX(), footBounds.centerY())
            }
            GameStep.COMPLETED -> null
        }
    }

    // --- Tool Drawing on Canvas ---

    private fun drawActiveTool(canvas: Canvas, x: Float, y: Float) {
        canvas.save()
        // Tilt tool naturally for pleasant tactile perspective
        val toolAngle = -22f
        canvas.rotate(toolAngle, x, y)

        val sprite = when (activeTool) {
            ToolType.NAIL_GRINDER -> SpriteManager.toolGrinder
            ToolType.WET_WIPE -> SpriteManager.toolWipes
            ToolType.TWEEZERS -> SpriteManager.toolTweezers
            ToolType.CREAM_TUBE -> SpriteManager.toolCream
        }

        if (sprite != null) {
            val toolW = 145f
            val toolH = toolW * (sprite.height.toFloat() / sprite.width.toFloat())
            val destRect = RectF(
                x - toolW * 0.45f,
                y - toolH * 0.85f,
                x + toolW * 0.55f,
                y + toolH * 0.15f
            )
            val shadowRect = RectF(destRect).apply { offset(10f, 14f) }
            canvas.drawBitmap(sprite, null, shadowRect, toolShadowPaint)
            canvas.drawBitmap(sprite, null, destRect, null)
        } else {
            when (activeTool) {
                ToolType.WET_WIPE -> drawWetWipeTool(canvas, x, y)
                ToolType.TWEEZERS -> drawTweezersTool(canvas, x, y)
                ToolType.NAIL_GRINDER -> drawNailGrinderTool(canvas, x, y)
                ToolType.CREAM_TUBE -> drawCreamTubeTool(canvas, x, y)
            }
        }

        canvas.restore()
    }

    private fun drawWetWipeTool(canvas: Canvas, x: Float, y: Float) {
        // Soft drop shadow
        canvas.drawOval(x - 45f, y - 35f, x + 45f, y + 45f, toolShadowPaint)

        // Folded textured wet wipe cloth
        val clothPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                x - 50f, y - 50f, x + 50f, y + 50f,
                Color.parseColor("#E1F5FE"),
                Color.parseColor("#B3E5FC"),
                Shader.TileMode.CLAMP
            )
        }
        val clothRect = RectF(x - 50f, y - 50f, x + 50f, y + 50f)
        canvas.drawRoundRect(clothRect, 22f, 22f, clothPaint)

        // Folds / creases
        val foldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#81D4FA")
        }
        canvas.drawArc(RectF(x - 40f, y - 40f, x + 40f, y + 20f), 20f, 140f, false, foldPaint)
        canvas.drawArc(RectF(x - 30f, y - 10f, x + 30f, y + 40f), 10f, 120f, false, foldPaint)

        // Water droplet icon in center
        val dropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#0288D1")
        }
        val dropPath = Path().apply {
            moveTo(x, y - 16f)
            cubicTo(x + 10f, y - 4f, x + 10f, y + 10f, x, y + 10f)
            cubicTo(x - 10f, y + 10f, x - 10f, y - 4f, x, y - 16f)
            close()
        }
        canvas.drawPath(dropPath, dropPaint)
    }

    private fun drawTweezersTool(canvas: Canvas, x: Float, y: Float) {
        val isPinching = activeSplinter != null
        val pinchSpread = if (isPinching) 3f else 12f

        // Metal body
        val metalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                x - 20f, y - 120f, x + 20f, y,
                Color.parseColor("#ECEFF1"),
                Color.parseColor("#B0BEC5"),
                Shader.TileMode.CLAMP
            )
            style = Paint.Style.FILL
        }
        val tipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#78909C")
            style = Paint.Style.STROKE
            strokeWidth = 4f
            strokeCap = Paint.Cap.ROUND
        }

        // Left Arm
        val leftArm = Path().apply {
            moveTo(x - 14f, y - 130f)
            lineTo(x - 18f, y - 60f)
            lineTo(x - pinchSpread, y)
            lineTo(x - pinchSpread - 4f, y)
            lineTo(x - 20f, y - 60f)
            lineTo(x - 16f, y - 130f)
            close()
        }
        // Right Arm
        val rightArm = Path().apply {
            moveTo(x + 14f, y - 130f)
            lineTo(x + 18f, y - 60f)
            lineTo(x + pinchSpread, y)
            lineTo(x + pinchSpread + 4f, y)
            lineTo(x + 20f, y - 60f)
            lineTo(x + 16f, y - 130f)
            close()
        }

        canvas.drawPath(leftArm, metalPaint)
        canvas.drawPath(rightArm, metalPaint)
        canvas.drawLine(x - pinchSpread, y, x - pinchSpread, y + 5f, tipPaint)
        canvas.drawLine(x + pinchSpread, y, x + pinchSpread, y + 5f, tipPaint)

        // Gripping ribs on handle
        val ribPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#78909C")
        }
        for (k in 0..4) {
            val ry = y - 85f + k * 8f
            canvas.drawLine(x - 17f, ry, x - 13f, ry, ribPaint)
            canvas.drawLine(x + 13f, ry, x + 17f, ry, ribPaint)
        }
    }

    private fun drawNailGrinderTool(canvas: Canvas, x: Float, y: Float) {
        // Drop shadow
        canvas.drawRect(x - 16f, y - 140f, x + 16f, y, toolShadowPaint)

        // Handheld Body (Teal / Mint ergonomic handle)
        val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                x - 22f, y - 150f, x + 22f, y - 40f,
                Color.parseColor("#80CBC4"),
                Color.parseColor("#00897B"),
                Shader.TileMode.CLAMP
            )
        }
        val handleRect = RectF(x - 20f, y - 150f, x + 20f, y - 35f)
        canvas.drawRoundRect(handleRect, 16f, 16f, handlePaint)

        // Power LED / Button
        val ledPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#69F0AE")
        }
        canvas.drawCircle(x, y - 80f, 5f, ledPaint)

        // Metal Chuck / Collar
        val chuckPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CFD8DC")
        }
        canvas.drawRect(x - 11f, y - 35f, x + 11f, y - 18f, chuckPaint)

        // Chrome Rotary Shaft
        val shaftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#90A4AE")
            strokeWidth = 5f
        }
        canvas.drawLine(x, y - 18f, x, y - 5f, shaftPaint)

        // Grinding abrasive diamond head (textured spinning bit)
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(grinderRotation)

        val bitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                0f, 0f, 14f,
                Color.parseColor("#F48FB1"),
                Color.parseColor("#C2185B"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawOval(-10f, -14f, 10f, 14f, bitPaint)

        // Spin texture notches
        val notchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#880E4F")
        }
        canvas.drawLine(-8f, 0f, 8f, 0f, notchPaint)
        canvas.drawLine(0f, -10f, 0f, 10f, notchPaint)

        canvas.restore()
    }

    private fun drawCreamTubeTool(canvas: Canvas, x: Float, y: Float) {
        // Drop shadow
        canvas.drawOval(x - 25f, y - 140f, x + 25f, y + 10f, toolShadowPaint)

        // Pastel Pink / Coral Lotion Tube Body
        val tubePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                x - 26f, y - 145f, x + 26f, y - 25f,
                Color.parseColor("#F8BBD0"),
                Color.parseColor("#EC407A"),
                Shader.TileMode.CLAMP
            )
        }
        val tubePath = Path().apply {
            moveTo(x - 26f, y - 145f)
            lineTo(x + 26f, y - 145f)
            lineTo(x + 20f, y - 40f)
            lineTo(x + 10f, y - 24f)
            lineTo(x - 10f, y - 24f)
            lineTo(x - 20f, y - 40f)
            close()
        }
        canvas.drawPath(tubePath, tubePaint)

        // Crimp fold at end
        val crimpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D81B60")
            strokeWidth = 4f
        }
        canvas.drawLine(x - 27f, y - 145f, x + 27f, y - 145f, crimpPaint)

        // Label Badge
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 255, 255, 255)
        }
        canvas.drawRoundRect(RectF(x - 16f, y - 105f, x + 16f, y - 65f), 8f, 8f, badgePaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D81B60")
            textSize = 10f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawText("SOOTHE", x, y - 82f, textPaint)

        // White Nozzle
        val nozzlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFFFFF")
        }
        canvas.drawRect(x - 6f, y - 24f, x + 6f, y - 10f, nozzlePaint)

        // Squeezed dollop of cream at tip
        val dollopPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFFDF7")
        }
        canvas.drawCircle(x, y - 4f, 8f, dollopPaint)
        canvas.drawCircle(x + 3f, y - 2f, 5f, dollopPaint)
    }

    // --- Touch Interaction & Core Layer Mechanics ---

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        touchX = event.x
        touchY = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isTouching = true
                lastTouchX = touchX
                lastTouchY = touchY
                handleTouchDown(touchX, touchY)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                isTouching = true
                handleTouchMove(lastTouchX, lastTouchY, touchX, touchY)
                lastTouchX = touchX
                lastTouchY = touchY
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTouching = false
                handleTouchUp()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTouchDown(x: Float, y: Float) {
        if (isHintActive) {
            dismissHint()
        }

        when (activeTool) {
            ToolType.TWEEZERS -> {
                // Check if touching near an unremoved splinter
                activeSplinter = splinters.firstOrNull { splinter ->
                    !splinter.isRemoved && hypot(x - splinter.x, y - splinter.y) < 65f
                }
                activeSplinter?.let {
                    it.isPinching = true
                    soundManager.vibrateTick(100)
                }
            }
            ToolType.WET_WIPE -> {
                eraseDirtAt(x, y, x, y)
            }
            ToolType.CREAM_TUBE -> {
                applyCreamAt(x, y, x, y)
            }
            ToolType.NAIL_GRINDER -> {
                grindNailAt(x, y)
            }
        }
    }

    private fun handleTouchMove(prevX: Float, prevY: Float, currX: Float, currY: Float) {
        when (activeTool) {
            ToolType.WET_WIPE -> {
                eraseDirtAt(prevX, prevY, currX, currY)
            }
            ToolType.TWEEZERS -> {
                activeSplinter?.let { splinter ->
                    val dist = hypot(currX - splinter.x, currY - splinter.y)
                    splinter.pullDistance = dist

                    // Emit resistance haptic while pulling
                    soundManager.vibrateTick(60)

                    // If pulled past threshold (40px), pop it free!
                    if (dist > 40f && !splinter.isRemoved) {
                        splinter.isRemoved = true
                        activeSplinter = null
                        soundManager.playPluckSound()
                        soundManager.vibratePop()

                        // Burst of healing green/gold sparkles
                        spawnSparkles(splinter.x, splinter.y, 20, Color.parseColor("#81C784"))
                        spawnSparkles(splinter.x, splinter.y, 10, Color.parseColor("#FFD54F"))

                        checkSplintersCompletion()
                    }
                }
            }
            ToolType.NAIL_GRINDER -> {
                grindNailAt(currX, currY)
            }
            ToolType.CREAM_TUBE -> {
                applyCreamAt(prevX, prevY, currX, currY)
            }
        }
    }

    private fun handleTouchUp() {
        activeSplinter?.let {
            it.isPinching = false
            it.pullDistance = 0f
            activeSplinter = null
        }
    }

    // --- Step 1: Dirt Erasing (PorterDuff.Mode.CLEAR) ---

    private fun eraseDirtAt(x0: Float, y0: Float, x1: Float, y1: Float) {
        dirtCanvas?.let { cvs ->
            cvs.drawLine(x0, y0, x1, y1, erasePaint)
            cvs.drawCircle(x1, y1, erasePaint.strokeWidth * 0.5f, erasePaint)

            // Spawn wipe water droplet / foam particles
            if (Random.nextFloat() < 0.45f) {
                particles.add(
                    Particle(
                        x = x1 + (Random.nextFloat() - 0.5f) * 40f,
                        y = y1 + (Random.nextFloat() - 0.5f) * 40f,
                        vx = (Random.nextFloat() - 0.5f) * 2f,
                        vy = -Random.nextFloat() * 1.5f,
                        size = 5f + Random.nextFloat() * 8f,
                        color = Color.argb(160, 225, 245, 254),
                        type = ParticleType.MOISTURE,
                        decay = 0.05f
                    )
                )
            }

            soundManager.playWipeSound()
            soundManager.vibrateTick(50)

            // Calculate percentage cleaned
            calculateDirtCleaned(x1, y1)
        }
    }

    private fun calculateDirtCleaned(touchX: Float, touchY: Float) {
        val eraseRadius = erasePaint.strokeWidth * 0.55f
        for ((idx, pt) in dirtSamplePoints.withIndex()) {
            if (!cleanedSampleIndices.contains(idx)) {
                if (hypot(touchX - pt.x, touchY - pt.y) <= eraseRadius) {
                    cleanedSampleIndices.add(idx)
                }
            }
        }

        val total = dirtSamplePoints.size.toFloat().coerceAtLeast(1f)
        val cleanedPercent = (cleanedSampleIndices.size / total)

        onStepProgressChanged?.invoke(GameStep.CLEAN_DIRT, cleanedPercent)

        // When >85% cleaned, smoothly fade out remaining dirt and advance
        if (cleanedPercent >= 0.85f && dirtFadeAlpha == 1f && currentStep == GameStep.CLEAN_DIRT) {
            triggerDirtFadeOut()
        }
    }

    private fun triggerDirtFadeOut() {
        soundManager.playChime()
        soundManager.vibrateSuccess()
        spawnSparkles(footBounds.centerX(), footBounds.centerY(), 35, Color.parseColor("#4FC3F7"))

        val animator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 550
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { va ->
                dirtFadeAlpha = va.animatedValue as Float
                invalidate()
            }
        }
        animator.start()

        advanceStep(GameStep.PLUCK_SPLINTERS)
    }

    // --- Step 2: Splinters Completion ---

    private fun checkSplintersCompletion() {
        val remaining = splinters.count { !it.isRemoved }
        val progress = (splinters.size - remaining) / splinters.size.toFloat()
        onStepProgressChanged?.invoke(GameStep.PLUCK_SPLINTERS, progress)

        if (remaining == 0 && currentStep == GameStep.PLUCK_SPLINTERS) {
            soundManager.playChime()
            soundManager.vibrateSuccess()
            spawnSparkles(footBounds.centerX(), footBounds.centerY(), 40, Color.parseColor("#81C784"))
            advanceStep(GameStep.TRIM_NAILS)
        }
    }

    // --- Step 3: Nail Grinding ---

    private fun grindNailAt(x: Float, y: Float) {
        for (nail in nails) {
            if (nail.trimProgress >= 1f) continue

            val dist = hypot(x - nail.x, y - nail.y)
            if (dist < nail.radiusX * 1.6f + 15f) {
                // Progress trimming this nail
                nail.trimProgress = (nail.trimProgress + 0.035f).coerceAtMost(1f)

                soundManager.playGrindSound()
                soundManager.vibrateGrind()

                // Spawn flying sparks and nail dust particles
                for (k in 0..2) {
                    particles.add(
                        Particle(
                            x = x + (Random.nextFloat() - 0.5f) * 10f,
                            y = y + (Random.nextFloat() - 0.5f) * 10f,
                            vx = (Random.nextFloat() - 0.5f) * 7f,
                            vy = (Random.nextFloat() - 0.8f) * 6f,
                            size = 3f + Random.nextFloat() * 4f,
                            color = if (Random.nextBoolean()) Color.parseColor("#FFE082") else Color.parseColor("#ECEFF1"),
                            type = ParticleType.DUST,
                            decay = 0.06f
                        )
                    )
                }

                // If nail just reached 1.0f, small sparkle pop
                if (nail.trimProgress >= 1f) {
                    soundManager.playChime()
                    spawnSparkles(nail.x, nail.y, 16, Color.parseColor("#FF80AB"))
                }

                checkNailsCompletion()
                break
            }
        }
    }

    private fun checkNailsCompletion() {
        val totalProgress = nails.map { it.trimProgress }.average().toFloat()
        onStepProgressChanged?.invoke(GameStep.TRIM_NAILS, totalProgress)

        if (nails.all { it.trimProgress >= 1f } && currentStep == GameStep.TRIM_NAILS) {
            soundManager.playChime()
            soundManager.vibrateSuccess()
            spawnSparkles(footBounds.centerX(), footBounds.top, 50, Color.parseColor("#FFD54F"))
            advanceStep(GameStep.APPLY_CREAM)
        }
    }

    // --- Step 4: Cream Layer & Sparkle Effect ---

    private fun applyCreamAt(x0: Float, y0: Float, x1: Float, y1: Float) {
        creamCanvas?.let { cvs ->
            cvs.drawLine(x0, y0, x1, y1, creamDepositPaint)
            cvs.drawCircle(x1, y1, creamDepositPaint.strokeWidth * 0.5f, creamDepositPaint)

            // Glossy center stroke
            cvs.drawLine(x0, y0, x1, y1, creamHighlightPaint)
            cvs.drawCircle(x1, y1, creamHighlightPaint.strokeWidth * 0.5f, creamHighlightPaint)

            soundManager.playCreamSound()
            soundManager.vibrateTick(45)

            // Calculate coverage
            val applyRadius = creamDepositPaint.strokeWidth * 0.5f
            for ((idx, pt) in creamSamplePoints.withIndex()) {
                if (!coveredCreamIndices.contains(idx)) {
                    if (hypot(x1 - pt.x, y1 - pt.y) <= applyRadius) {
                        coveredCreamIndices.add(idx)
                    }
                }
            }

            val total = creamSamplePoints.size.toFloat().coerceAtLeast(1f)
            val coverage = coveredCreamIndices.size / total
            onStepProgressChanged?.invoke(GameStep.APPLY_CREAM, coverage)

            // When >85% applied, smooth absorb into radiant sparkle glow
            if (coverage >= 0.85f && !creamAbsorbed && currentStep == GameStep.APPLY_CREAM) {
                absorbCreamIntoSparkles()
            }
        }
    }

    private fun absorbCreamIntoSparkles() {
        creamAbsorbed = true
        soundManager.playVictory()
        soundManager.vibrateSuccess()

        // Fade cream layer into radiant skin
        val animator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 750
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { va ->
                creamFadeAlpha = va.animatedValue as Float
                // Spawn radiant sparkles across the entire foot during absorption
                if (Random.nextFloat() < 0.6f) {
                    val rx = footBounds.left + Random.nextFloat() * footBounds.width()
                    val ry = footBounds.top + Random.nextFloat() * footBounds.height()
                    spawnSparkles(rx, ry, 3, Color.parseColor("#FFF9C4"))
                }
                invalidate()
            }
        }
        animator.start()

        // Confetti celebration
        spawnConfetti()

        advanceStep(GameStep.COMPLETED)
        onLevelCompletedListener?.invoke()
    }

    // --- Particle Spawners ---

    private fun spawnSparkles(cx: Float, cy: Float, count: Int, color: Int) {
        for (i in 0 until count) {
            val ang = Random.nextFloat() * 6.28f
            val spd = 2f + Random.nextFloat() * 7f
            particles.add(
                Particle(
                    x = cx,
                    y = cy,
                    vx = cos(ang) * spd,
                    vy = sin(ang) * spd,
                    size = 10f + Random.nextFloat() * 14f,
                    color = color,
                    type = ParticleType.SPARKLE,
                    rotation = Random.nextFloat() * 360f,
                    rotationSpeed = 6f + Random.nextFloat() * 12f,
                    decay = 0.025f
                )
            )
        }
    }

    private fun spawnConfetti() {
        val confettiColors = intArrayOf(
            Color.parseColor("#FF4081"),
            Color.parseColor("#7C4DFF"),
            Color.parseColor("#536DFE"),
            Color.parseColor("#00E676"),
            Color.parseColor("#FFD700"),
            Color.parseColor("#FF6D00")
        )
        for (i in 0 until 80) {
            particles.add(
                Particle(
                    x = footBounds.left + Random.nextFloat() * footBounds.width(),
                    y = footBounds.top - 50f + Random.nextFloat() * 150f,
                    vx = (Random.nextFloat() - 0.5f) * 10f,
                    vy = -2f - Random.nextFloat() * 8f,
                    size = 12f + Random.nextFloat() * 10f,
                    color = confettiColors[Random.nextInt(confettiColors.size)],
                    type = ParticleType.CONFETTI,
                    rotation = Random.nextFloat() * 360f,
                    rotationSpeed = 8f + Random.nextFloat() * 15f,
                    decay = 0.015f
                )
            )
        }
    }

    // --- Step Flow Management ---

    private fun advanceStep(next: GameStep) {
        val prev = currentStep
        currentStep = next
        onStepCompletedListener?.invoke(prev)
        // Automatically switch recommended tool for next step
        activeTool = next.recommendedTool
    }

    fun selectTool(tool: ToolType) {
        activeTool = tool
        soundManager.vibrateTick(70)
        invalidate()
    }

    fun showHint() {
        isHintActive = true
        hintAnimator?.cancel()
        hintAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000
            repeatCount = 3
            addUpdateListener { va ->
                hintProgress = va.animatedValue as Float
                invalidate()
            }
        }
        hintAnimator?.start()
        soundManager.playChime()
        soundManager.vibrateTick(120)
    }

    private fun dismissHint() {
        isHintActive = false
        hintAnimator?.cancel()
        hintAnimator = null
    }

    fun resetLevel() {
        dismissHint()
        currentStep = GameStep.CLEAN_DIRT
        activeTool = ToolType.WET_WIPE
        cleanedSampleIndices.clear()
        coveredCreamIndices.clear()
        particles.clear()

        for (nail in nails) {
            nail.trimProgress = 0f
        }
        for (splinter in splinters) {
            splinter.isRemoved = false
            splinter.pullDistance = 0f
            splinter.isPinching = false
        }

        if (width > 0 && height > 0) {
            initDirtLayer(width, height)
            initCreamLayer(width, height)
        }
        invalidate()
    }
}
