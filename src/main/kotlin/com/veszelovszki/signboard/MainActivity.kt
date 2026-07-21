package com.veszelovszki.signboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import org.json.JSONArray // Ships in the Android framework, not a dependency.

class MainActivity : Activity() {
  private lateinit var textView: TextView
  private lateinit var root: FrameLayout
  private val prefs by lazy { getSharedPreferences("signboard", Context.MODE_PRIVATE) }

  /** Most recent insets, kept so cutout padding can be recomputed after the text is laid out. */
  private var lastInsets: WindowInsets? = null

  /**
   * Whether cutout padding has been decided for the current text and window size. Adding padding
   * shrinks the text, which can move it clear of the cutout, which would argue for removing the
   * padding again: evaluating exactly once per layout generation is what stops that oscillating.
   */
  private var cutoutPaddingResolved = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Let the window extend under the camera cutout instead of letting the system letterbox
    // the app away from it. Without this the framework reserves the whole cutout strip across
    // the full width (or height, in landscape), so the sign loses that band on every screen
    // even though the hole itself is small. Drawing under it and padding by the measured
    // safe inset below means only the affected edge gives up space, and only as much as the
    // hole actually needs.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      window.attributes = window.attributes.apply {
        layoutInDisplayCutoutMode =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // ALWAYS covers landscape too, where the cutout sits on a long edge.
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
          } else {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
          }
      }
    }

    // Root container: keep screen on
    root = FrameLayout(this).apply {
      keepScreenOn = true
    }

    // Text display with auto-sizing to fill screen
    val padding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, resources.displayMetrics).toInt()
    textView = TextView(this).apply {
      val savedText = prefs.getString("text", getString(R.string.default_text))
      text = savedText
      gravity = android.view.Gravity.CENTER
      setPadding(padding, padding, padding, padding)
      isClickable = true

      // Auto-size text to fill available space, no wrapping
      setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM)
      setAutoSizeTextTypeUniformWithConfiguration(24, 200, 2, TypedValue.COMPLEX_UNIT_SP)
      // Set maxLines to number of linebreaks in text (prevents unwanted wrapping)
      val lineCount = savedText?.count { it == '\n' }?.plus(1) ?: 1
      maxLines = lineCount

      setOnLongClickListener {
        showEditDialog()
        true
      }
    }

    root.addView(
      textView,
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT,
      ),
    )

    setContentView(root)
    applyTheme()

    root.setOnApplyWindowInsetsListener { _, insets ->
      lastInsets = insets
      resetCutoutPadding()
      insets
    }
    // The listener is attached after the window's first inset dispatch, so ask for another
    // one. Without this the padding can stay at the un-adjusted base until something else
    // triggers a pass, such as a rotation.
    root.requestApplyInsets()

    // Cutout padding can only be decided once the text has been laid out, since it depends on
    // where the lines actually landed. Pre-draw is the first point where that's known.
    textView.viewTreeObserver.addOnPreDrawListener { resolveCutoutPadding() }
  }

  /** Drops back to uniform padding and schedules a fresh cutout decision. */
  private fun resetCutoutPadding() {
    val base = dp(24)
    cutoutPaddingResolved = false
    textView.setPadding(base, base, base, base)
  }

  /**
   * Widens padding on an edge only where a cutout actually overlaps a line of text.
   *
   * Returns false to skip one frame when the padding changed, so the text is never drawn at the
   * old padding. Deliberately evaluates at most once per layout generation; see
   * [cutoutPaddingResolved].
   */
  private fun resolveCutoutPadding(): Boolean {
    if (cutoutPaddingResolved) return true
    val insets = lastInsets ?: return true
    val layout = textView.layout ?: return true

    cutoutPaddingResolved = true
    val base = dp(24)
    val padding = cutoutPadding(insets, textLineRects(layout), base)

    val unchanged = padding[0] == textView.paddingLeft &&
      padding[1] == textView.paddingTop &&
      padding[2] == textView.paddingRight &&
      padding[3] == textView.paddingBottom
    if (unchanged) return true

    textView.setPadding(padding[0], padding[1], padding[2], padding[3])
    return false
  }

  /**
   * The laid-out text lines as window-coordinate rectangles.
   *
   * Per line rather than one box around the whole block: with multi-line text, a cutout beside a
   * short line shouldn't inset the long ones.
   */
  private fun textLineRects(layout: android.text.Layout): List<Rect> {
    val location = IntArray(2)
    textView.getLocationInWindow(location)

    // TextView centers the Layout itself when gravity is CENTER, and doesn't expose that offset,
    // so mirror the calculation. Line coordinates are relative to the Layout, not the view.
    val innerHeight = textView.height - textView.compoundPaddingTop - textView.compoundPaddingBottom
    val verticalOffset = ((innerHeight - layout.height) / 2).coerceAtLeast(0)
    val originX = location[0] + textView.compoundPaddingLeft
    val originY = location[1] + textView.compoundPaddingTop + verticalOffset

    return (0 until layout.lineCount).map { line ->
      Rect(
        originX + layout.getLineLeft(line).toInt(),
        originY + layout.getLineTop(line),
        originX + layout.getLineRight(line).toInt(),
        originY + layout.getLineBottom(line),
      )
    }
  }

  /**
   * Padding as [left, top, right, bottom], starting from [base] and growing only on edges where a
   * cutout genuinely overlaps a line of text.
   *
   * Pointedly does not use `safeInsetTop` and friends. Those are bands spanning an entire edge,
   * far larger than the hole: on a punch-hole phone, clearing a ~120px circle costs the full
   * screen width. Each cutout's own bounding rect decides both whether to inset and by how much,
   * so the sign only gives up the strip the camera actually sits in.
   *
   * `boundingRects` is API 28; the per-edge `boundingRectTop` accessors are API 29, which is
   * above this app's minimum.
   */
  private fun cutoutPadding(insets: WindowInsets, lines: List<Rect>, base: Int): IntArray {
    val padding = intArrayOf(base, base, base, base)
    // Guarding here rather than at the call site keeps every DisplayCutout reference, including
    // the ones in this method's body, provably API 28+ for lint.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return padding
    val cutout = insets.displayCutout ?: return padding

    val width = root.width
    val height = root.height
    for (bounds in cutout.boundingRects) {
      if (bounds.isEmpty) continue
      if (lines.none { Rect.intersects(it, bounds) }) continue
      // Inset from whichever edge this cutout is attached to, by exactly enough to clear it.
      if (bounds.left <= 0) padding[0] = maxOf(padding[0], bounds.right)
      if (bounds.top <= 0) padding[1] = maxOf(padding[1], bounds.bottom)
      if (width > 0 && bounds.right >= width) padding[2] = maxOf(padding[2], width - bounds.left)
      if (height > 0 && bounds.bottom >= height) padding[3] = maxOf(padding[3], height - bounds.top)
    }
    return padding
  }

  private fun applyTheme() {
    val inverted = prefs.getBoolean("inverted", false)
    if (inverted) {
      root.setBackgroundColor(android.graphics.Color.WHITE)
      textView.setTextColor(android.graphics.Color.BLACK)
    } else {
      root.setBackgroundColor(android.graphics.Color.BLACK)
      textView.setTextColor(android.graphics.Color.WHITE)
    }
  }

  private fun updateText(newText: String) {
    textView.text = newText
    val lineCount = newText.count { it == '\n' }.plus(1)
    textView.maxLines = lineCount
    prefs.edit().putString("text", newText).apply()
    resetCutoutPadding()
    addToHistory(newText)
  }

  private fun addToHistory(text: String) {
    val historyJson = prefs.getString("history", "[]")
    val history =
      JSONArray(historyJson).let { arr ->
        (0 until arr.length()).map { arr.getString(it) }.toMutableList()
      }
    history.removeAll { it == text }
    history.add(0, text)
    if (history.size > 5) history.removeAt(history.size - 1)
    prefs.edit().putString("history", JSONArray(history).toString()).apply()
  }

  private fun getHistory(): List<String> {
    val historyJson = prefs.getString("history", "[]")
    return (0 until JSONArray(historyJson).length()).map {
      JSONArray(historyJson).getString(it)
    }
  }

  private fun deleteFromHistory(text: String) {
    val historyJson = prefs.getString("history", "[]")
    val history =
      JSONArray(historyJson).let { arr ->
        (0 until arr.length()).map { arr.getString(it) }.toMutableList()
      }
    history.removeAll { it == text }
    prefs.edit().putString("history", JSONArray(history).toString()).apply()
  }

  private fun showEditDialog() {
    val input =
      EditText(this).apply {
        setText(textView.text)
        hint = getString(R.string.text_hint)
        minHeight = 200
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        // Always use white text in dialog for readability, regardless of invert state
        setTextColor(android.graphics.Color.WHITE)
        setHintTextColor(android.graphics.Color.GRAY)
      }

    val invertCheck =
      CheckBox(this).apply {
        text = getString(R.string.invert)
        isChecked = prefs.getBoolean("inverted", false)
        setOnCheckedChangeListener { _, isChecked ->
          prefs.edit().putBoolean("inverted", isChecked).apply()
          applyTheme()
        }
      }

    val history = getHistory()
    var historyList: ListView? = null
    var historyLabel: TextView? = null

    if (history.isNotEmpty()) {
      historyLabel = TextView(this).apply {
        text = getString(R.string.recent)
        textSize = 12f
        setPadding(0, 12, 0, 4)
      }

      historyList = ListView(this).apply {
        adapter = HistoryAdapter(
          this@MainActivity,
          history.toMutableList(),
          onSelect = { selected -> input.setText(selected) },
          onDelete = { deletedText ->
            deleteFromHistory(deletedText)
            (adapter as HistoryAdapter).remove(deletedText)
          },
        )
      }
    }

    val margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics).toInt()
    val container =
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = FrameLayout.LayoutParams(
          FrameLayout.LayoutParams.MATCH_PARENT,
          FrameLayout.LayoutParams.WRAP_CONTENT,
        )
        setPadding(margin, margin, margin, 0)

        addView(
          invertCheck,
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
          ),
        )

        addView(
          input,
          LinearLayout
            .LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT,
              LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
              topMargin = 12
            },
        )

        if (historyLabel != null && historyList != null) {
          addView(historyLabel)

          // Set list to wrap content initially, then measure actual height
          historyList.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
          )
          addView(historyList)

          // Measure actual content height and constrain to 90% of screen
          historyList.viewTreeObserver.addOnPreDrawListener(
            object : android.view.ViewTreeObserver.OnPreDrawListener {
              override fun onPreDraw(): Boolean {
                historyList.viewTreeObserver.removeOnPreDrawListener(this)

                val screenHeight = resources.displayMetrics.heightPixels
                val maxListHeight =
                  (screenHeight * 0.9).toInt() -
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 300f, resources.displayMetrics).toInt()

                val actualHeight = historyList.measuredHeight
                val finalHeight = minOf(actualHeight, maxListHeight)

                historyList.layoutParams = LinearLayout.LayoutParams(
                  LinearLayout.LayoutParams.MATCH_PARENT,
                  finalHeight,
                )
                return true
              }
            },
          )
        }
      }

    AlertDialog
      .Builder(this)
      .setView(container)
      .setPositiveButton("OK") { _, _ ->
        val newText = input.text.toString()
        updateText(newText)
      }.setNegativeButton("Cancel", null)
      .show()
  }

  private inner class HistoryAdapter(
    context: Context,
    private val items: MutableList<String>,
    private val onSelect: (String) -> Unit,
    private val onDelete: (String) -> Unit,
  ) : ArrayAdapter<String>(context, 0, items) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
      val itemText = items[position]

      val row =
        LinearLayout(context).apply {
          layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
          )
          orientation = LinearLayout.HORIZONTAL
          gravity = android.view.Gravity.CENTER_VERTICAL
          minimumHeight = dp(48)
          background = themedDrawable(android.R.attr.selectableItemBackground)
          // The row handles its own click rather than relying on the ListView's
          // OnItemClickListener: a row containing a focusable child (the delete button)
          // never fires that listener, which is why tapping a history entry did nothing.
          setOnClickListener { onSelect(itemText) }
        }

      row.addView(
        TextView(context).apply {
          // Collapse newlines so a multi-line entry stays one row.
          text = itemText.replace('\n', ' ')
          maxLines = 2
          ellipsize = android.text.TextUtils.TruncateAt.END
          layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
          setPadding(dp(8), dp(8), dp(8), dp(8))
        },
      )

      row.addView(
        Button(context, null, android.R.attr.borderlessButtonStyle).apply {
          text = context.getString(R.string.delete)
          textSize = 18f
          minWidth = 0
          minimumWidth = 0
          layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
          // Keeps the button clickable without making it focusable, so it no longer
          // competes with the row for the tap.
          isFocusable = false
          isFocusableInTouchMode = false
          setOnClickListener { onDelete(itemText) }
        },
      )

      return row
    }

    fun remove(item: String) {
      items.remove(item)
      notifyDataSetChanged()
    }
  }

  private fun dp(value: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

  /** Resolves a theme attribute that points at a drawable, e.g. a ripple background. */
  private fun themedDrawable(attr: Int): android.graphics.drawable.Drawable? {
    val value = TypedValue()
    theme.resolveAttribute(attr, value, true)
    return getDrawable(value.resourceId)
  }
}
