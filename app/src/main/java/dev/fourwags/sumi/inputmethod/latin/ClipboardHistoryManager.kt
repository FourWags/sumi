package dev.fourwags.sumi.inputmethod.latin

/** Legacy UI compatibility only. No owner, listener, clipboard, history, files or jobs retained. */
@Suppress("UNUSED_PARAMETER")
class ClipboardHistoryManager(latinIME: LatinIME) : android.content.ClipboardManager.OnPrimaryClipChangedListener {
    fun onCreate() {}
    fun onDestroy() {}
    override fun onPrimaryClipChanged() {}
    fun onPinnedClipsAvailable(pinnedClips: List<ClipboardHistoryEntry>) {}
    fun toggleClipPinned(ts: Long) {}
    fun clearHistory() {}
    fun prepareClipboardHistory() {}
    fun getHistorySize() = 0
    fun getHistoryEntry(position: Int): ClipboardHistoryEntry = throw IndexOutOfBoundsException("Clipboard history is disabled")
    fun getHistoryEntryContent(timeStamp: Long): ClipboardHistoryEntry = throw NoSuchElementException("Clipboard history is disabled")
    fun setHistoryChangeListener(l: OnHistoryChangeListener?) {}
    fun retrieveClipboardContent(): CharSequence = ""
    interface OnHistoryChangeListener {
        fun onClipboardHistoryEntryAdded(at: Int)
        fun onClipboardHistoryEntriesRemoved(pos: Int, count: Int)
        fun onClipboardHistoryEntryMoved(from: Int, to: Int)
    }
}
