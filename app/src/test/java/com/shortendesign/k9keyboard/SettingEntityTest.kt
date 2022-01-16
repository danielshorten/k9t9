package com.shortendesign.k9keyboard

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.shortendesign.k9keyboard.dao.SettingDao
import com.shortendesign.k9keyboard.db.AppDatabase
import com.shortendesign.k9keyboard.entity.Setting
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class SettingEntityTest {
    private lateinit var settingDao: SettingDao
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .fallbackToDestructiveMigration().allowMainThreadQueries().build()
        settingDao = db.getSettingDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun setSetting() {
        runBlocking {
            var setting: Setting? = Setting("foo", "bar")
            settingDao.insert(setting!!)
            setting.value = "baz"
            settingDao.insert(setting)

            setting = settingDao.getByKey("foo")
            Assert.assertNotNull(setting!!)
            Assert.assertEquals("foo", setting.key)
            Assert.assertEquals("baz", setting.value)
        }
    }
}
