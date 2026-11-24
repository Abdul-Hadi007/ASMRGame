package com.example.asmrgame

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader

/**
 * Loads the transparent PNG game assets from res/drawable-nodpi and registers
 * all foot states, damaged nails, mud layers, and tools into a shared coordinate space.
 */
object SpriteManager {

    var isLoaded = false
        private set

    // Playfield & Foot sprites (all cropped to exact visible foot silhouette bounds)
    var treatmentMat: Bitmap? = null
        private set
    var cleanFoot: Bitmap? = null
        private set
    var dirtyFoot: Bitmap? = null
        private set
    var dirtOnlyFoot: Bitmap? = null
        private set
    var creamFullFoot: Bitmap? = null
        private set
    var peelFrame1: Bitmap? = null
        private set
    var peelFrame2: Bitmap? = null
        private set
    var peelFrame3: Bitmap? = null
        private set
    var cleanHappyFoot: Bitmap? = null
        private set

    // 5 individual damaged nails extracted directly from foot_dirty at FootAnchors.NAILS
    val damagedNails = mutableListOf<Bitmap?>()

    // Tray tool icons (306x360)
    var toolTrayGrinder: Bitmap? = null
        private set
    var toolTrayWipes: Bitmap? = null
        private set
    var toolTrayTweezers: Bitmap? = null
        private set
    var toolTrayCream: Bitmap? = null
        private set

    // In-use draggable 3D tools (1024x1024)
    var toolUseGrinder: Bitmap? = null
        private set
    var toolUseWipes: Bitmap? = null
        private set
    var toolUseTweezers: Bitmap? = null
        private set
    var toolUseCreamOpen: Bitmap? = null
        private set
    var toolUseCreamApplying: Bitmap? = null
        private set

    // Effects & Reactions
    var fxCreamSparkle: Bitmap? = null
        private set
    var fxGoldenSparkle: Bitmap? = null
        private set
    var fxSoapBubble: Bitmap? = null
        private set
    var fxHappyReaction: Bitmap? = null
        private set
    var fxCreamFilmPeelOff: Bitmap? = null
        private set

    // Exact visible foot bounding boxes verified against the PNG assets
    private val FOOT_VISIBLE_516X915 = Rect(8, 8, 508, 907)
    private val PEEL_FOOT_VISIBLE_1024 = Rect(274, 87, 749, 937)

    fun load(context: Context) {
        if (isLoaded) return

        try {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            }

            fun decode(resId: Int): Bitmap? = try {
                BitmapFactory.decodeResource(context.resources, resId, opts)
            } catch (_: Exception) {
                null
            }

            treatmentMat = decode(R.drawable.play_treatment_mat)

            // Crop clean & dirty feet to exact visible foot bounds [8,8,508,907] (500x899)
            cleanFoot = decode(R.drawable.foot_clean)?.let { cropSafe(it, FOOT_VISIBLE_516X915) }
            dirtyFoot = decode(R.drawable.foot_dirty)?.let { cropSafe(it, FOOT_VISIBLE_516X915) }

            // Crop cream & all 3 peel frames + happy foot to exact [274,87,749,937] (475x850)
            // so the foot stays completely motionless across every cream & peel transition
            creamFullFoot = decode(R.drawable.foot_cream_full)?.let { cropSafe(it, PEEL_FOOT_VISIBLE_1024) }
            peelFrame1 = decode(R.drawable.foot_peel_01)?.let { cropSafe(it, PEEL_FOOT_VISIBLE_1024) }
            peelFrame2 = decode(R.drawable.foot_peel_02)?.let { cropSafe(it, PEEL_FOOT_VISIBLE_1024) }
            peelFrame3 = decode(R.drawable.foot_peel_03)?.let { cropSafe(it, PEEL_FOOT_VISIBLE_1024) }
            cleanHappyFoot = decode(R.drawable.foot_clean_happy)?.let { cropSafe(it, PEEL_FOOT_VISIBLE_1024) }

            // Extract mud-only overlay (with nail regions feathered out) and 5 damaged nail overlays
            dirtyFoot?.let { dirty ->
                extractDirtAndNailLayers(dirty)
            }

            // Tray tools
            toolTrayGrinder = decode(R.drawable.tool_tray_nail_grinder)
            toolTrayWipes = decode(R.drawable.tool_tray_wet_wipe)
            toolTrayTweezers = decode(R.drawable.tool_tray_tweezers)
            toolTrayCream = decode(R.drawable.tool_tray_cream_tube)

            // In-use draggable tools
            toolUseGrinder = decode(R.drawable.tool_use_nail_grinder)
            toolUseWipes = decode(R.drawable.tool_use_wet_wipe)
            toolUseTweezers = decode(R.drawable.tool_use_tweezers)
            toolUseCreamOpen = decode(R.drawable.tool_use_cream_open)
            toolUseCreamApplying = decode(R.drawable.tool_use_cream_applying)

            // Effects
            fxCreamSparkle = decode(R.drawable.fx_cream_sparkle)
            fxGoldenSparkle = decode(R.drawable.fx_golden_sparkle)
            fxSoapBubble = decode(R.drawable.fx_soap_bubble)
            fxHappyReaction = decode(R.drawable.fx_happy_reaction)
            fxCreamFilmPeelOff = decode(R.drawable.fx_cream_film_peel_off)

            isLoaded = true
        } catch (_: Exception) {
        }
    }

    private fun cropSafe(src: Bitmap, rect: Rect): Bitmap {
        val left = rect.left.coerceIn(0, src.width - 1)
        val top = rect.top.coerceIn(0, src.height - 1)
        val w = rect.width().coerceIn(1, src.width - left)
        val h = rect.height().coerceIn(1, src.height - top)
        return Bitmap.createBitmap(src, left, top, w, h)
    }

    /**
     * Separates the 5 damaged nail regions from the mud/dirt layer on foot_dirty so:
     * 1. Step 1 (Wet Wipe) erases mud spots to reveal clean skin without prematurely cleaning damaged nails.
     * 2. Step 3 (Nail Grinder) grinds each damaged nail independently on its exact toe anchor.
     */
    private fun extractDirtAndNailLayers(dirty: Bitmap) {
        val w = dirty.width
        val h = dirty.height

        // 1. Create dirtOnlyFoot by punching soft feathered ovals out of the 5 nail regions
        val dirtBmp = dirty.copy(Bitmap.Config.ARGB_8888, true)
        val dirtCanvas = Canvas(dirtBmp)
        val punchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }

        damagedNails.clear()

        for (anchor in FootAnchors.NAILS) {
            val l = anchor.normLeft * w
            val t = anchor.normTop * h
            val r = anchor.normRight * w
            val b = anchor.normBottom * h
            val cx = (l + r) * 0.5f
            val cy = (t + b) * 0.5f
            val rx = ((r - l) * 0.5f).coerceAtLeast(1f)
            val ry = ((b - t) * 0.5f).coerceAtLeast(1f)

            // Elliptical feathered gradient for DST_OUT
            val grad = RadialGradient(
                0f, 0f, 1f,
                intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT),
                floatArrayOf(0f, 0.72f, 1f),
                Shader.TileMode.CLAMP
            )
            val m = Matrix()
            m.setScale(rx, ry)
            m.postTranslate(cx, cy)
            grad.setLocalMatrix(m)
            punchPaint.shader = grad
            dirtCanvas.drawOval(RectF(l, t, r, b), punchPaint)

            // 2. Extract the individual damaged nail sprite cropped strictly to [l, t, r, b]
            val ix = l.toInt().coerceIn(0, w - 1)
            val iy = t.toInt().coerceIn(0, h - 1)
            val iw = (r - l).toInt().coerceIn(1, w - ix)
            val ih = (b - t).toInt().coerceIn(1, h - iy)

            val nailCrop = Bitmap.createBitmap(dirty, ix, iy, iw, ih)
            val maskedNail = Bitmap.createBitmap(iw, ih, Bitmap.Config.ARGB_8888)
            val nailCanvas = Canvas(maskedNail)

            // Draw soft feathered oval mask first, then SRC_IN the nail pixels
            val localMaskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val localGrad = RadialGradient(
                0f, 0f, 1f,
                intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT),
                floatArrayOf(0f, 0.74f, 1f),
                Shader.TileMode.CLAMP
            )
            val localM = Matrix()
            localM.setScale(iw * 0.5f, ih * 0.5f)
            localM.postTranslate(iw * 0.5f, ih * 0.5f)
            localGrad.setLocalMatrix(localM)
            localMaskPaint.shader = localGrad
            nailCanvas.drawOval(RectF(0f, 0f, iw.toFloat(), ih.toFloat()), localMaskPaint)

            val srcInPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            }
            nailCanvas.drawBitmap(nailCrop, 0f, 0f, srcInPaint)
            damagedNails.add(maskedNail)
        }

        dirtOnlyFoot = dirtBmp
    }
}
