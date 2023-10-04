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


class K9InputMethodService : InputMethodService() {
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
    private var areWordsInitialized = false
    private lateinit var wordDao: WordDao
    private lateinit var settingDao: SettingDao
    private var customProperties: Properties? = null

    // Job/scope for coroutines
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var inputProxy: InputProxy? = null


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
            consumed = when (key) {
                null -> false
                else -> mode.getKeyCodeResult(
                    key,
                    event?.repeatCount ?: 0,
                    long,
                )
            }
            //Log.d(LOG_TAG, "Result: $result")
            //Log.d(LOG_TAG, "Result codeWord: ${result?.codeWord}")
            updateStatusIcon(mode.status)

            if (!consumed && key != null) {
                val command = keyCommandResolver.getCommand(key, long)
                consumed = if (command != null ) handleUnconsumedCommand(command) else false
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
            // FIXME: I guess the input mode should handle composing
            //finishComposing()
            // Since we're not composing, we should make sure there's no job trying to resolve some
            // candidate
            // FIXME: Where does this belong?
            //preloadJob?.cancel()
            // If we aren't at the very beginning of the input, delete one character
            // TODO: return inputProxy.deleteOneCharacter()
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
            currentMode == K9InputType.WORD.idx && !areWordsInitialized -> {
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

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return super.onKeyUp(keyCode, event)
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int,
                                   newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        inputProxy?.setCursorPosition(newSelStart)
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
    }

    override fun onCreateCandidatesView(): View {
        return CandidateView(applicationContext)
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        inputProxy = InputProxy(
            currentInputConnection,
            this.scope,
            info?.initialSelEnd ?: 0)

        val inputType =
            if (info != null)
                info.inputType and InputType.TYPE_MASK_CLASS
            else InputType.TYPE_CLASS_TEXT

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
        modeStatus = null
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
        var type = type
        currentMode = type.idx
        if (type == K9InputType.WORD && !areWordsInitialized) {
            type = K9InputType.ALPHA
        }
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
        mode.load(
            this.db,
            this.scope,
            this.inputProxy!!,
            keyCommandResolver,
            customProperties
        )
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