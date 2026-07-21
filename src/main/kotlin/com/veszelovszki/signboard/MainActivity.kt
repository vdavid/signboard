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
  private lateinit var signboard: SignboardView
  private lateinit var root: FrameLayout
  private val prefs by lazy { getSharedPreferences("signboard", Context.MODE_PRIVATE) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Let the window extend under the camera cutout instead of letting the system letterbox the
    // app away from it. Without this the framework reserves the whole cutout strip across the full
    // width (or height, in landscape), so the sign loses that band on every screen even though the
    // hole itself is small. SignboardView then flows the text around the hole per line.
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

    val padding = dp(24)
    signboard = SignboardView(this).apply {
      text = prefs.getString("text", getString(R.string.default_text)).orEmpty()
      setPadding(padding, padding, padding, padding)
      isClickable = true
      setOnLongClickListener {
        showEditDialog()
        true
      }
    }

    root.addView(
      signboard,
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT,
      ),
    )

    setContentView(root)
    applyTheme()

    root.setOnApplyWindowInsetsListener { _, insets ->
      signboard.setCutouts(cutoutRects(insets))
      insets
    }
    // The listener is attached after the window's first inset dispatch, so ask for another one.
    // Without this the cutouts stay unknown until something else triggers a pass, such as a
    // rotation.
    root.requestApplyInsets()
  }

  /**
   * Cutout rectangles in window coordinates, empty on anything below API 28.
   *
   * Deliberately the bounding rects rather than `safeInsetTop` and friends: a safe inset is a band
   * spanning a whole edge, sized to clear the hole, so using it hands over far more screen than the
   * camera occupies. [SignboardView] wants to know where the hole actually is.
   */
  private fun cutoutRects(insets: WindowInsets): List<Rect> {
    // Guarding here rather than at the call site keeps every DisplayCutout reference in this
    // method provably API 28+ for lint.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return emptyList()
    val cutout = insets.displayCutout ?: return emptyList()
    return cutout.boundingRects.filterNot { it.isEmpty }
  }

  private fun applyTheme() {
    val inverted = prefs.getBoolean("inverted", false)
    if (inverted) {
      root.setBackgroundColor(android.graphics.Color.WHITE)
      signboard.textColor = android.graphics.Color.BLACK
    } else {
      root.setBackgroundColor(android.graphics.Color.BLACK)
      signboard.textColor = android.graphics.Color.WHITE
    }
  }

  private fun updateText(newText: String) {
    signboard.text = newText
    prefs.edit().putString("text", newText).apply()
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
        setText(signboard.text)
        hint = getString(R.string.text_hint)
        // Height follows the content. The previous fixed minimum was in raw pixels, so on a dense
        // screen it reserved several lines' worth of space and left a large gap between a
        // one-line value and the underline.
        minLines = 1
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
        setPadding(0, dp(12), 0, dp(4))
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

    val margin = dp(16)
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
              topMargin = dp(8)
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
                val maxListHeight = (screenHeight * 0.9).toInt() - dp(300)

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
