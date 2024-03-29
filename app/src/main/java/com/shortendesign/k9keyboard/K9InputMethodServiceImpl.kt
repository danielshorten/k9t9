package com.shortendesign.k9keyboard

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Toast
import com.shortendesign.k9keyboard.dao.SettingDao
import com.shortendesign.k9keyboard.dao.WordDao
import com.shortendesign.k9keyboard.db.AppDatabase
import com.shortendesign.k9keyboard.entity.Setting
import com.shortendesign.k9keyboard.entity.Word
import com.shortendesign.k9keyboard.inputmode.*
import com.shortendesign.k9keyboard.trie.Node
import com.shortendesign.k9keyboard.trie.T9Trie
import com.shortendesign.k9keyboard.util.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.Math.abs
import java.util.*
import java.util.concurrent.ArrayBlockingQueue


class K9InputMethodServiceImpl : InputMethodService(), K9InputMethodService {
    private val LOG_TAG: String = "K9Input"

    // Current input mode (e.g. Word, letter, number)
    private var mode: InputMode? = null
    // Hold on to references to modes while we're still using the input method
    private var wordMode: InputMode? = null
    private var letterMode: InputMode? = null
    private var numberMode: InputMode? = null
    // Mode enum value for cycling through modes
    private var currentMode = K9InputType.WORD.idx
    // Also keep track of the current mode for entering text/words
    private var currentTextMode = K9InputType.WORD
    // Current status for the input mode (e.g. capitalized word, all-caps letters)
    private var modeStatus: Status? = null
    // Keypad class to handle key/character mapping
    private lateinit var keypad: Keypad
    // For handling basic key->command mapping
    private lateinit var keyCommandResolver: KeyCommandResolver

    private lateinit var db: AppDatabase
    private lateinit var wordDao: WordDao
    private lateinit var settingDao: SettingDao
    private var customProperties: Properties? = null
    private var areWordsInitialized = false
    private var isTrieInitialized = false

    // Job/scope for coroutines
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    // current trie preloading job
    private var preloadJob: Job? = null
    // current delayed commit job
    private var delayedCommitJob: Job? = null
    // Keep track of recently preloaded keys to reduce unnecessary loading
    private val preloadsToCache = 3
    private val lastNPreloads = ArrayBlockingQueue<String>(preloadsToCache)
    private var retryCodeWordAfterPreload: String? = null

    // Keep track of if we're composing - we use this to help know when to commit a finished word
    private var isComposing = false
    private var lastComposingText: String? = null

    private var inputConnection: InputConnection? = null
    private var cursorPosition: Int = 0

    // Trie for loading and searching for words by key
    private val t9Trie = T9Trie()

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getInstance(this)
        wordDao = db.getWordDao()
        settingDao = db.getSettingDao()
        customProperties = loadCustomProperties()
        val (keypad, keyCommandResolver) = loadKeyPadAndCommandResolver(customProperties)
        this.keypad = keypad
        this.keyCommandResolver = keyCommandResolver
        initializeWordsFirstTime()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(LOG_TAG, "KEYCODE: ${keyCode}")
        val key = keypad.getKey(keyCode)
        return handleKeyCode(key, event, false)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        val key = keypad.getKey(keyCode)
        return handleKeyCode(key, event, true)
    }

    private fun handleKeyCode(key: Key?, event: KeyEvent?, long: Boolean = false): Boolean {
        var consumed = false
        val mode = this.mode
        if (mode != null) {
            val result = when (key) {
                null -> null
                else -> mode.getKeyCodeResult(
                    key,
                    event?.repeatCount ?: 0,
                    long,
                    inputConnection?.getTextBeforeCursor(25,0),
                    inputConnection?.getTextAfterCursor(25, 0)
                )
            }
            //Log.d(LOG_TAG, "Result: $result")
            //Log.d(LOG_TAG, "Result codeWord: ${result?.codeWord}")
            consumed = result?.consumed ?: false
            updateStatusIcon(mode.status)

            if (result != null) {
                //Log.d(LOG_TAG, "CODEWORD: ${result.codeWord}")
                if (!result.consumed && key != null) {
                    val command = keyCommandResolver.getCommand(key, long)
                    consumed = if (command != null ) handleUnconsumedCommand(command) else false
                }
                handleInputModeResult(result)
            }
        }
        if (consumed && !long) {
            event?.startTracking()
        }
        return consumed
    }

    private fun handleUnconsumedCommand(command: Command): Boolean {
        // Delete or back
        if (command == Command.DELETE) {
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
        else if (command == Command.NEXT_MODE) {
            switchToNextInputMode()
            return true
        }
        return false
    }

    private fun handleInputModeResult(result: KeyPressResult) {
        // If we get back a code word, we'll need to resolve it
        when {
            // Don't handle any key presses for WORD input type if words aren't yet initialized
            currentMode == K9InputType.WORD.idx && (!areWordsInitialized || !isTrieInitialized) -> {
                return
            }
            // Handle the case where the input mode returns a code word to be resolved.
            result.codeWord != null -> {
                if (result.recomposing) {
                    handleRecomposing(result)
                }
                // Attempt to resolve a word candidate from the mode's sequence of key presses
                resolveCodeWord(
                    result.codeWord,
                    recomposingResult = if (result.recomposing) result else null
                )
            }
            // Handle the case where the input mode returns a literal word to be added to the input
            result.word != null -> {
                // TODO: Support a delay for committing the word
                if (result.commitDelay > 0) {
                    setComposingText(result.word, result.commitDelay)
                }
                else {
                    // Support for undoing some previous characters if a long press ends up adding a
                    // different character
                    if (result.cursorOffset < 0) {
                        inputConnection?.deleteSurroundingText(abs(result.cursorOffset), 0)
                    }
                    finishComposing()
                    if (result.cursorOffset > 0) {
                        setComposingText(result.word, (result.cursorOffset.toLong()))
                    }
                    else {
                        inputConnection?.commitText(result.word, 1)
                    }
                }
            }
            // If we get nothing back, it's some unhandled action and we'll assume we should be
            // committing the current composition.
            else -> {
                // In some cases, after we finish composing, we may want to move the cursor, e.g.
                // to the beginning of the word.
                finishComposing(result.cursorOffset)
            }
        }
    }

    private fun handleRecomposing(result: KeyPressResult) {
        val newSel = cursorPosition + result.cursorOffset
        // Calculate the new cursor position base on cursor offset
        inputConnection?.setSelection(newSel, newSel)
        // If we moved the cursor right, we'll delete the text to the left.
        val deleteLeft = result.cursorOffset > 0
        inputConnection?.deleteSurroundingText(
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
        inputConnection?.setComposingText(composingText, 1)
        if (commitDelay != null) {
            delayedCommitJob?.cancel()
            delayedCommitJob = scope.launch {
                delay(commitDelay)
                finishComposing()
                mode?.resolveCodeWord("", listOf(), resetToWord = composingText)
            }
        }
    }

    private fun finishComposing(cursorOffset: Int = 0) {
        if (isComposing) {
            inputConnection?.finishComposingText()
            if (cursorOffset != 0) {
                val selection = cursorPosition + cursorOffset
                inputConnection?.setSelection(selection, selection)
            }
            isComposing = false
            lastComposingText = null
        }
    }

    fun resolveCodeWord(codeWord: String, final: Boolean = false,
                        recomposingResult: KeyPressResult? = null) {
        val candidates = t9Trie.getCandidates(codeWord, maxLength = codeWord.length)
//        Log.d(LOG_TAG, "CANDIDATES for $codeWord: $candidates")
        // Special case: if the new codeword is one character shorter than the last-seen composing
        // text, assume we're deleting
        val composingText = lastComposingText
        val deleteResult = if (composingText?.length?.minus(codeWord.length) == 1)
            composingText.substring(0, composingText.length - 1)
            else null
        val resetToWord = when {
            // If we're beginning to recompose, reset to the initial word
            recomposingResult != null -> recomposingResult.word
            // If we're deleting, force the last text with one character truncated
            deleteResult != null -> deleteResult
            else -> null
        }
        val candidate = mode!!.resolveCodeWord(codeWord, candidates, final, resetToWord)
//        Log.d(LOG_TAG, "CANDIDATE: $candidate")

        // Initial pass
        if (!final) {
            // Ensure our T9 trie is primed with candidates for the current code word prefix
            // (it gets cleared periodically to free up memory, so needs to be reprimed).
            // This will also retry resolving the candidate if the above attempt returned null.
            preloadTrie(
                codeWord, 2,
                retryCandidates = candidate == null,
                recomposingResult = recomposingResult
            )
        }
        if (deleteResult != null) {
            setComposingText(deleteResult)
        }
        // If we can resolve a candidate, set the composing text
        // (except if we're beginning to recompose)
        else if (candidate != null && recomposingResult == null) {
            setComposingText(candidate)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return super.onKeyUp(keyCode, event)
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int,
                                   newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        cursorPosition = newSelStart
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
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
            else -> enableInputMode(currentTextMode)
        }
        updateStatusIcon(mode.status)
    }

    override fun onFinishInput() {
        super.onFinishInput()
        hideStatusIcon()
        mode = null
        numberMode = null
        letterMode = null
        wordMode = null
        cursorPosition = 0
        modeStatus = null
        t9Trie.prune("a", depth = 3)
    }

    override suspend fun findCandidates(word: String): List<Word> {
        return wordDao.findCandidates(word)
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
        val nextInputMode = K9InputType.values()[
            if (currentMode == K9InputType.values().size - 1)
                0
            else
                currentMode + 1
        ]
        enableInputMode(nextInputMode)
        alertNewMode(nextInputMode)
    }

    private fun enableInputMode(type: K9InputType): InputMode {
        currentMode = type.idx
        val mode = when(type) {
            K9InputType.ALPHA -> letterMode ?: LetterInputMode(keypad)
            K9InputType.NUMBER -> numberMode ?: NumberInputMode(keypad)
            K9InputType.WORD -> wordMode ?: WordInputMode(keypad)
        }
        // Cache the mode for use later
        when(type) {
            K9InputType.ALPHA -> {currentTextMode = K9InputType.ALPHA; letterMode = mode}
            K9InputType.NUMBER -> {numberMode = mode}
            K9InputType.WORD -> {currentTextMode = K9InputType.WORD; wordMode = mode}
        }
        mode.load(keyCommandResolver, customProperties, inputConnection?.getTextBeforeCursor(5, 0))
        this.mode = mode
        updateStatusIcon(mode.status)
        return mode
    }

    private fun updateStatusIcon(status: Status) {
        if (status != modeStatus) {
            modeStatus = status
            showStatusIcon(
                when (status) {
                    Status.WORD -> R.drawable.mode_en
                    Status.WORD_CAP -> R.drawable.mode_en_cap
                    Status.WORD_UPPER -> R.drawable.mode_en_upper
                    Status.ALPHA -> R.drawable.mode_la_letter
                    Status.ALPHA_CAP -> R.drawable.mode_la_letter_cap
                    Status.ALPHA_UPPER -> R.drawable.mode_la_letter_upper
                    Status.NUM -> R.drawable.mode_number
                    else -> 0
                }
            )
        }
    }

    private fun alertNewMode(modeType: K9InputType) {
        val duration = Toast.LENGTH_SHORT
        val text = when (modeType) {
            K9InputType.WORD -> "En"
            K9InputType.ALPHA -> "Abc"
            K9InputType.NUMBER -> "123"
        }
        val toast = Toast.makeText(applicationContext, text, duration)
        toast.show()
    }

    private suspend fun initT9Trie() {
        Node.setSupportedChars("123456789")
        t9Trie.clear()
        for (char in "123456789") {
            doPreloadTrie(char.toString())
        }
        isTrieInitialized = true
    }

    private fun preloadTrie(key: String, minKeyLength: Int = 0, retryCandidates: Boolean = false,
                            recomposingResult: KeyPressResult? = null) {
        // Only start a preload job if there isn't one active
        if (preloadJob == null || !preloadJob!!.isActive) {
            preloadJob = scope.launch {
                doPreloadTrie(key, minKeyLength, 200, retryCandidates = retryCandidates,
                recomposingResult=recomposingResult)
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
        retryCandidates: Boolean = false,
        recomposingResult: KeyPressResult? = null
    ) {
        var skipLoad = false
        // Only preload if the key length meets the minimum threshold
        if (minKeyLength > 0 && key.length < minKeyLength) {
            skipLoad = true
        }
        // If we've preloaded for this key recently, skip
        if (lastNPreloads.contains(key)) {
            skipLoad = true
        }
        else {
            if (lastNPreloads.size == preloadsToCache) {
                lastNPreloads.remove()
            }
            lastNPreloads.add(key)
        }
        if (!skipLoad) {
            val words = wordDao.findCandidates(key, numCandidates)
            for (word in words) {
                //Log.d(LOG_TAG, "Preload trie ${word.code} => ${word.word}: ${word.frequency}")
                t9Trie.add(word.code, word.word, word.frequency)
            }
        }
        if (retryCandidates || retryCodeWordAfterPreload != null) {
//            Log.d(LOG_TAG, "Retrying resolveCodeWord")
            resolveCodeWord(retryCodeWordAfterPreload?: key, true, recomposingResult)
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
                            when (parts.size) {
                                // Emoji support
                                3 -> Word(
                                    word = parts[0],
                                    code = parts[2],
                                    length = 1,
                                    frequency = parts[1].toInt(),
                                    "en_US")
                                // Regular dictionary words
                                else -> getWord(
                                    word = parts[0],
                                    frequency = if (parts.size > 1) parts[1].toInt() else 0)
                            }
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

    private fun loadCustomProperties(): Properties? {
        var properties: Properties? = null
        try {
            val dir = applicationContext.getExternalFilesDir(null)
            if (dir?.exists() == false) {
                dir.mkdir()
            }
            FileInputStream(File(dir, "k9t9.properties")).use { input ->
                val props =  Properties()
                props.load(input)
                Log.d(LOG_TAG, "Loaded settings from k9t9.properties")
                properties = props
            }
        } catch (ex: FileNotFoundException) {
            Log.d(LOG_TAG, "No custom settings file found. Using default settings.")
        }
        catch (ex: IOException) {
            ex.printStackTrace()
        }
        return properties
    }

    private fun loadKeyPadAndCommandResolver(properties: Properties?): Pair<Keypad, KeyCommandResolver> {
        return if (properties != null) {
            Pair(
                Keypad(KeyCodeMapping.fromProperties(properties), LetterLayout.enUS),
                KeyCommandResolver.fromProperties(properties)
            )
        } else {
            Pair(
                Keypad(KeyCodeMapping(KeyCodeMapping.basic), LetterLayout.enUS),
                KeyCommandResolver.getBasic()
            )
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