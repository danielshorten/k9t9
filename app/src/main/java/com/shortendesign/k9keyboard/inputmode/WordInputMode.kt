package com.shortendesign.k9keyboard.inputmode

import com.shortendesign.k9keyboard.KeyPressResult
import com.shortendesign.k9keyboard.Keypad
import com.shortendesign.k9keyboard.util.Command
import com.shortendesign.k9keyboard.util.Key
import com.shortendesign.k9keyboard.util.KeyCommandResolver
import com.shortendesign.k9keyboard.util.Status
import java.lang.StringBuilder
import java.util.*

class WordInputMode(
    private val keypad: Keypad,
): InputMode {
    private val LOG_TAG: String = "K9Word"
    val codeWord = StringBuilder()
    var caseTransformer: CaseTransformer? = null
    // Index chosen by cycling through candidates with next key
    private var candidateIdx: Int = 0
    private var lastResolvedCodeWord: String? = null
    private var newlineShortCommand: Command? = null

    private var keyCommandResolver: KeyCommandResolver? = null

    override val status: Status
        get() = this.caseTransformer?.status ?: Status.WORD_CAP

    private val shouldRecomposeBeforeRegex = """([\w]+?)(\n*)\Z""".toRegex()
    private val shouldRecomposeAfterRegex = """^([\w]+)""".toRegex()
    // TODO: maybe this could move into LetterCase
    private val endOfSentence = """[.!]+\s*""".toRegex()

    override fun load(parent: KeyCommandResolver, properties: Properties?,
                      beforeText: CharSequence?) {
        finishComposing()
        caseTransformer = CaseTransformer(if (beforeText != null) {
            if (beforeText == "" || endOfSentence.find(beforeText) != null) {
                Status.WORD_CAP
            } else {
                Status.WORD
            }
        } else {
            Status.WORD_CAP
        }, Status.WORD, Status.WORD_CAP, Status.WORD_UPPER)

        if (keyCommandResolver != null) {
            return
        }
        val resolver = KeyCommandResolver(
            hashMapOf(
                Key.STAR to Command.CYCLE_CANDIDATES,
            ),
            hashMapOf(
                Key.N0 to Command.NEWLINE
            ),
            parent
        )
        if (properties != null) {
            resolver.overrideFromProperties(properties, "command.word")
        }
        keyCommandResolver = resolver
    }

    /**
     *
     */
    override fun getKeyCodeResult(key: Key, repeatCount: Int, longPress: Boolean,
                                  textBeforeCursor: CharSequence?, textAfterCursor: CharSequence?): KeyPressResult {
        val command = keyCommandResolver?.getCommand(key, longPress)
        // Swallow regular keypress repeats that arent navigate or delete commands
        if (!longPress && repeatCount > 0 && !setOf(Command.NAVIGATE, Command.DELETE).contains(command)) {
            return state(consumed = true)
        }
        val result = when(command) {
            Command.CHARACTER -> {
                addLetter(key)
            }
            Command.SPACE, Command.NEWLINE -> {
                addSpace(command == Command.NEWLINE)
            }
            Command.DELETE -> {
                deleteLetter()
            }
            Command.NAVIGATE -> {
                navigate(key, textBeforeCursor, textAfterCursor)
            }
            Command.CYCLE_CANDIDATES -> {
                nextCandidate()
            }
            Command.SHIFT_MODE -> {
                shiftMode()
            }
            else -> {
                if (codeWord.isNotEmpty()) {
                    finishComposing()
                }
                state(false)
            }
        }
        if (command != null) {
            recordNewlineShortCommand(command, key, longPress)
        }
        return result
    }

    private fun addLetter(key: Key): KeyPressResult {
        codeWord.append(key.code)
        caseTransformer?.signalTyping(codeWord.length)
        //Log.d(LOG_TAG, "MASK: ${Integer.toBinaryString(caseMask.toInt())}")
        return state(true, codeWord = codeWord.toString())
    }

    private fun deleteLetter(): KeyPressResult {
        var consumed = false
        if (isComposing()) {
            codeWord.deleteAt(codeWord.length - 1)
            caseTransformer?.signalDelete(codeWord.length)
            consumed = codeWord.isNotEmpty()
            // If we've deleted the whole word we were composing, reset the candidate index
            if (!consumed) {
                candidateIdx = 0
            }
        }
        return state(consumed, codeWord = codeWord.toString())
    }

    private fun addSpace(newline: Boolean = false): KeyPressResult {
        finishComposing()
        caseTransformer?.signalSpace()
        val cursorOffset = when {
            // Handle the case where the corresponding short command for the newline is one that we
            // want to undo.  For now this is only SPACE.
            newline && newlineShortCommand == Command.SPACE -> -1
            else -> 0
        }
        return state(
            consumed = true,
            word = if (newline) "\n" else " ",
            // Delete the previously-entered space (short press) when adding the newline (long press)
            cursorOffset = cursorOffset)
    }

    private fun nextCandidate(): KeyPressResult {
        if (isComposing()) {
            candidateIdx++
        }
        return state(true)
    }

    private fun shiftMode(): KeyPressResult {
        caseTransformer?.shiftMode()
        return state()
    }

    private fun navigate(key: Key, beforeCursor: CharSequence?, afterCursor: CharSequence?): KeyPressResult {
        if (codeWord.isEmpty()) {
            val beforeMatches =
                if (beforeCursor != null) shouldRecomposeBeforeRegex.find(beforeCursor) else null
            val afterMatches =
                if (afterCursor != null) shouldRecomposeAfterRegex.find(afterCursor) else null

            val cursorOffset = if (key == Key.RIGHT && afterMatches != null) {
                afterMatches.groups[0]?.value!!.length
            } else if (key == Key.LEFT && beforeMatches != null && beforeMatches.groups[2]?.value.equals("")) {
                -beforeMatches.groups[0]?.value!!.length
            } else {
                return state(false)
            }

            val afterText = afterMatches?.groups?.get(0)?.value ?: ""
            val beforeText = if (beforeMatches?.groups?.get(2)?.value?.equals("") == true) beforeMatches.groups[1]?.value else ""
            val recomposingWord = beforeText + afterText
            codeWord.clear()
            codeWord.append(keypad.getCodeForWord(recomposingWord))
            caseTransformer?.init(recomposingWord)
            return state(
                true,
                word = recomposingWord,
                recomposing = true,
                cursorOffset = cursorOffset
            )
        }
        else {
            val codeWordLength = codeWord.length
            finishComposing()
            // Treat RIGHT or LEFT as consumed to allow pressing directions to end composing
            // but not send it on as a keypress to move to the next input or something
            // annoying.
            return state(
                consumed = setOf(Key.RIGHT, Key.LEFT).contains(key),
                cursorOffset = if (key == Key.LEFT) -codeWordLength else 0
            )
        }
    }

    private fun state(consumed: Boolean = true, command: Command? = null, codeWord: String = "", word: String? = null,
                      recomposing: Boolean = false, cursorOffset: Int = 0): KeyPressResult {
        var finalCodeWord: String? = codeWord
        if (finalCodeWord!!.isEmpty()) {
            finalCodeWord = this.codeWord.toString()
            if (finalCodeWord.isEmpty()) {
                finalCodeWord = null
            }
        }
        return KeyPressResult(
            consumed = consumed,
            command = command,
            codeWord = finalCodeWord,
            word = word,
            recomposing = recomposing,
            cursorOffset = cursorOffset
        )
    }

    private fun finishComposing() {
        codeWord.clear()
        caseTransformer?.init()
        candidateIdx = 0
        lastResolvedCodeWord = null
    }

    private fun isComposing(): Boolean {
        return codeWord.isNotEmpty()
    }

    private fun recordNewlineShortCommand(command: Command, key: Key?, longPress: Boolean) {
        // Track if this is the short keypress for the same long keypress that does a newline.
        // If it is, record the command so we can undo it if necessary.
        newlineShortCommand = when {
            !longPress && key != null
                    && keyCommandResolver?.getCommand(key, true) == Command.NEWLINE -> command
            else -> null
        }
    }

    /**
     * Resolve a code word to an actual word, using the available information
     *
     * codeWord: the code word to resolve
     * candidates: list of words that resolve to the codeword
     * final: this is the second & final chance to resolve the code word
     * searchForWord: we are resetting to a previously-written word, so attempt to reset state to
     *                a particular word.
     * deleting: support deleting as a special case where we don't reset to a previously-resolved
     *           codeword if the codeword doesn't resolve to a word
     */
    override fun resolveCodeWord(codeWord: String, candidates: List<String>, final: Boolean,
                                 resetToWord: String?): String? {
        if (!candidates.isEmpty()) {
            var candidateWord: String? = null
            if (resetToWord != null) {
                candidates.forEachIndexed { idx, candidate ->
                    if (candidate.equals(resetToWord)) {
                        candidateWord = candidate
                        candidateIdx = idx
                    }
                }
            }

            // Get the candidate at the correct index, based on which one we've chosen
            candidateWord = when {
                candidateWord != null -> candidateWord
                candidateIdx < candidates.count() -> candidates[candidateIdx]
                candidateIdx >= candidates.count() -> candidates[0]
                else -> null
            } ?: return null
            // Reset the candidate index if it's falling out of bounds
            if (candidateIdx >= candidates.count() && candidates.count() > 0) {
                candidateIdx = 0
            }

            // Replace the code word with the one we're resolving.
            // This handles the case where we've recorded a bunch of key presses but haven't been
            // able to resolve them to any actual word.  We keep the codeWord for the ones we found.
            this.replaceCodeWord(codeWord)
            // Truncate the word if it's longer than the actual number of keys that have been
            // pressed.
            if (candidateWord!!.length > codeWord.length) {
                candidateWord = candidateWord!!.substring(0, codeWord.length)
            }

            candidateWord = caseTransformer?.applyCaseMask(candidateWord!!)

            lastResolvedCodeWord = codeWord
            //Log.d(LOG_TAG, "LAST RESOLVED CODE WORD: ${lastResolvedCodeWord}")
            caseTransformer?.signalEndOfSentence(candidateWord!!)
            return resetToWord ?: candidateWord
        }
        else if (resetToWord != null) {
            // If we're resetting to a previously composed word, force the codeword
            this.replaceCodeWord(this.keypad.getCodeForWord(resetToWord))
            lastResolvedCodeWord = codeWord
        }
        // If this was the final chance to resolve the code word, and we couldn't...reset to the last
        // resolved code word.  Don't do this if we're deleting so the delete actually works.
        if (final && lastResolvedCodeWord != null) {
//            Log.d(LOG_TAG, "FINALLY REPLACING WITH: ${lastResolvedCodeWord}")
            this.replaceCodeWord(lastResolvedCodeWord!!)
        }
        return null
    }

    private fun replaceCodeWord(codeWord: String) {
        this.codeWord.replace(0, maxOf(this.codeWord.length, 0), codeWord)
    }

}