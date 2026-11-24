package com.example.asmrgame

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
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
    var activeTool: ToolType? = ToolType.WET_WIPE

    // Listeners for UI updates
    var onStepProgressChanged: ((GameStep, Float) -> Unit)? = null
    var onStepCompletedListener: ((GameStep) -> Unit)? = null
    var onLevelCompletedListener: (() -> Unit)? = null
    var onToolDeselectRequested: (() -> Unit)? = null

    // Preview / Debug inspection flags
    var showAnchorOverlay: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var previewPeelStage: Int? = null // null = normal gameplay, 0=full cream, 1=peel1, 2=peel2, 3=peel3
        set(value) {
            field = value
            invalidate()
        }
    var previewToolAtAnchor: Pair<ToolType, PointF>? = null
        set(value) {
            field = value
            invalidate()
        }

    // --- Shared Coordinate Space ---
    private val matBounds = RectF()
    private val footBounds = RectF()

    // --- Layers ---
    private var dirtBitmap: Bitmap? = null
    private var dirtCanvas: Canvas? = null
    private var dirtFadeAlpha: Float = 1f

    private var creamBitmap: Bitmap? = null
    private var creamCanvas: Canvas? = null
    private var creamFadeAlpha: Float = 1f
    private var footAlphaMask: Bitmap? = null

    // Peel-off mask state
    enum class MaskState {
        APPLYING,
        DRYING,
        PEELABLE,
        PEELED
    }
    private var maskState = MaskState.APPLYING
    private var maskDryProgress = 0f
    private var maskPeelProgress = 0f

    // Cool finishing animation state
    private var finishAnimProgress = 0f
    private var isFinishing = false

    // Interactive targets mapped in shared foot space
    private val mudSpots = mutableListOf<MudSpot>()
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
    private var grinderJitterPhase = 0f

    // Hint Tracking
    private var isHintActive = false
    private var hintProgress = 0f
    private var hintAnimator: ValueAnimator? = null

    // --- Pre-allocated Paints ---
    private val erasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.STROKE
        strokeWidth = 90f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val creamDepositPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 85f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.argb(215, 255, 167, 38)
    }
    private val creamHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 30f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.argb(165, 255, 236, 179)
    }
    private val maskClipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val promptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E65100")
        textSize = 32f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(8f, 0f, 3f, Color.argb(170, 255, 255, 255))
    }
    private val splinterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 3.5f
        color = Color.parseColor("#4E342E")
    }
    private val splinterHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(125, 229, 115, 115)
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val toolShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(55, 0, 0, 0)
    }
    private val layerAlphaPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val victoryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E65100")
        textSize = 36f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    // Debug Anchor Overlay Paints
    private val debugNailBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        color = Color.parseColor("#E91E63")
    }
    private val debugNailFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(45, 233, 30, 99)
    }
    private val debugMudCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
        color = Color.parseColor("#0288D1")
    }
    private val debugMudFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(40, 2, 136, 209)
    }
    private val debugCrosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.WHITE
    }
    private val debugLabelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(210, 38, 50, 56)
    }
    private val debugLabelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private var activeSplinter: Splinter? = null

    init {
        if (!isInEditMode) {
            post(object : Runnable {
                override fun run() {
                    updateFrame()
                    invalidate()
                    postDelayed(this, 16)
                }
            })
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        SpriteManager.load(context)
        buildSharedCoordinateSpace(w.toFloat(), h.toFloat())
        initDirtLayer(w, h)
        initCreamLayer(w, h)
    }

    /**
     * Computes matBounds and footBounds using the exact aspect ratios of treatment_mat (996x1272)
     * and the visible foot artwork (500x899), ensuring identical alignment across all screen sizes.
     */
    private fun buildSharedCoordinateSpace(w: Float, h: Float) {
        val padX = w * 0.03f
        val padY = h * 0.015f
        val availW = (w - padX * 2f).coerceAtLeast(1f)
        val availH = (h - padY * 2f).coerceAtLeast(1f)

        val matAspect = 996f / 1272f
        val matW: Float
        val matH: Float
        if (availW / availH <= matAspect) {
            matW = availW
            matH = availW / matAspect
        } else {
            matH = availH
            matW = availH * matAspect
        }

        val matLeft = (w - matW) * 0.5f
        val matTop = (h - matH) * 0.5f
        matBounds.set(matLeft, matTop, matLeft + matW, matTop + matH)

        // Position foot inside treatment_mat matching 03_gameplay mockup proportions:
        // Foot visible aspect = 500 / 899 = 0.55617
        val footAspect = 500f / 899f
        var footH = matH * 0.875f
        var footW = footH * footAspect
        if (footW > matW * 0.72f) {
            footW = matW * 0.72f
            footH = footW / footAspect
        }
        val footLeft = matBounds.centerX() - footW * 0.5f
        val footTop = matBounds.top + (matH - footH) * 0.44f
        footBounds.set(footLeft, footTop, footLeft + footW, footTop + footH)

        // Dynamically scale interactive brush strokes and text
        erasePaint.strokeWidth = footW * 0.22f
        creamDepositPaint.strokeWidth = footW * 0.20f
        creamHighlightPaint.strokeWidth = footW * 0.075f
        promptPaint.textSize = (w * 0.036f).coerceIn(22f, 40f)
        victoryTextPaint.textSize = (w * 0.045f).coerceIn(26f, 46f)
        debugLabelTextPaint.textSize = (footW * 0.042f).coerceIn(14f, 26f)

        // Build FootAlphaMask for keeping cream application strictly inside the foot contour
        SpriteManager.cleanFoot?.let { cleanBmp ->
            val mask = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val mc = Canvas(mask)
            mc.drawBitmap(cleanBmp, null, footBounds, null)
            footAlphaMask = mask
        }

        // 1. Map the 5 Nails from shared normalized coordinates
        nails.clear()
        for (a in FootAnchors.NAILS) {
            val rect = RectF(
                footBounds.left + a.normLeft * footW,
                footBounds.top + a.normTop * footH,
                footBounds.left + a.normRight * footW,
                footBounds.top + a.normBottom * footH
            )
            nails.add(
                ToeNail(
                    index = a.index,
                    name = a.name,
                    x = footBounds.left + a.normCenterX * footW,
                    y = footBounds.top + a.normCenterY * footH,
                    bounds = rect,
                    radiusX = rect.width() * 0.5f,
                    radiusY = rect.height() * 0.5f,
                    rotationDeg = a.rotationDeg,
                    trimProgress = 0f
                )
            )
        }

        // 2. Map the 6 Mud Spots from shared normalized coordinates
        mudSpots.clear()
        for (m in FootAnchors.MUD_SPOTS) {
            mudSpots.add(
                MudSpot(
                    id = m.id,
                    name = m.name,
                    x = footBounds.left + m.normX * footW,
                    y = footBounds.top + m.normY * footH,
                    radius = m.radiusRatio * footW,
                    cleanProgress = 0f
                )
            )
        }

        // 3. Map the 3 Splinters from shared normalized coordinates
        splinters.clear()
        for (s in FootAnchors.SPLINTERS) {
            splinters.add(
                Splinter(
                    id = s.id,
                    x = footBounds.left + s.normX * footW,
                    y = footBounds.top + s.normY * footH,
                    angleDeg = s.angleDeg,
                    length = s.lengthRatio * footH
                )
            )
        }

        // 4. Sample grid for overall dirt & cream coverage (excluding the 5 nail tips)
        dirtSamplePoints.clear()
        val gridStep = footW * 0.075f
        var sx = footBounds.left + footW * 0.14f
        while (sx <= footBounds.right - footW * 0.14f) {
            var sy = footBounds.top + footH * 0.08f
            while (sy <= footBounds.top + footH * 0.80f) {
                val dx = (sx - footBounds.centerX()) / (footW * 0.42f)
                val dy = (sy - (footBounds.top + footH * 0.44f)) / (footH * 0.42f)
                if (dx * dx + dy * dy <= 1.0f) {
                    dirtSamplePoints.add(PointF(sx, sy))
                }
                sy += gridStep
            }
            sx += gridStep
        }
        // Also ensure all 6 mud spot centers are in dirt sample points
        for (spot in mudSpots) {
            dirtSamplePoints.add(PointF(spot.x, spot.y))
        }
        cleanedSampleIndices.clear()

        creamSamplePoints.clear()
        creamSamplePoints.addAll(dirtSamplePoints)
        coveredCreamIndices.clear()
    }

    private fun initDirtLayer(w: Int, h: Int) {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cvs = Canvas(bmp)

        SpriteManager.dirtOnlyFoot?.let { dirtBmp ->
            cvs.drawBitmap(dirtBmp, null, footBounds, null)
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
    }

    private fun updateFrame() {
        if (activeTool == ToolType.NAIL_GRINDER && isTouching) {
            grinderJitterPhase += 0.65f
        }

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

        // 0. Draw Treatment Mat Background
        SpriteManager.treatmentMat?.let { mat ->
            canvas.drawBitmap(mat, null, matBounds, null)
        }

        // Handle explicit Preview Stage inspection (for verifying stationary foot across peel frames)
        val peelStage = previewPeelStage
        if (peelStage != null) {
            val frameBmp = when (peelStage) {
                0 -> SpriteManager.creamFullFoot
                1 -> SpriteManager.peelFrame1
                2 -> SpriteManager.peelFrame2
                3 -> SpriteManager.peelFrame3
                else -> SpriteManager.cleanHappyFoot ?: SpriteManager.cleanFoot
            }
            if (frameBmp != null) {
                canvas.drawBitmap(frameBmp, null, footBounds, null)
            }
            if (showAnchorOverlay) {
                drawAnchorDebugOverlay(canvas)
            }
            return
        }

        // 1. Draw Clean Base Foot
        SpriteManager.cleanFoot?.let { cleanBmp ->
            canvas.drawBitmap(cleanBmp, null, footBounds, null)
        }

        // 2. Draw Mud/Dirt Layer (PorterDuff.Mode.CLEAR holes reveal clean skin beneath)
        dirtBitmap?.let { bmp ->
            if (dirtFadeAlpha > 0f) {
                layerAlphaPaint.alpha = (dirtFadeAlpha * 255).toInt().coerceIn(0, 255)
                layerAlphaPaint.colorFilter = null
                canvas.drawBitmap(bmp, 0f, 0f, layerAlphaPaint)
            }
        }

        // 3. Draw 5 Damaged Overgrown Nails aligned 1:1 on their toes
        drawDamagedNails(canvas)

        // 4. Draw Splinters (Tweezers target)
        drawSplinters(canvas)

        // 5. Draw Cream / Peel-off Animation Layer (foot stays 100% locked at footBounds!)
        drawCreamAndPeelLayer(canvas)

        // 6. Draw Hint Pulse (if active)
        if (isHintActive) {
            drawHintGuidance(canvas)
        }

        // 7. Draw Debug Anchor Overlay (if enabled in Preview)
        if (showAnchorOverlay) {
            drawAnchorDebugOverlay(canvas)
        }

        // 8. Draw Particles
        drawParticles(canvas)

        // 9. Peel prompt & Finishing Victory Banner
        val cx = footBounds.centerX()
        if (maskState == MaskState.PEELABLE && currentStep == GameStep.APPLY_CREAM) {
            canvas.drawText("✨ Mask is dry! Drag upward to peel! ✨", cx, footBounds.bottom + 36f, promptPaint)
        }

        if (finishAnimProgress > 0f) {
            drawFinishingCelebration(canvas, cx, footBounds.centerY())
        }

        // 10. Draw Active Tool (contact tip follows finger directly!)
        val previewTool = previewToolAtAnchor
        if (previewTool != null) {
            drawActiveTool(canvas, previewTool.first, previewTool.second.x, previewTool.second.y)
        } else if (isTouching) {
            activeTool?.let { tool ->
                drawActiveTool(canvas, tool, touchX, touchY)
            }
        }
    }

    private fun drawDamagedNails(canvas: Canvas) {
        val nailSprites = SpriteManager.damagedNails
        for ((idx, nail) in nails.withIndex()) {
            val remain = 1f - nail.trimProgress
            if (remain <= 0.01f) continue

            val sprite = nailSprites.getOrNull(idx) ?: continue
            layerAlphaPaint.alpha = (remain * 255).toInt().coerceIn(0, 255)
            layerAlphaPaint.colorFilter = null
            canvas.drawBitmap(sprite, null, nail.bounds, layerAlphaPaint)
        }
    }

    private fun drawSplinters(canvas: Canvas) {
        for (splinter in splinters) {
            if (splinter.isRemoved) continue

            canvas.save()
            canvas.translate(splinter.x, splinter.y)
            canvas.rotate(splinter.angleDeg)

            val haloRadius = 16f * (1f - (splinter.pullDistance / 80f)).coerceAtLeast(0.35f)
            canvas.drawCircle(0f, 0f, haloRadius, splinterHaloPaint)

            val currentOffset = -splinter.pullDistance
            val path = Path().apply {
                moveTo(-3.5f, currentOffset)
                lineTo(3.5f, currentOffset)
                lineTo(0f, currentOffset - splinter.length)
                close()
            }
            canvas.drawPath(path, splinterPaint)
            canvas.drawLine(
                1f, currentOffset - splinter.length * 0.5f,
                5f, currentOffset - splinter.length * 0.6f,
                splinterPaint
            )
            canvas.restore()
        }
    }

    private fun drawCreamAndPeelLayer(canvas: Canvas) {
        when (maskState) {
            MaskState.APPLYING -> {
                creamBitmap?.let { bmp ->
                    val saveId = canvas.saveLayer(footBounds, null)
                    canvas.drawBitmap(bmp, 0f, 0f, null)
                    footAlphaMask?.let { mask ->
                        canvas.drawBitmap(mask, 0f, 0f, maskClipPaint)
                    }
                    canvas.restoreToCount(saveId)
                }
            }
            MaskState.DRYING -> {
                // Blend from brushed strokes into the crisp 01_cream_fully_applied frame
                creamBitmap?.let { bmp ->
                    layerAlphaPaint.alpha = ((1f - maskDryProgress) * 255).toInt().coerceIn(0, 255)
                    layerAlphaPaint.colorFilter = null
                    val saveId = canvas.saveLayer(footBounds, null)
                    canvas.drawBitmap(bmp, 0f, 0f, layerAlphaPaint)
                    footAlphaMask?.let { mask ->
                        canvas.drawBitmap(mask, 0f, 0f, maskClipPaint)
                    }
                    canvas.restoreToCount(saveId)
                }
                SpriteManager.creamFullFoot?.let { fullBmp ->
                    layerAlphaPaint.alpha = (maskDryProgress * 255).toInt().coerceIn(0, 255)
                    // Subtle warm darkening as cream dries into peelable film
                    val dryTint = Color.argb((maskDryProgress * 45).toInt(), 191, 54, 12)
                    layerAlphaPaint.colorFilter = PorterDuffColorFilter(dryTint, PorterDuff.Mode.SRC_ATOP)
                    canvas.drawBitmap(fullBmp, null, footBounds, layerAlphaPaint)
                    layerAlphaPaint.colorFilter = null
                }
            }
            MaskState.PEELABLE -> {
                // Smoothly step through cream_full -> peel_01 -> peel_02 -> peel_03
                // All bitmaps share exact footBounds so the foot stays completely motionless!
                val frameBmp = when {
                    maskPeelProgress < 0.18f -> SpriteManager.creamFullFoot
                    maskPeelProgress < 0.45f -> SpriteManager.peelFrame1
                    maskPeelProgress < 0.72f -> SpriteManager.peelFrame2
                    else -> SpriteManager.peelFrame3
                }
                frameBmp?.let { bmp ->
                    layerAlphaPaint.alpha = 255
                    layerAlphaPaint.colorFilter = null
                    canvas.drawBitmap(bmp, null, footBounds, layerAlphaPaint)
                }
            }
            MaskState.PEELED -> {
                // Smoothly reveal cleanHappyFoot + happy reaction badge
                val happyFoot = SpriteManager.cleanHappyFoot ?: SpriteManager.cleanFoot
                happyFoot?.let { bmp ->
                    layerAlphaPaint.alpha = 255
                    layerAlphaPaint.colorFilter = null
                    canvas.drawBitmap(bmp, null, footBounds, layerAlphaPaint)
                }
                SpriteManager.fxHappyReaction?.let { badge ->
                    val badgeSize = footBounds.width() * 0.28f
                    val badgeRect = RectF(
                        footBounds.right - badgeSize * 0.35f,
                        footBounds.top + footBounds.height() * 0.10f,
                        footBounds.right + badgeSize * 0.65f,
                        footBounds.top + footBounds.height() * 0.10f + badgeSize
                    )
                    canvas.drawBitmap(badge, null, badgeRect, null)
                }
            }
        }
    }

    private fun drawFinishingCelebration(canvas: Canvas, cx: Float, cy: Float) {
        val maxR = hypot(width.toFloat(), height.toFloat()) * 0.55f
        for (ring in 0..2) {
            val ringProg = (finishAnimProgress * 1.4f - ring * 0.22f).coerceIn(0f, 1f)
            if (ringProg > 0f && ringProg < 1f) {
                val ringRadius = maxR * ringProg
                val ringAlpha = ((1f - ringProg) * 200).toInt()
                ringPaint.strokeWidth = 14f * (1f - ringProg)
                ringPaint.color = Color.argb(ringAlpha, 255, 215, 0)
                canvas.drawCircle(cx, cy, ringRadius, ringPaint)
            }
        }

        if (finishAnimProgress in 0.15f..0.95f) {
            val bannerAlpha = if (finishAnimProgress < 0.45f) {
                ((finishAnimProgress - 0.15f) / 0.3f * 255).toInt()
            } else {
                ((0.95f - finishAnimProgress) / 0.5f * 255).toInt()
            }.coerceIn(0, 255)

            victoryTextPaint.alpha = bannerAlpha
            victoryTextPaint.setShadowLayer(14f, 0f, 4f, Color.argb(bannerAlpha, 255, 238, 88))
            canvas.drawText("✨ PERFECT RESTORATION! ✨", cx, footBounds.centerY(), victoryTextPaint)
        }
    }

    /**
     * Renders the 5 nail anchor rects + 6 mud spot circles directly over the foot
     * for visual alignment inspection.
     */
    private fun drawAnchorDebugOverlay(canvas: Canvas) {
        // 6 Mud Spots (Blue dashed circles + M1..M6 labels)
        for (spot in mudSpots) {
            canvas.drawCircle(spot.x, spot.y, spot.radius, debugMudFillPaint)
            canvas.drawCircle(spot.x, spot.y, spot.radius, debugMudCirclePaint)
            canvas.drawLine(spot.x - 10f, spot.y, spot.x + 10f, spot.y, debugCrosshairPaint)
            canvas.drawLine(spot.x, spot.y - 10f, spot.x, spot.y + 10f, debugCrosshairPaint)

            val label = "M${spot.id}"
            val tw = debugLabelTextPaint.measureText(label)
            val pill = RectF(spot.x - tw * 0.65f, spot.y - 28f, spot.x + tw * 0.65f, spot.y - 4f)
            canvas.drawRoundRect(pill, 6f, 6f, debugLabelBgPaint)
            canvas.drawText(label, spot.x, spot.y - 9f, debugLabelTextPaint)
        }

        // 5 Nail Anchors (Pink boxes + N1..N5 labels)
        for ((idx, nail) in nails.withIndex()) {
            canvas.drawRect(nail.bounds, debugNailFillPaint)
            canvas.drawRect(nail.bounds, debugNailBoxPaint)
            canvas.drawLine(nail.x - 8f, nail.y, nail.x + 8f, nail.y, debugCrosshairPaint)
            canvas.drawLine(nail.x, nail.y - 8f, nail.x, nail.y + 8f, debugCrosshairPaint)

            val label = "N${idx + 1}"
            val tw = debugLabelTextPaint.measureText(label)
            val ly = nail.bounds.bottom + 24f
            val pill = RectF(nail.x - tw * 0.65f, ly - 20f, nail.x + tw * 0.65f, ly + 4f)
            canvas.drawRoundRect(pill, 6f, 6f, debugLabelBgPaint)
            canvas.drawText(label, nail.x, ly - 1f, debugLabelTextPaint)
        }
    }

    // --- Active Tool Rendering (Exact Contact Point Follows Finger) ---

    private fun drawActiveTool(
        canvas: Canvas,
        tool: ToolType,
        x: Float,
        y: Float
    ) {
        val sprite = when (tool) {
            ToolType.NAIL_GRINDER -> SpriteManager.toolUseGrinder
            ToolType.WET_WIPE -> SpriteManager.toolUseWipes
            ToolType.TWEEZERS -> SpriteManager.toolUseTweezers
            ToolType.CREAM_TUBE -> SpriteManager.toolUseCreamApplying ?: SpriteManager.toolUseCreamOpen
        } ?: return

        val (contactXRatio, contactYRatio) = FootAnchors.getToolContactNormalized(tool)
        val baseScale = when (tool) {
            ToolType.WET_WIPE -> 0.25f
            else -> 0.34f
        }
        val toolSize = (matBounds.width() * baseScale).coerceIn(160f, 340f)

        // Subtle tactile vibration offset for spinning grinder
        val jitterX = if (tool == ToolType.NAIL_GRINDER && isTouching) cos(grinderJitterPhase) * 2.5f else 0f
        val jitterY = if (tool == ToolType.NAIL_GRINDER && isTouching) sin(grinderJitterPhase * 1.3f) * 2.5f else 0f

        val left = x - contactXRatio * toolSize + jitterX
        val top = y - contactYRatio * toolSize + jitterY
        val destRect = RectF(left, top, left + toolSize, top + toolSize)

        val shadowRect = RectF(destRect).apply { offset(8f, 12f) }
        canvas.drawBitmap(sprite, null, shadowRect, toolShadowPaint)
        canvas.drawBitmap(sprite, null, destRect, null)
    }

    // --- Particles & Hint ---

    private fun drawParticles(canvas: Canvas) {
        val goldSparkle = SpriteManager.fxGoldenSparkle
        val creamSparkle = SpriteManager.fxCreamSparkle

        for (p in particles) {
            particlePaint.color = p.color
            particlePaint.alpha = (p.alpha * 255).toInt().coerceIn(0, 255)

            when (p.type) {
                ParticleType.SPARKLE -> {
                    val bmp = if (p.color == Color.parseColor("#FFE082")) creamSparkle else goldSparkle
                    if (bmp != null) {
                        canvas.save()
                        canvas.rotate(p.rotation, p.x, p.y)
                        val r = p.size * 1.4f
                        layerAlphaPaint.alpha = (p.alpha * 255).toInt().coerceIn(0, 255)
                        canvas.drawBitmap(bmp, null, RectF(p.x - r, p.y - r, p.x + r, p.y + r), layerAlphaPaint)
                        canvas.restore()
                    } else {
                        drawStar(canvas, p.x, p.y, p.size, p.rotation, particlePaint)
                    }
                }
                ParticleType.CONFETTI -> {
                    canvas.save()
                    canvas.translate(p.x, p.y)
                    canvas.rotate(p.rotation)
                    canvas.drawRect(-p.size, -p.size * 0.5f, p.size, p.size * 0.5f, particlePaint)
                    canvas.restore()
                }
                else -> {
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
            val px = cx + cos(angle) * r
            val py = cy + sin(angle) * r
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawHintGuidance(canvas: Canvas) {
        val target = getHintTargetPoint() ?: return
        val pulseRadius = 32f + 22f * hintProgress
        ringPaint.strokeWidth = 6f
        ringPaint.color = Color.parseColor("#FFCA28")
        ringPaint.alpha = ((1f - hintProgress) * 255).toInt().coerceIn(0, 255)
        canvas.drawCircle(target.x, target.y, pulseRadius, ringPaint)

        particlePaint.color = Color.parseColor("#FFA000")
        particlePaint.alpha = 255
        drawStar(canvas, target.x, target.y - 42f + 10f * hintProgress, 18f, hintProgress * 180f, particlePaint)
    }

    private fun getHintTargetPoint(): PointF? {
        return when (currentStep) {
            GameStep.CLEAN_DIRT -> {
                mudSpots.firstOrNull { it.cleanProgress < 1f }?.let { PointF(it.x, it.y) }
                    ?: dirtSamplePoints.firstOrNull { !cleanedSampleIndices.contains(dirtSamplePoints.indexOf(it)) }
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

    // --- Touch Interaction ---

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
        if (isHintActive) dismissHint()

        if (maskState == MaskState.PEELABLE && currentStep == GameStep.APPLY_CREAM) {
            soundManager.playPeelSound()
            soundManager.vibrateTick(60)
            return
        }

        when (activeTool) {
            ToolType.TWEEZERS -> {
                activeSplinter = splinters.firstOrNull { splinter ->
                    !splinter.isRemoved && hypot(x - splinter.x, y - splinter.y) < 65f
                }
                activeSplinter?.let {
                    it.isPinching = true
                    soundManager.vibrateTick(100)
                }
            }
            ToolType.WET_WIPE -> eraseDirtAt(x, y, x, y)
            ToolType.CREAM_TUBE -> applyCreamAt(x, y, x, y)
            ToolType.NAIL_GRINDER -> grindNailAt(x, y)
            null -> {}
        }
    }

    private fun handleTouchMove(prevX: Float, prevY: Float, currX: Float, currY: Float) {
        if (maskState == MaskState.PEELABLE && currentStep == GameStep.APPLY_CREAM) {
            val dragDist = lastTouchY - currY
            val anyDrag = hypot(currX - prevX, currY - prevY)
            val progressDelta = if (dragDist > 0f) dragDist / (footBounds.height() * 0.65f) else anyDrag / (footBounds.height() * 1.4f)
            if (progressDelta > 0f) {
                maskPeelProgress = (maskPeelProgress + progressDelta).coerceIn(0f, 1f)
                onStepProgressChanged?.invoke(GameStep.APPLY_CREAM, 0.7f + maskPeelProgress * 0.3f)
                soundManager.playPeelSound()
                soundManager.vibrateTick(45)

                if (Random.nextFloat() < 0.45f) {
                    spawnSparkles(currX, currY, 2, Color.parseColor("#FFE082"))
                }

                if (maskPeelProgress >= 0.88f && maskState == MaskState.PEELABLE) {
                    maskState = MaskState.PEELED
                    absorbCreamIntoSparkles()
                }
            }
            return
        }

        when (activeTool) {
            ToolType.WET_WIPE -> eraseDirtAt(prevX, prevY, currX, currY)
            ToolType.TWEEZERS -> {
                activeSplinter?.let { splinter ->
                    val dist = hypot(currX - splinter.x, currY - splinter.y)
                    splinter.pullDistance = dist
                    soundManager.vibrateTick(60)
                    if (dist > 45f && !splinter.isRemoved) {
                        splinter.isRemoved = true
                        activeSplinter = null
                        soundManager.playPluckSound()
                        soundManager.vibratePop()
                        spawnSparkles(currX, currY, 14, Color.parseColor("#81C784"))
                        spawnSparkles(currX, currY, 8, Color.parseColor("#FFD54F"))
                        checkSplintersCompletion()
                    }
                }
            }
            ToolType.NAIL_GRINDER -> grindNailAt(currX, currY)
            ToolType.CREAM_TUBE -> applyCreamAt(prevX, prevY, currX, currY)
            null -> {}
        }
    }

    private fun handleTouchUp() {
        activeSplinter?.let {
            it.isPinching = false
            it.pullDistance = 0f
            activeSplinter = null
        }
    }

    // --- Step 1: Wipe off mud ---

    private fun eraseDirtAt(x0: Float, y0: Float, x1: Float, y1: Float) {
        dirtCanvas?.let { cvs ->
            cvs.drawLine(x0, y0, x1, y1, erasePaint)
            cvs.drawCircle(x1, y1, erasePaint.strokeWidth * 0.5f, erasePaint)

            if (Random.nextFloat() < 0.45f) {
                particles.add(
                    Particle(
                        x = x1 + (Random.nextFloat() - 0.5f) * 36f,
                        y = y1 + (Random.nextFloat() - 0.5f) * 36f,
                        vx = (Random.nextFloat() - 0.5f) * 2f,
                        vy = -Random.nextFloat() * 1.5f,
                        size = 5f + Random.nextFloat() * 8f,
                        color = Color.argb(165, 225, 245, 254),
                        type = ParticleType.MOISTURE,
                        decay = 0.05f
                    )
                )
            }

            soundManager.playWipeSound()
            soundManager.vibrateTick(45)
            calculateDirtCleaned(x1, y1)
        }
    }

    private fun calculateDirtCleaned(x: Float, y: Float) {
        val eraseRadius = erasePaint.strokeWidth * 0.58f
        for (spot in mudSpots) {
            if (spot.cleanProgress < 1f && hypot(x - spot.x, y - spot.y) <= spot.radius + eraseRadius * 0.4f) {
                spot.cleanProgress = 1f
            }
        }
        for ((idx, pt) in dirtSamplePoints.withIndex()) {
            if (!cleanedSampleIndices.contains(idx) && hypot(x - pt.x, y - pt.y) <= eraseRadius) {
                cleanedSampleIndices.add(idx)
            }
        }

        val total = dirtSamplePoints.size.toFloat().coerceAtLeast(1f)
        val cleanedPercent = cleanedSampleIndices.size / total
        onStepProgressChanged?.invoke(GameStep.CLEAN_DIRT, cleanedPercent)

        if (cleanedPercent >= 0.85f && dirtFadeAlpha == 1f && currentStep == GameStep.CLEAN_DIRT) {
            triggerDirtFadeOut()
        }
    }

    private fun triggerDirtFadeOut() {
        soundManager.playChime()
        soundManager.vibrateSuccess()
        spawnSparkles(footBounds.centerX(), footBounds.centerY(), 25, Color.parseColor("#4FC3F7"))

        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 550
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { va ->
                dirtFadeAlpha = va.animatedValue as Float
                invalidate()
            }
        }.start()

        advanceStep(GameStep.PLUCK_SPLINTERS)
    }

    // --- Step 2: Splinters ---

    private fun checkSplintersCompletion() {
        val remaining = splinters.count { !it.isRemoved }
        val progress = (splinters.size - remaining) / splinters.size.toFloat()
        onStepProgressChanged?.invoke(GameStep.PLUCK_SPLINTERS, progress)

        if (remaining == 0 && currentStep == GameStep.PLUCK_SPLINTERS) {
            soundManager.playChime()
            soundManager.vibrateSuccess()
            spawnSparkles(footBounds.centerX(), footBounds.centerY(), 30, Color.parseColor("#81C784"))
            advanceStep(GameStep.TRIM_NAILS)
        }
    }

    // --- Step 3: Nail Grinding ---

    private fun grindNailAt(x: Float, y: Float) {
        for (nail in nails) {
            if (nail.trimProgress >= 1f) continue

            val hitRadius = maxOf(nail.radiusX, nail.radiusY) * 1.45f + 24f
            if (hypot(x - nail.x, y - nail.y) <= hitRadius) {
                nail.trimProgress = (nail.trimProgress + 0.042f).coerceAtMost(1f)
                soundManager.playGrindSound()
                soundManager.vibrateGrind()

                for (k in 0..2) {
                    particles.add(
                        Particle(
                            x = x + (Random.nextFloat() - 0.5f) * 12f,
                            y = y + (Random.nextFloat() - 0.5f) * 12f,
                            vx = (Random.nextFloat() - 0.5f) * 7f,
                            vy = (Random.nextFloat() - 0.8f) * 6f,
                            size = 3f + Random.nextFloat() * 4f,
                            color = if (Random.nextBoolean()) Color.parseColor("#FFE082") else Color.parseColor("#ECEFF1"),
                            type = ParticleType.DUST,
                            decay = 0.06f
                        )
                    )
                }

                if (nail.trimProgress >= 1f) {
                    soundManager.playChime()
                    spawnSparkles(nail.x, nail.y, 12, Color.parseColor("#FFD54F"))
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
            spawnSparkles(footBounds.centerX(), footBounds.bottom - footBounds.height() * 0.1f, 32, Color.parseColor("#FFD54F"))
            advanceStep(GameStep.APPLY_CREAM)
        }
    }

    // --- Step 4: Cream & Stationary Peel-Off ---

    private fun applyCreamAt(x0: Float, y0: Float, x1: Float, y1: Float) {
        if (maskState != MaskState.APPLYING) return

        creamCanvas?.let { cvs ->
            cvs.drawLine(x0, y0, x1, y1, creamDepositPaint)
            cvs.drawCircle(x1, y1, creamDepositPaint.strokeWidth * 0.5f, creamDepositPaint)
            cvs.drawLine(x0, y0, x1, y1, creamHighlightPaint)
            cvs.drawCircle(x1, y1, creamHighlightPaint.strokeWidth * 0.5f, creamHighlightPaint)

            soundManager.playCreamSound()
            soundManager.vibrateTick(45)

            val applyRadius = creamDepositPaint.strokeWidth * 0.55f
            for ((idx, pt) in creamSamplePoints.withIndex()) {
                if (!coveredCreamIndices.contains(idx) && hypot(x1 - pt.x, y1 - pt.y) <= applyRadius) {
                    coveredCreamIndices.add(idx)
                }
            }

            val total = creamSamplePoints.size.toFloat().coerceAtLeast(1f)
            val coverage = coveredCreamIndices.size / total
            onStepProgressChanged?.invoke(GameStep.APPLY_CREAM, coverage * 0.65f)

            if (coverage >= 0.80f && maskState == MaskState.APPLYING && currentStep == GameStep.APPLY_CREAM) {
                startMaskDrying()
            }
        }
    }

    private fun startMaskDrying() {
        maskState = MaskState.DRYING
        soundManager.playChime()
        soundManager.vibrateTick(120)

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1600
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { va ->
                maskDryProgress = va.animatedValue as Float
                if (Random.nextFloat() < 0.4f) {
                    val rx = footBounds.left + Random.nextFloat() * footBounds.width()
                    val ry = footBounds.top + Random.nextFloat() * footBounds.height()
                    spawnSparkles(rx, ry, 1, Color.parseColor("#FFE082"))
                }
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    maskState = MaskState.PEELABLE
                    selectTool(null)
                    onToolDeselectRequested?.invoke()
                    soundManager.playChime()
                    soundManager.vibrateSuccess()
                    onStepProgressChanged?.invoke(GameStep.APPLY_CREAM, 0.7f)
                    invalidate()
                }
            })
        }.start()
    }

    private fun absorbCreamIntoSparkles() {
        isFinishing = true
        soundManager.playVictory()
        soundManager.vibrateSuccess()

        spawnConfetti()
        for (nail in nails) {
            spawnSparkles(nail.x, nail.y, 10, Color.parseColor("#FFD700"))
        }

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { va ->
                finishAnimProgress = va.animatedValue as Float
                if (Random.nextFloat() < 0.7f) {
                    val rx = footBounds.left + Random.nextFloat() * footBounds.width()
                    val ry = footBounds.top + Random.nextFloat() * footBounds.height()
                    spawnSparkles(rx, ry, 3, Color.parseColor("#FFF9C4"))
                }
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    advanceStep(GameStep.COMPLETED)
                    onLevelCompletedListener?.invoke()
                }
            })
        }.start()
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
                    size = 10f + Random.nextFloat() * 12f,
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
        for (i in 0 until 75) {
            particles.add(
                Particle(
                    x = footBounds.left + Random.nextFloat() * footBounds.width(),
                    y = footBounds.top - 40f + Random.nextFloat() * 140f,
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
        activeTool = next.recommendedTool
    }

    fun selectTool(tool: ToolType?) {
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

        maskState = MaskState.APPLYING
        maskDryProgress = 0f
        maskPeelProgress = 0f
        finishAnimProgress = 0f
        isFinishing = false

        for (spot in mudSpots) spot.cleanProgress = 0f
        for (nail in nails) nail.trimProgress = 0f
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
