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
import com.shortendesign.k9keyboard.inputmode.InputMode
import com.shortendesign.k9keyboard.inputmode.K9InputType
import com.shortendesign.k9keyboard.inputmode.NumberInputMode
import com.shortendesign.k9keyboard.inputmode.WordInputMode
import com.shortendesign.k9keyboard.trie.Node
import com.shortendesign.k9keyboard.trie.T9Trie
import com.shortendesign.k9keyboard.util.KeyCodeMapping
import com.shortendesign.k9keyboard.util.LetterLayout
import com.shortendesign.k9keyboard.util.MissingLetterCode
import com.shortendesign.k9keyboard.util.Status
import kotlinx.coroutines.*


class K9InputMethodServiceImpl() : InputMethodService(), K9InputMethodService {
    private val LOG_TAG: String = "K9Input"
    private var mode: InputMode? = null
    private lateinit var db: AppDatabase
    private lateinit var wordDao: WordDao
    private lateinit var settingDao: SettingDao
    private val keypad = Keypad(KeyCodeMapping.basic, LetterLayout.enUS)
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var preloadJob: Job? = null
    private var isComposing = false
    private var inputConnection: InputConnection? = null
    private var cursorPosition: Int = 0
    private var modeStatus = Status.WORD_CAP
    private val t9Trie = T9Trie()
    private var currentMode = K9InputType.WORD.idx

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

    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.i(LOG_TAG, "keyCode: $keyCode")
        var consumed = true
        val mode = this.mode
        if (mode != null) {
            val result = mode.getKeyCodeResult(keyCode)
            consumed = result?.consumed ?: false
            updateStatusIcon(mode.status)

            if (result != null) {
                //Log.d(LOG_TAG, "CODEWORD: ${result.codeWord}")
                if (!result.consumed) {
                    consumed = handleUnconsumedKeyEvent(event)
                }
                handleInputModeResult(result)
            }
        }
        return consumed
    }

    private fun handleUnconsumedKeyEvent(event: KeyEvent?): Boolean {
        // Delete or back
        if (event?.keyCode == KeyEvent.KEYCODE_BACK) {
            finishComposing()
            return if (cursorPosition > 0) {
                inputConnection?.deleteSurroundingText(1, 0)
                true
            } else
                false
        }
        else if (event?.keyCode == KeyEvent.KEYCODE_POUND) {
            switchToNextInputMode()
            return true
        }
        return false
    }

    private fun handleInputModeResult(result: KeyPressResult) {
        // If we get back a code word, we'll need to resolve it
        when {
            result.codeWord != null -> {
                isComposing = true
                val candidate = resolveCodeWord(result.codeWord, 1)

                scope.launch {
                    preloadTrie(result.codeWord, 2, candidate == null)
                }
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
    }

    private fun finishComposing() {
        if (isComposing) {
            inputConnection?.finishComposingText()
            isComposing = false
        }
    }

    fun resolveCodeWord(codeWord: String, cursorPosition: Int, final: Boolean = false): String? {
        val candidates = t9Trie.getCandidates(codeWord, 10)
        Log.d(LOG_TAG, "CANDIDATES for $codeWord: $candidates")
        val candidate = mode!!.resolveCodeWord(codeWord, candidates, final)
        if (candidate != null) inputConnection?.setComposingText(candidate, cursorPosition)
        return candidate
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return super.onKeyUp(keyCode, event)
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int,
                                   newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        Log.d(LOG_TAG, "Cursor pos: $newSelEnd")
        cursorPosition = newSelEnd
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
    }

    override fun onCreateCandidatesView(): View {
        val candidatesView =
            CandidateView(applicationContext)
        candidatesView.setSuggestions(listOf("Ball", "Call", "Mall", "Tall", "Fall", "Pall", "Gall"))
        setCandidatesViewShown(true)
        return candidatesView
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        inputConnection = currentInputConnection

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

        val inputType =
            if (info != null)
                info.inputType and InputType.TYPE_MASK_CLASS
            else InputType.TYPE_CLASS_TEXT

        if (this.t9Trie.root == null) {
            Node.setSupportedChars("123456789")
            scope.launch {
                initT9Trie()
            }
        }

        val mode = when (inputType) {
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_DATETIME,
            InputType.TYPE_CLASS_PHONE -> enableInputMode(K9InputType.NUMBER)
            else -> enableInputMode(K9InputType.WORD)
        }
        updateStatusIcon(mode.status)
    }

    override fun onFinishInput() {
        super.onFinishInput()
        hideStatusIcon()
        mode = null
        cursorPosition = 0
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

    private fun switchToNextInputMode() {
        enableInputMode(
            K9InputType.values()[
                if (currentMode == K9InputType.values().size - 1)
                    0
                else
                    currentMode + 1
            ]
        )
    }

    private fun enableInputMode(type: K9InputType): InputMode {
        currentMode = type.idx
        val mode = when (type) {
            K9InputType.NUMBER -> NumberInputMode(keypad)
            K9InputType.WORD -> WordInputMode(keypad)
        }
        this.mode = mode
        updateStatusIcon(mode.status)
        return mode
    }

    private fun updateStatusIcon(status: Status) {
        if (status != modeStatus) {
            modeStatus = status
            showStatusIcon(
                when (status) {
                    Status.WORD_CAP -> R.drawable.ime_en_lang_single
                    Status.NUM -> R.drawable.ime_number
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
            val words = wordDao.findCandidates(char.toString(), 50)
            Log.d(LOG_TAG, "Found '${words.count()}'")
            for (word in words) {
                Log.d(LOG_TAG, "Trie ${word.code} => ${word.word}")
                t9Trie.add(word.code, word.word)
            }
        }
    }

    private fun preloadTrie(key: String, minKeyLength: Int = 0, retryCandidates: Boolean = false) {
        preloadJob?.cancel()
        preloadJob = scope.launch {
            doPreloadTrie(key, minKeyLength, retryCandidates)
        }
    }

    private suspend fun doPreloadTrie(key: String, minKeyLength: Int = 0, retryCandidates: Boolean = false) {
        if (minKeyLength > 0 && key.length < minKeyLength) {
            return
        }
        val words = wordDao.findCandidates(key, 200)
        for (word in words) {
            Log.d(LOG_TAG, "Preload trie ${word.code} => ${word.word}")
            t9Trie.add(word.code, word.word)
        }
        if (retryCandidates) {
            resolveCodeWord(key, 1, true)
        }
        t9Trie.prune(key, depth = 3)
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
                var arrayListIndex = 0
                chunk.forEach { line ->
                    val parts = line.split("\\s".toRegex())
                    try {
                        wordBatch.add(
                            arrayListIndex,
                            getWord(
                                word = parts[0],
                                frequency = if (parts.size > 1) parts[1].toInt() else 1
                            )
                        )
                        arrayListIndex++
                    }
                    catch (ex: MissingLetterCode) {
                        Log.w(LOG_TAG, "Problem adding ${parts[0]}: " + ex.message)
                    }
                }
                runBlocking {
                    wordDao.insert(*wordBatch.toTypedArray())
                }
            }
        }
    }
}