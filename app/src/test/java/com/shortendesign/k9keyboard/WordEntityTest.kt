package com.shortendesign.k9keyboard

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shortendesign.k9keyboard.dao.WordDao
import com.shortendesign.k9keyboard.db.AppDatabase
import com.shortendesign.k9keyboard.entity.Word
import com.shortendesign.k9keyboard.util.TestUtil
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Example local unit test, which will execute on the development machine (host).
 *x
 * See [testing documentation](http://d.android.com/tools/testing).
 */

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class WordEntityTest {
    private lateinit var wordDao: WordDao
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .fallbackToDestructiveMigration().allowMainThreadQueries().build()
        wordDao = db.getWordDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun createWord() {
        runBlocking {
            val word: Word = TestUtil.createWord("ball", "2255")
            wordDao.insert(word)
            val byCode = wordDao.findCandidates("22")
            assertTrue(byCode.contains(word))
        }
    }
}