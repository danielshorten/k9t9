package com.shortendesign.k9keyboard

import android.inputmethodservice.InputMethodService
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
import com.shortendesign.k9keyboard.util.KeyCodeMapping
import com.shortendesign.k9keyboard.util.LetterLayout
import com.shortendesign.k9keyboard.util.Status
import kotlinx.coroutines.*


class K9InputMethodServiceImpl() : InputMethodService(), K9InputMethodService {
    private val LOG_TAG: String = "K9Input"
    private var mode: WordInputMode? = null
    private lateinit var db: AppDatabase
    private lateinit var wordDao: WordDao
    private lateinit var settingDao: SettingDao
    private val keypad = Keypad(KeyCodeMapping.basic, LetterLayout.enUS)
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var inputConnection: InputConnection? = null
    private var cursorPosition: Int = 0
    private var modeStatus = Status.WORD_CAP

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getInstance(this)
        wordDao = db.getWordDao()
        settingDao = db.getSettingDao()
        initializeWordsFirstTime()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        inputConnection = currentInputConnection
        showStatusIcon(R.drawable.ime_en_lang_single)
        cursorPosition = info?.initialSelEnd ?: 0
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.i(LOG_TAG, "keyCode: $keyCode")
        val mode = this.mode ?: return false
        val result = mode.getKeyCodeResult(keyCode)
        updateStatusIcon(mode.status)

        if (result != null) {
            if (result.consumed) {
                if (result.codeWord != null) {
                    scope.launch {
                        updateCandidates(result.codeWord, 1)
                    }
                }
            }
            else {
                // Delete or back
                if (event?.keyCode == KeyEvent.KEYCODE_BACK) {
                    return if (cursorPosition > 0)
                        inputConnection?.deleteSurroundingText(1, 0) ?: false
                    else
                        false
                }
            }
            // Committed word from the mode
            if (result.word != null) {
                inputConnection?.commitText(result.word, 2)
            }
            return result.consumed
        }
        else {
            return false
        }
    }

    suspend fun updateCandidates(codeWord: String, cursorPosition: Int) {
        val candidates = wordDao.findCandidates(codeWord)
        //Log.d(LOG_TAG, "CANDIDATES: " + candidates.joinToString())
        inputConnection?.setComposingText(mode!!.getComposingText(candidates), cursorPosition)
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
        val file_name = "word_freq.txt"
        val batchSize = 500
        val wordBatch = ArrayList<Word>(batchSize)
        var freq: Int
        application.assets.open(file_name).bufferedReader().useLines { lines ->
            for (chunk in lines.chunked(batchSize)) {
                wordBatch.clear()
                chunk.forEachIndexed { index, line ->
                    val parts = line.split(" ")
                    freq = parts[1].toFloat().toInt()
                    wordBatch.add(
                        index,
                        getWord(word = parts[2], frequency = freq)
                    )
                }
                runBlocking {
                    wordDao.insert(*wordBatch.toTypedArray())
                }
            }
        }
    }
}