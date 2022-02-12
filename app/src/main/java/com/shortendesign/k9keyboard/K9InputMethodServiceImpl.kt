package com.shortendesign.k9keyboard

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.shortendesign.k9keyboard.dao.SettingDao
import com.shortendesign.k9keyboard.dao.WordDao
import com.shortendesign.k9keyboard.db.AppDatabase
import com.shortendesign.k9keyboard.entity.Setting
import com.shortendesign.k9keyboard.entity.Word
import com.shortendesign.k9keyboard.inputmode.WordInputMode
import com.shortendesign.k9keyboard.trie.Node
import com.shortendesign.k9keyboard.trie.T9Trie
import com.shortendesign.k9keyboard.util.KeyCodeMapping
import com.shortendesign.k9keyboard.util.LetterLayout
import com.shortendesign.k9keyboard.util.Status
import kotlinx.coroutines.*
import kotlin.collections.ArrayList


class K9InputMethodServiceImpl() : InputMethodService(), K9InputMethodService {
    private val LOG_TAG: String = "K9Input"
    private var mode: WordInputMode? = null
    private lateinit var db: AppDatabase
    private lateinit var wordDao: WordDao
    private lateinit var settingDao: SettingDao
    private val keypad = Keypad(KeyCodeMapping.basic, LetterLayout.enUS)
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var isComposing = false
    private var inputConnection: InputConnection? = null
    private var cursorPosition: Int = 0
    private var modeStatus = Status.WORD_CAP
    private val t9Trie = T9Trie()

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getInstance(this)
        wordDao = db.getWordDao()
        settingDao = db.getSettingDao()
        initializeWordsFirstTime()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        showStatusIcon(R.drawable.ime_en_lang_single)
        cursorPosition = info?.initialSelEnd ?: 0

        when (info!!.inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER ->
                Log.d(LOG_TAG, "TYPE_CLASS_NUMBER")
            InputType.TYPE_CLASS_DATETIME ->                // Numbers and dates default to the symbols keyboard, with
                Log.d(LOG_TAG, "TYPE_CLASS_DATETIME")
            InputType.TYPE_CLASS_PHONE ->                // Phones will also default to the symbols keyboard, though
                Log.d(LOG_TAG, "TYPE_CLASS_PHONE")
            InputType.TYPE_CLASS_TEXT ->
                Log.d(LOG_TAG, "TYPE_CLASS_TEXT")
            else ->
                Log.d(LOG_TAG, "TYPE_OTHER" + (info.inputType and InputType.TYPE_MASK_CLASS))
        }
        if (this.t9Trie.root == null) {
            Node.setSupportedChars("123456789")
            this.t9Trie.add("2", "a")
            scope.launch {
                initT9Trie()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.i(LOG_TAG, "keyCode: $keyCode")
        val mode = this.mode ?: return false
        val result = mode.getKeyCodeResult(keyCode)
        updateStatusIcon(mode.status)

        if (result != null) {
            //Log.d(LOG_TAG, "CODEWORD: ${result.codeWord}")
            if (!result.consumed) {
                finishComposing()
                // Delete or back
                if (event?.keyCode == KeyEvent.KEYCODE_BACK) {
                    return if (cursorPosition > 0)
                        inputConnection?.deleteSurroundingText(1, 0) ?: false
                    else
                        false
                }
            }
            // If we get back a code word, we'll need to resolve it
            when {
                result.codeWord != null -> {
                    isComposing = true
                    resolveCodeWord(result.codeWord, 1)
                }
                result.word != null -> {
                    // TODO: Support a delay for committing the word
                    finishComposing()
                    inputConnection?.commitText(result.word, 2)
                }
                else -> {
                    finishComposing()
                }
            }

            return result.consumed
        }
        else {
            return false
        }
    }

    private fun finishComposing() {
        if (isComposing) {
            inputConnection?.finishComposingText()
            isComposing = false
        }
    }

    fun resolveCodeWord(codeWord: String, cursorPosition: Int) {
        val candidates = t9Trie.getCandidates(codeWord)
        val candidate = mode!!.resolveCodeWord(codeWord, candidates)
        if (candidate != null) inputConnection?.setComposingText(candidate, cursorPosition)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return super.onKeyUp(keyCode, event)
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int,
                                   newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        Log.d(LOG_TAG, "Cursor pos: $newSelEnd")
        cursorPosition = newSelEnd
        mode?.setCursorPosition(newSelEnd)
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
    }

    override fun onCreateCandidatesView(): View {
        val candidatesView =
            CandidateView(applicationContext)
        candidatesView.setSuggestions(listOf("Ball", "Call", "Mall", "Tall", "Fall", "Pall", "Gall"))
        setCandidatesViewShown(true)
        return candidatesView
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        inputConnection = currentInputConnection
        val mode = WordInputMode(
            keypad = keypad,
        )
        this.mode = mode
        mode.setCursorPosition(attribute!!.initialSelStart.coerceAtLeast(attribute.initialSelEnd))
        updateStatusIcon(mode.status)
    }

    override fun onFinishInput() {
        super.onFinishInput()
        hideStatusIcon()
        this.mode = null
    }

    override suspend fun findCandidates(word: String): List<Word> {
        return wordDao.findCandidates(word)
    }

    override fun setComposingText(text: String, cursorPosition: Int) {
        inputConnection?.setComposingText(text, cursorPosition)
    }

    override fun commitText(text: String, cursorPosition: Int) {
        inputConnection?.commitText(text, cursorPosition)
    }

    /**
     * Factory method for a Word object given a string (and optional metadata)
     */
    fun getWord(word: String, frequency: Int = 1, locale: String = "en_US") = Word(
        word = word,
        code = keypad.getCodeForWord(word),
        length = word.length,
        frequency = frequency,
        locale = locale
    )

    private fun updateStatusIcon(status: Status) {
        if (status != modeStatus) {
            showStatusIcon(
                when (status) {
                    Status.WORD_CAP -> R.drawable.ime_en_lang_single
                    else -> 0
                }
            )
        }
    }

    private suspend fun initT9Trie() {
        Log.d(LOG_TAG, "Initializing T9 trie")
        t9Trie.clear()
        for (char in "123456789") {
            Log.d(LOG_TAG, "Finding candidates for '$char'")
            val words = wordDao.findCandidates(char.toString())
            Log.d(LOG_TAG, "Found '${words.count()}'")
            for (word in words) {
                Log.d(LOG_TAG, "Trie ${word.code} => ${word.word}")
                t9Trie.add(word.code, word.word)
            }
        }
    }

    private fun initializeWordsFirstTime() {
        // TODO: Enumerate the settings keys
        Log.d(LOG_TAG,"Checking for initialized DB asynchronously...")
        scope.launch {
            val setting = settingDao.getByKey("initialized")
            if (setting == null) {
                Log.d(LOG_TAG,"Initializing word database...")
                initializeWords()
                Setting.set("initialized", "t", settingDao)
            }
            else {
                Log.d(LOG_TAG, "Word database already initialized")
            }
        }
    }

    private fun initializeWords() {
        val file_name = "t9_words.txt"
        val batchSize = 500
        val wordBatch = ArrayList<Word>(batchSize)
        var freq: Int
        application.assets.open(file_name).bufferedReader().useLines { lines ->
            for (chunk in lines.chunked(batchSize)) {
                wordBatch.clear()
                chunk.forEachIndexed { index, line ->
                    //val parts = line.split(" ")
                    //freq = parts[1].toFloat().toInt()
                    wordBatch.add(
                        index,
                        getWord(word = line, frequency = 1)
                    )
                }
                runBlocking {
                    wordDao.insert(*wordBatch.toTypedArray())
                }
            }
        }
    }
}