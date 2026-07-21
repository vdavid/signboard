package com.veszelovszki.signboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import kotlin.math.roundToInt

/**
 * Draws text as large as it will fit, centered, flowing around display cutouts.
 *
 * An auto-sizing `TextView` can only avoid a cutout with padding, and padding applies to an entire
 * edge. Clearing a punch-hole camera therefore costs a full-height strip of screen even though the
 * hole covers a small band of it: on a 2244x1008 landscape screen, a 149x87 hole cost 149x1008.
 *
 * Here every line is fitted against only the cutouts level with that line, so a line nowhere near
 * the camera keeps the full width, and a line beside it gives up only the width the hole occupies.
 */
class SignboardView(context: Context) : View(context) {
  private val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
  private var lines: List<String> = listOf("")
  private var cutouts: List<Rect> = emptyList()
  private var fittedSizePx = 0f
  private var needsFit = true

  var text: String = ""
    set(value) {
      field = value
      // A trailing newline should still produce an empty last line, so split rather than lines().
      lines = value.split('\n')
      needsFit = true
      invalidate()
    }

  var textColor: Int
    get() = paint.color
    set(value) {
      paint.color = value
      invalidate()
    }

  /** Cutout rectangles, in this view's coordinates. */
  fun setCutouts(rects: List<Rect>) {
    if (rects == cutouts) return
    cutouts = rects
    needsFit = true
    invalidate()
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    needsFit = true
  }

  override fun onDraw(canvas: Canvas) {
    if (needsFit) {
      fittedSizePx = largestFittingSize()
      needsFit = false
    }
    paint.textSize = fittedSizePx

    val metrics = paint.fontMetrics
    val lineHeight = metrics.descent - metrics.ascent
    var lineTop = blockTop(lineHeight * lines.size)

    for (line in lines) {
      val (left, right) = horizontalSpan(lineTop, lineTop + lineHeight)
      val x = left + (right - left - paint.measureText(line)) / 2f
      canvas.drawText(line, x, lineTop - metrics.ascent, paint)
      lineTop += lineHeight
    }
  }

  /**
   * Binary search for the largest size that still clears every cutout.
   *
   * Monotonic in the size, which is what makes the search valid: shrinking the text both narrows
   * each line and shortens the block, and a vertically centered block only moves away from a top
   * or bottom cutout as it shrinks.
   */
  private fun largestFittingSize(): Float {
    var low = sp(MIN_TEXT_SP)
    var high = sp(MAX_TEXT_SP)
    if (!fits(low)) return low

    // Half a pixel is far finer than is visible at these sizes.
    while (high - low > 0.5f) {
      val mid = (low + high) / 2f
      if (fits(mid)) low = mid else high = mid
    }
    return low
  }

  private fun fits(sizePx: Float): Boolean {
    paint.textSize = sizePx
    val metrics = paint.fontMetrics
    val lineHeight = metrics.descent - metrics.ascent
    val blockHeight = lineHeight * lines.size
    if (blockHeight > contentBottom() - contentTop()) return false

    var lineTop = blockTop(blockHeight)
    for (line in lines) {
      val lineBottom = lineTop + lineHeight
      val (left, right) = horizontalSpan(lineTop, lineBottom)
      val width = paint.measureText(line)
      if (width > right - left) return false

      // Narrowing only helps against cutouts attached to a side. A hole in the middle of a top
      // edge has to be cleared by shrinking until the block no longer reaches it, so check the
      // line where it actually lands rather than assuming the span did the job.
      val x = left + (right - left - width) / 2f
      val lineRect = Rect(x.roundToInt(), lineTop.roundToInt(), (x + width).roundToInt(), lineBottom.roundToInt())
      if (cutouts.any { Rect.intersects(it, lineRect) }) return false

      lineTop = lineBottom
    }
    return true
  }

  /** The usable left and right edges for a line occupying [top]..[bottom]. */
  private fun horizontalSpan(top: Float, bottom: Float): Pair<Float, Float> {
    var left = contentLeft()
    var right = contentRight()
    for (rect in cutouts) {
      // Only cutouts level with this line can narrow it.
      if (rect.bottom <= top || rect.top >= bottom) continue
      if (rect.left <= 0) left = maxOf(left, rect.right.toFloat())
      if (rect.right >= width) right = minOf(right, rect.left.toFloat())
    }
    return left to right
  }

  private fun blockTop(blockHeight: Float): Float = contentTop() + (contentBottom() - contentTop() - blockHeight) / 2f

  private fun contentLeft(): Float = paddingLeft.toFloat()

  private fun contentRight(): Float = (width - paddingRight).toFloat()

  private fun contentTop(): Float = paddingTop.toFloat()

  private fun contentBottom(): Float = (height - paddingBottom).toFloat()

  private fun sp(value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

  private companion object {
    const val MIN_TEXT_SP = 24f
    const val MAX_TEXT_SP = 400f
  }
}
