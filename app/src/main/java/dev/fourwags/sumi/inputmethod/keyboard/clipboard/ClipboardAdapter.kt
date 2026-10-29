package dev.fourwags.sumi.inputmethod.keyboard.clipboard

import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.fourwags.sumi.inputmethod.latin.ClipboardHistoryEntry
import dev.fourwags.sumi.inputmethod.latin.ClipboardHistoryManager
import dev.fourwags.sumi.R

class ClipboardAdapter(
       val clipboardLayoutParams: ClipboardLayoutParams,
       val keyEventListener: OnKeyEventListener
) : RecyclerView.Adapter<ClipboardAdapter.ViewHolder>() {

    var clipboardHistoryManager: ClipboardHistoryManager? = null

    var pinnedIconResId = 0
    var itemBackgroundId = 0
    var itemTypeFace: Typeface? = null
    var itemTextColor = 0
    var itemTextSize = 0f

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.clipboard_entry_key, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.setContent(getItem(position))
    }

    private fun getItem(position: Int) = clipboardHistoryManager?.getHistoryEntry(position)

    override fun getItemCount() = clipboardHistoryManager?.getHistorySize() ?: 0

    inner class ViewHolder(
            view: View
    ) : RecyclerView.ViewHolder(view), View.OnClickListener, View.OnLongClickListener {

        private val pinnedIconView: ImageView
        private val contentView: TextView

        init {
            view.apply {
                setOnClickListener(this@ViewHolder)
                setOnLongClickListener(this@ViewHolder)
                setBackgroundResource(itemBackgroundId)
            }
            pinnedIconView = view.findViewById<ImageView>(R.id.clipboard_entry_pinned_icon).apply {
                visibility = View.GONE
                setImageResource(pinnedIconResId)
            }
            contentView = view.findViewById<TextView>(R.id.clipboard_entry_content).apply {
                typeface = itemTypeFace
                setTextColor(itemTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, itemTextSize)
            }
            clipboardLayoutParams.setItemProperties(view)
        }

        fun setContent(historyEntry: ClipboardHistoryEntry?) {
            itemView.tag = historyEntry?.timeStamp
            contentView.text = historyEntry?.content
            pinnedIconView.visibility = if (historyEntry?.isPinned == true) View.VISIBLE else View.GONE
        }

        override fun onClick(view: View) {
            val clipId = view.tag as? Long ?: return
            keyEventListener.onKeyDown(clipId)
            keyEventListener.onKeyUp(clipId)
        }

        override fun onLongClick(view: View): Boolean {
            val clipId = view.tag as? Long ?: return false
            clipboardHistoryManager?.toggleClipPinned(clipId)
            return true
        }
    }

}