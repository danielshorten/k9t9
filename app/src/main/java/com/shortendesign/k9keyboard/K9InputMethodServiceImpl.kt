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
import java.util.concurrent.ArrayBlockingQueue


class K9InputMethodServiceImpl : InputMethodService(), K9InputMethodService {
    private val LOG_TAG: String = "K9Input"

    // Current input mode (e.g. Word, letter, number)
    private var mode: InputMode? = null
    // Mode enum value for cycling through modes
    private var currentMode = K9InputType.WORD.idx
    // Current status for the input mode (e.g. capitalized word, all-caps letters)
    private var modeStatus: Status? = null
    // Keypad class to handle key/character mapping
    private val keypad = Keypad(KeyCodeMapping.basic, LetterLayout.enUS)

    private lateinit var db: AppDatabase
    private lateinit var wordDao: WordDao
    private lateinit var settingDao: SettingDao
    private var areWordsInitialized = false

    // Job/scope for coroutines
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    // current trie preloading job
    private var preloadJob: Job? = null
    // Keep track of recently preloaded keys to reduce unnecessary loading
    private val preloadsToCache = 3
    private val lastNPreloads = ArrayBlockingQueue<String>(preloadsToCache)
    private var retryCodeWordAfterPreload: String? = null

    // Keep track of if we're composing - we use this to help know when to commit a finished word
    private var isComposing = false

    private var inputConnection: InputConnection? = null
    private var cursorPosition: Int = 0
    // Whether we should end composing on the right side of the word.  May be false if we're
    // moving the selection backwards.
    private var rightSideOfWord = true

    // Trie for loading and searching for words by key
    private val t9Trie = T9Trie()

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getInstance(this)
        wordDao = db.getWordDao()
        settingDao = db.getSettingDao()
        initializeWordsFirstTime()
        //writeToFile()
    }

//    fun writeToFile() {
//        val dir = applicationContext.getExternalFilesDir(null)
//        if (dir?.exists() == false) {
//            dir.mkdir()
//        }
//
//        try {
//            val gpxfile = File(dir, "k9test.txt")
//            val writer = FileWriter(gpxfile)
//            writer.append("My test content.")
//            writer.flush()
//            writer.close()
//            Log.d(LOG_TAG, "Wrote to file $gpxfile")
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.i(LOG_TAG, "keyCode: $keyCode")
        var consumed = false
        val mode = this.mode
        if (mode != null) {
            val result = mode.getKeyCodeResult(keyCode)
            Log.d(LOG_TAG, "Result: $result")
            //Log.d(LOG_TAG, "Result codeWord: ${result?.codeWord}")
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
            // If the input mode isn't handling the delete, we've definitely finished composing
            finishComposing()
            // Since we're not composing, we should make sure there's no job trying to resolve some
            // candidate
            preloadJob?.cancel()
            // If we aren't at the very beginning of the input, delete one character
            return if (cursorPosition > 0) {
                inputConnection?.deleteSurroundingText(1, 0)
                true
            } else
                // Otherwise, don't handle this key press so that it bubbles up as "back"
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
            // Don't handle any key presses for WORD input type if words aren't yet initialized
            currentMode == K9InputType.WORD.idx && !areWordsInitialized -> {
                return
            }
            // Handle the case where the input mode returns a code word to be resolved.
            result.codeWord != null -> {
                isComposing = true
                // Attempt to resolve a word candidate from the mode's sequence of key presses
                val candidate = resolveCodeWord(result.codeWord, 1)
                // Ensure our T9 trie is primed with candidates for the current code word prefix
                // (it gets cleared periodically to free up memory, so needs to be reprimed).
                // This will also retry resolving the candidate if the above attempt returned null.
                preloadTrie(result.codeWord, 2, retryCandidates = candidate == null)
            }
            // Handle the case where the input mode returns a literal word to be added to the input
            result.word != null -> {
                // TODO: Support a delay for committing the word
                finishComposing()
                inputConnection?.commitText(result.word, 1)
            }
            // If we get nothing back, it's some unhandled action and we'll assume we should be
            // committing the current composition.
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
        val candidates = t9Trie.getCandidates(codeWord, 7, codeWord.length)
        //Log.d(LOG_TAG, "CANDIDATES for $codeWord: $candidates")
        val candidate = mode!!.resolveCodeWord(codeWord, candidates, final)
        //Log.d(LOG_TAG, "Composing Text: $candidate - $cursorPosition")
        if (candidate != null) inputConnection?.setComposingText(candidate, cursorPosition)
        return candidate
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return super.onKeyUp(keyCode, event)
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int,
                                   newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        cursorPosition = newSelEnd
        val result = mode?.recompose(
            inputConnection?.getTextBeforeCursor(25, 0),
            inputConnection?.getTextAfterCursor(25, 0)
        )
        if (result != null) {
            if (oldSelStart < newSelStart) {
                Log.d(LOG_TAG, "Recomposing to the right.")
                inputConnection?.deleteSurroundingText(1, result.word!!.length - 1)
            }
            else {
                Log.d(LOG_TAG, "Recomposing to the left.")
                inputConnection?.deleteSurroundingText(result.word!!.length - 1, 1)
            }
            inputConnection?.setComposingText(result.word, 0)
            isComposing = true
            preloadTrie(result.codeWord!!, 2)
        }
        else {
            Log.d(LOG_TAG, "Ending recompose.")
            finishComposing()
            cursorPosition = oldSelStart - result.word!!.length
        }
        super.onUpdateSelection(oldSelStart, oldSelEnd, cursorPosition, cursorPosition, candidatesStart, candidatesEnd)
    }

    override fun onCreateCandidatesView(): View {
        return CandidateView(applicationContext)
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        inputConnection = currentInputConnection
        cursorPosition = info?.initialSelEnd ?: 0

        val inputType =
            if (info != null)
                info.inputType and InputType.TYPE_MASK_CLASS
            else InputType.TYPE_CLASS_TEXT

        if (this.t9Trie.root == null && areWordsInitialized) {
            preloadJob?.cancel()
            preloadJob = scope.launch {
                initT9Trie()
            }
        }

        val mode = when (inputType) {
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_DATETIME,
            InputType.TYPE_CLASS_PHONE -> enableInputMode(K9InputType.NUMBER)
            0 -> return
            else -> enableInputMode(K9InputType.WORD)
        }
        updateStatusIcon(mode.status)
    }

    override fun onFinishInput() {
        super.onFinishInput()
        hideStatusIcon()
        mode = null
        cursorPosition = 0
        modeStatus = null
        t9Trie.prune("a", depth = 3)
    }

    override suspend fun findCandidates(word: String): List<Word> {
        return wordDao.findCandidates(word)
    }

//    override fun setComposingText(text: String, cursorPosition: Int) {
//        supe
//        inputConnection?.setComposingText(text, cursorPosition)
//    }

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
                    Status.WORD -> R.drawable.ime_en_lang_lower
                    Status.WORD_CAP -> R.drawable.ime_en_lang_single
                    Status.WORD_UPPER -> R.drawable.ime_en_lang_upper
                    Status.NUM -> R.drawable.ime_number
                    else -> 0
                }
            )
        }
    }

    private suspend fun initT9Trie() {
        Node.setSupportedChars("123456789")
        t9Trie.clear()
        for (char in "123456789") {
            doPreloadTrie(char.toString())
        }
    }

    private fun preloadTrie(key: String, minKeyLength: Int = 0, retryCandidates: Boolean = false) {
        // Only start a preload job if there isn't one active
        if (preloadJob == null || !preloadJob!!.isActive) {
            preloadJob = scope.launch {
                doPreloadTrie(key, minKeyLength, 200, retryCandidates = retryCandidates)
            }
        } else {
            // If there's a job active but we wanted to retry, store that info
            retryCodeWordAfterPreload = key
        }
    }

    private suspend fun doPreloadTrie(
        key: String,
        minKeyLength: Int = 0,
        numCandidates: Int = 200,
        retryCandidates: Boolean = false
    ) {
        // Only preload if the key length meets the minimum threshold
        if (minKeyLength > 0 && key.length < minKeyLength) {
            return
        }
        // If we've preloaded for this key recently, skip
        if (lastNPreloads.contains(key)) {
            return
        }
        else {
            if (lastNPreloads.size == preloadsToCache) {
                lastNPreloads.remove()
            }
            lastNPreloads.add(key)
        }
        val words = wordDao.findCandidates(key, numCandidates)
        for (word in words) {
            //Log.d(LOG_TAG, "Preload trie ${word.code} => ${word.word}: ${word.frequency}")
            t9Trie.add(word.code, word.word, word.frequency)
        }
        if (retryCandidates || retryCodeWordAfterPreload != null) {
            resolveCodeWord(retryCodeWordAfterPreload?: key, 1, true)
        }
        retryCodeWordAfterPreload = null
        //t9Trie.prune(key, depth = 3)
    }

    /**
     * On the first time loading the service, we want to initialize the SQLite database with words
     * from our word list
     */
    private fun initializeWordsFirstTime() {
        // TODO: Enumerate the settings keys
        scope.launch {
            val setting = settingDao.getByKey("initialized")
            areWordsInitialized = if (setting == null) {
                Log.d(LOG_TAG,"Initializing word database...")
                initializeWords()
                Setting.set("initialized", "t", settingDao)
                true
            } else {
                Log.d(LOG_TAG, "Word database already initialized")
                true
            }
        }
    }

    private fun initializeWords() {
        val file_name = "t9_words.txt"
        val batchSize = 1000
        val wordBatch = ArrayList<Word>(batchSize)
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
                                frequency = if (parts.size > 1) parts[1].toInt() else 0
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

    /***************************** HELPERS ****************************************/

    private fun logInputType(info: EditorInfo) {
        when (info.inputType and InputType.TYPE_MASK_CLASS) {
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
    }
}