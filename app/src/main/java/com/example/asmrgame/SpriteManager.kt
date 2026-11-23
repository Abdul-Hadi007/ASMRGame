package com.example.asmrgame

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.util.ArrayDeque
import kotlin.math.abs

/**
 * Loads the game asset sprite sheet and extracts individual transparent sprites.
 */
object SpriteManager {

    var isLoaded = false
        private set

    var cleanFoot: Bitmap? = null
        private set
    var hairOverlay: Bitmap? = null
        private set
    var dirtOverlay: Bitmap? = null
        private set
    var dirtStains: Bitmap? = null
        private set

    var overgrownNails = mutableListOf<Bitmap>()
        private set

    var toolGrinder: Bitmap? = null
        private set
    var toolWipes: Bitmap? = null
        private set
    var toolTweezers: Bitmap? = null
        private set
    var toolCream: Bitmap? = null
        private set

    var bannerSign: Bitmap? = null
        private set
    var btnBack: Bitmap? = null
        private set
    var btnHint: Bitmap? = null
        private set
    var btnAddTime: Bitmap? = null
        private set

    fun load(context: Context) {
        if (isLoaded) return

        try {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = true
            }
            val rawSheet = BitmapFactory.decodeResource(context.resources, R.drawable.game_sheet, opts)
                ?: return

            // Make background checkerboard transparent using flood fill from outer edges
            val sheet = makeCheckerboardTransparent(rawSheet)

            // 1. Top Row: Foot, Hair, Dirt
            cleanFoot = cropRelative(sheet, 0.035f, 0.015f, 0.335f, 0.405f)
            hairOverlay = cropRelative(sheet, 0.365f, 0.015f, 0.665f, 0.405f)
            dirtOverlay = cropRelative(sheet, 0.695f, 0.015f, 0.985f, 0.405f)

            // 2. Second Row: Overgrown Nails (6 toes)
            overgrownNails.clear()
            overgrownNails.add(cropRelative(sheet, 0.055f, 0.425f, 0.235f, 0.555f)) // Big toe left
            overgrownNails.add(cropRelative(sheet, 0.235f, 0.445f, 0.355f, 0.535f)) // Toe 2
            overgrownNails.add(cropRelative(sheet, 0.355f, 0.455f, 0.455f, 0.540f)) // Toe 3
            overgrownNails.add(cropRelative(sheet, 0.485f, 0.425f, 0.665f, 0.560f)) // Big toe split
            overgrownNails.add(cropRelative(sheet, 0.685f, 0.435f, 0.810f, 0.540f)) // Toe 5 cracked
            overgrownNails.add(cropRelative(sheet, 0.825f, 0.450f, 0.935f, 0.540f)) // Toe 6 small

            // 3. Third Row: Tools
            toolGrinder = cropRelative(sheet, 0.045f, 0.605f, 0.295f, 0.795f)
            toolWipes = cropRelative(sheet, 0.300f, 0.635f, 0.505f, 0.790f)
            toolTweezers = cropRelative(sheet, 0.525f, 0.620f, 0.725f, 0.795f)
            toolCream = cropRelative(sheet, 0.735f, 0.610f, 0.950f, 0.795f)

            // 4. Fourth Row: UI Elements
            bannerSign = try {
                val bannerOpts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inMutable = true
                }
                val rawBanner = BitmapFactory.decodeResource(context.resources, R.drawable.banner_sign, bannerOpts)
                if (rawBanner != null) cleanStainsBackground(rawBanner) else null
            } catch (_: Exception) {
                null
            } ?: cropRelative(sheet, 0.030f, 0.800f, 0.440f, 0.985f)

            btnBack = cropRelative(sheet, 0.455f, 0.840f, 0.605f, 0.970f)
            btnHint = cropRelative(sheet, 0.625f, 0.840f, 0.780f, 0.985f)
            btnAddTime = cropRelative(sheet, 0.795f, 0.840f, 0.950f, 0.985f)

            // 5. Load Dirt Stains Asset
            try {
                val stainsOpts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inMutable = true
                }
                val rawStains = BitmapFactory.decodeResource(context.resources, R.drawable.dirt_stains, stainsOpts)
                if (rawStains != null) {
                    dirtStains = cleanStainsBackground(rawStains)
                }
            } catch (_: Exception) {
            }

            isLoaded = true
        } catch (_: Exception) {
        }
    }

    private fun cropRelative(
        sheet: Bitmap,
        leftF: Float,
        topF: Float,
        rightF: Float,
        bottomF: Float
    ): Bitmap {
        val x = (leftF * sheet.width).toInt().coerceIn(0, sheet.width - 1)
        val y = (topF * sheet.height).toInt().coerceIn(0, sheet.height - 1)
        val w = ((rightF - leftF) * sheet.width).toInt().coerceIn(1, sheet.width - x)
        val h = ((bottomF - topF) * sheet.height).toInt().coerceIn(1, sheet.height - y)

        val cropped = Bitmap.createBitmap(sheet, x, y, w, h)
        return trimTransparentBorders(cropped)
    }

    /**
     * Cleans background from stains image if needed (white or checkerboard).
     */
    private fun cleanStainsBackground(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val corner = src.getPixel(0, 0)
        // If already transparent at corner, return directly
        if (Color.alpha(corner) < 20) {
            return src
        }

        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val visited = BooleanArray(w * h)
        val queue = ArrayDeque<Int>()

        fun isBgPixel(c: Int): Boolean {
            val a = Color.alpha(c)
            if (a < 20) return true
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)
            val isWhite = r > 235 && g > 235 && b > 235
            val isGrayCheck = r > 150 && g > 150 && b > 150 && abs(r - g) <= 22 && abs(g - b) <= 22
            return isWhite || isGrayCheck
        }

        fun enqueue(x: Int, y: Int) {
            if (x !in 0 until w || y !in 0 until h) return
            val idx = y * w + x
            if (!visited[idx]) {
                visited[idx] = true
                if (isBgPixel(pixels[idx])) {
                    pixels[idx] = 0
                    queue.add(idx)
                }
            }
        }

        for (x in 0 until w) {
            enqueue(x, 0)
            enqueue(x, h - 1)
        }
        for (y in 0 until h) {
            enqueue(0, y)
            enqueue(w - 1, y)
        }

        val dx = intArrayOf(1, -1, 0, 0)
        val dy = intArrayOf(0, 0, 1, -1)

        while (queue.isNotEmpty()) {
            val curr = queue.poll() ?: break
            val cx = curr % w
            val cy = curr / w

            for (i in 0..3) {
                val nx = cx + dx[i]
                val ny = cy + dy[i]
                if (nx in 0 until w && ny in 0 until h) {
                    val nIdx = ny * w + nx
                    if (!visited[nIdx]) {
                        visited[nIdx] = true
                        if (isBgPixel(pixels[nIdx])) {
                            pixels[nIdx] = 0
                            queue.add(nIdx)
                        }
                    }
                }
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Removes the gray-and-white checkerboard transparency pattern.
     * Uses an outside-in boundary flood fill so inner whites and grays remain intact.
     */
    private fun makeCheckerboardTransparent(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val visited = BooleanArray(w * h)
        val queue = ArrayDeque<Int>()

        fun isCheckerboardPixel(color: Int): Boolean {
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            // Checkerboard is grayscale with brightness > 155 (squares of ~#CECECE and #FFFFFF)
            val isNeutral = abs(r - g) <= 22 && abs(g - b) <= 22 && abs(r - b) <= 22
            return isNeutral && r >= 155 && g >= 155 && b >= 155
        }

        fun enqueue(x: Int, y: Int) {
            if (x !in 0 until w || y !in 0 until h) return
            val idx = y * w + x
            if (!visited[idx]) {
                visited[idx] = true
                if (isCheckerboardPixel(pixels[idx])) {
                    pixels[idx] = 0 // Transparent
                    queue.add(idx)
                }
            }
        }

        // Enqueue all perimeter pixels
        for (x in 0 until w) {
            enqueue(x, 0)
            enqueue(x, h - 1)
        }
        for (y in 0 until h) {
            enqueue(0, y)
            enqueue(w - 1, y)
        }

        // 4-way flood fill
        val dx = intArrayOf(1, -1, 0, 0)
        val dy = intArrayOf(0, 0, 1, -1)

        while (queue.isNotEmpty()) {
            val curr = queue.poll() ?: break
            val cx = curr % w
            val cy = curr / w

            for (i in 0..3) {
                val nx = cx + dx[i]
                val ny = cy + dy[i]
                if (nx in 0 until w && ny in 0 until h) {
                    val nIdx = ny * w + nx
                    if (!visited[nIdx]) {
                        visited[nIdx] = true
                        if (isCheckerboardPixel(pixels[nIdx])) {
                            pixels[nIdx] = 0 // Transparent
                            queue.add(nIdx)
                        }
                    }
                }
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Trims any empty transparent borders around a sliced sprite.
     */
    private fun trimTransparentBorders(bmp: Bitmap): Bitmap {
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        var minX = w
        var minY = h
        var maxX = 0
        var maxY = 0

        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                val alpha = Color.alpha(pixels[rowOffset + x])
                if (alpha > 20) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (minX > maxX || minY > maxY) return bmp

        val cropW = maxX - minX + 1
        val cropH = maxY - minY + 1
        return Bitmap.createBitmap(bmp, minX, minY, cropW, cropH)
    }
}
