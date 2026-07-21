package com.veszelovszki.signboard

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray

class MainActivity : AppCompatActivity() {
  private lateinit var textView: TextView
  private lateinit var root: FrameLayout
  private val prefs by lazy { getSharedPreferences("signboard", Context.MODE_PRIVATE) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Root container: keep screen on
    root = FrameLayout(this).apply {
      keepScreenOn = true
    }

    // Text display with auto-sizing to fill screen
    val padding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, resources.displayMetrics).toInt()
    textView = TextView(this).apply {
      val savedText = prefs.getString("text", "Long-press to edit")
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

    root.addView(textView, FrameLayout.LayoutParams(
      FrameLayout.LayoutParams.MATCH_PARENT,
      FrameLayout.LayoutParams.MATCH_PARENT
    ))

    setContentView(root)
    applyTheme()

    // Handle display cutout (camera hole)
    ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
      val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
      val basePadding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, resources.displayMetrics).toInt()

      val left = maxOf(basePadding, cutout.left)
      val top = maxOf(basePadding, cutout.top)
      val right = maxOf(basePadding, cutout.right)
      val bottom = maxOf(basePadding, cutout.bottom)

      textView.setPadding(left, top, right, bottom)
      insets
    }
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
    addToHistory(newText)
  }

  private fun addToHistory(text: String) {
    val historyJson = prefs.getString("history", "[]")
    val history = JSONArray(historyJson).let { arr ->
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
    val history = JSONArray(historyJson).let { arr ->
      (0 until arr.length()).map { arr.getString(it) }.toMutableList()
    }
    history.removeAll { it == text }
    prefs.edit().putString("history", JSONArray(history).toString()).apply()
  }

  private fun showEditDialog() {
    val input = EditText(this).apply {
      setText(textView.text)
      hint = "Enter text (supports newlines)"
      minHeight = 200
      inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
      // Always use white text in dialog for readability, regardless of invert state
      setTextColor(android.graphics.Color.WHITE)
      setHintTextColor(android.graphics.Color.GRAY)
    }

    val invertCheck = CheckBox(this).apply {
      text = "Invert"
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
        text = "Recent:"
        textSize = 12f
        setPadding(0, 12, 0, 4)
      }

      historyList = ListView(this).apply {
        adapter = HistoryAdapter(this@MainActivity, history.toMutableList()) { deletedText ->
          deleteFromHistory(deletedText)
          (adapter as HistoryAdapter).remove(deletedText)
        }
        setOnItemClickListener { _, _, position, _ ->
          input.setText(history[position])
        }
      }
    }

    val margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics).toInt()
    val container = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.WRAP_CONTENT
      )
      setPadding(margin, margin, margin, 0)

      addView(invertCheck, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ))

      addView(input, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply {
        topMargin = 12
      })

      if (historyLabel != null && historyList != null) {
        addView(historyLabel)

        // Set list to wrap content initially, then measure actual height
        historyList.layoutParams = LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        )
        addView(historyList)

        // Measure actual content height and constrain to 90% of screen
        historyList.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
          override fun onPreDraw(): Boolean {
            historyList.viewTreeObserver.removeOnPreDrawListener(this)

            val screenHeight = resources.displayMetrics.heightPixels
            val maxListHeight = (screenHeight * 0.9).toInt() - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 300f, resources.displayMetrics).toInt()

            val actualHeight = historyList.measuredHeight
            val finalHeight = minOf(actualHeight, maxListHeight)

            historyList.layoutParams = LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT,
              finalHeight
            )
            return true
          }
        })
      }
    }

    AlertDialog.Builder(this)
      .setView(container)
      .setPositiveButton("OK") { _, _ ->
        val newText = input.text.toString()
        updateText(newText)
      }
      .setNegativeButton("Cancel", null)
      .show()
  }

  private fun EditText.applyTextTheme() {
    val inverted = prefs.getBoolean("inverted", false)
    if (inverted) {
      setTextColor(android.graphics.Color.BLACK)
      setHintTextColor(android.graphics.Color.GRAY)
    } else {
      setTextColor(android.graphics.Color.WHITE)
      setHintTextColor(android.graphics.Color.GRAY)
    }
  }

  private inner class HistoryAdapter(
    context: Context,
    private val items: MutableList<String>,
    private val onDelete: (String) -> Unit
  ) : ArrayAdapter<String>(context, 0, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
      val itemText = items[position]
      val displayText = itemText.replace('\n', ' ')

      val view = LinearLayout(context).apply {
        layoutParams = ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT
        )
        orientation = LinearLayout.HORIZONTAL
        setPadding(8, 8, 8, 8)
      }

      val textView = TextView(context).apply {
        text = displayText
        layoutParams = LinearLayout.LayoutParams(
          0,
          LinearLayout.LayoutParams.WRAP_CONTENT,
          1f
        )
        setPadding(8, 8, 8, 8)
      }
      view.addView(textView)

      val deleteBtn = Button(context).apply {
        text = "×"
        textSize = 20f
        layoutParams = LinearLayout.LayoutParams(
          TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48f, resources.displayMetrics).toInt(),
          TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48f, resources.displayMetrics).toInt()
        )
        setOnClickListener {
          onDelete(itemText)
        }
      }
      view.addView(deleteBtn)

      return view
    }

    fun remove(item: String) {
      items.remove(item)
      notifyDataSetChanged()
    }
  }
}
