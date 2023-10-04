package com.shortendesign.k9keyboard.util

import android.view.inputmethod.InputConnection
import com.shortendesign.k9keyboard.KeyPressResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class InputProxy (
    private val inputConnection: InputConnection,
    private val scope: CoroutineScope,
    private var cursorPosition: Int
) {
    private var isComposing = false

    // current delayed commit job
    private var delayedCommitJob: Job? = null
    // Keep track of if we're composing - we use this to help know when to commit a finished word
    private var lastComposingText: String? = null


    fun setCursorPosition(position: Int) {
        cursorPosition = position
    }

    fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? {
        return inputConnection.getTextBeforeCursor(n, flags)
    }

    /**
     * Delete a single character
     * Return true if the delete was possible, false if we were at the beginning of the input and
     * couldn't delete.
     */
    fun deleteOneCharacter(): Boolean {
        return if (cursorPosition > 0) {
            inputConnection.deleteSurroundingText(1, 0)
            true
        } else {
            // Otherwise, don't handle this key press so that it bubbles up as "back"
            false
        }
    }

    private fun handleRecomposing(result: KeyPressResult) {
        val newSel = cursorPosition + result.cursorOffset
        // Calculate the new cursor position base on cursor offset
        inputConnection.setSelection(newSel, newSel)
        // If we moved the cursor right, we'll delete the text to the left.
        val deleteLeft = result.cursorOffset > 0
        inputConnection.deleteSurroundingText(
            // Delete the characters before the cursor if we're deleting left
            if (deleteLeft) result.codeWord!!.length else 0,
            // Otherwise delete the characters after the cursor (right)
            if (deleteLeft) 0 else result.codeWord!!.length,
        )
        setComposingText(result.word!!)
    }

    private fun setComposingText(composingText: String, commitDelay: Long? = null) {
        isComposing = true
        lastComposingText = composingText
        inputConnection.setComposingText(composingText, 1)
        if (commitDelay != null) {
            delayedCommitJob?.cancel()
            delayedCommitJob = scope.launch {
                delay(commitDelay)
                finishComposing()
                mode?.resolveCodeWord("", listOf(), resetToWord = composingText)
            }
        }
    }

    fun finishComposing(cursorOffset: Int = 0) {
        if (isComposing) {
            inputConnection.finishComposingText()
            if (cursorOffset != 0) {
                val selection = cursorPosition + cursorOffset
                inputConnection.setSelection(selection, selection)
            }
            isComposing = false
            lastComposingText = null
        }
    }

}