package com.example.asmrgame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FootAlignmentTest {

    private data class RectBounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    private fun computeFootBounds(viewW: Float, viewH: Float): RectBounds {
        val padX = viewW * 0.03f
        val padY = viewH * 0.015f
        val availW = (viewW - padX * 2f).coerceAtLeast(1f)
        val availH = (viewH - padY * 2f).coerceAtLeast(1f)

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

        val matLeft = (viewW - matW) * 0.5f
        val matTop = (viewH - matH) * 0.5f

        val footAspect = 500f / 899f
        var footH = matH * 0.875f
        var footW = footH * footAspect
        if (footW > matW * 0.72f) {
            footW = matW * 0.72f
            footH = footW / footAspect
        }
        val footLeft = (matLeft + matW * 0.5f) - footW * 0.5f
        val footTop = matTop + (matH - footH) * 0.44f
        return RectBounds(footLeft, footTop, footLeft + footW, footTop + footH)
    }

    @Test
    fun nailsAndMudAlignAcrossPhoneAndTablet() {
        val screenSizes = listOf(
            1024f to 1365f, // Phone gameplay view (390x844 dp @ density)
            1470f to 1837f  // Tablet gameplay view (768x1024 dp @ density)
        )

        for ((w, h) in screenSizes) {
            val foot = computeFootBounds(w, h)
            assertEquals(5, FootAnchors.NAILS.size)
            assertEquals(6, FootAnchors.MUD_SPOTS.size)

            var prevNailX = -1f
            for (nail in FootAnchors.NAILS) {
                val nx = foot.left + nail.normCenterX * foot.width
                val ny = foot.top + nail.normCenterY * foot.height
                assertTrue("${nail.name} X must be inside foot bounds", nx in foot.left..foot.right)
                assertTrue("${nail.name} Y must be in toe region (>80%)", ny in (foot.top + foot.height * 0.80f)..foot.bottom)
                assertTrue("Toes must progress left-to-right Pinky->Big", nx > prevNailX)
                prevNailX = nx
            }

            for (mud in FootAnchors.MUD_SPOTS) {
                val mx = foot.left + mud.normX * foot.width
                val my = foot.top + mud.normY * foot.height
                assertTrue("${mud.name} X must be inside foot bounds", mx in foot.left..foot.right)
                assertTrue("${mud.name} Y must be above toe nails", my in foot.top..(foot.top + foot.height * 0.78f))
            }
        }
    }

    @Test
    fun toolContactPointsMatchFingerCoordinatesAcrossScreenSizes() {
        val matWidths = listOf(950f, 1400f)
        val fingerX = 612.5f
        val fingerY = 1284.2f

        for (matW in matWidths) {
            for (tool in ToolType.entries) {
                val (cxRatio, cyRatio) = FootAnchors.getToolContactNormalized(tool)
                val baseScale = if (tool == ToolType.WET_WIPE) 0.25f else 0.34f
                val toolSize = (matW * baseScale).coerceIn(160f, 340f)
                val left = fingerX - cxRatio * toolSize
                val top = fingerY - cyRatio * toolSize

                val renderedContactX = left + cxRatio * toolSize
                val renderedContactY = top + cyRatio * toolSize
                assertEquals(fingerX, renderedContactX, 0.001f)
                assertEquals(fingerY, renderedContactY, 0.001f)
            }
        }
    }
}
