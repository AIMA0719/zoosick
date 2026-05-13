package com.myinfocar.aicoachstock.poc

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myinfocar.aicoachstock.data.local.db.AppDatabase
import com.myinfocar.aicoachstock.data.local.db.principle.PrincipleEntity
import com.myinfocar.aicoachstock.data.local.secure.SecurePassphraseProvider
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * PoC #4 — SQLCipher + Keystore-backed passphrase 왕복 검증.
 *
 * 검증 시나리오:
 *  1) SecurePassphraseProvider는 같은 인스턴스에서 호출해도 동일한 32바이트 키 반환.
 *  2) 새 인스턴스에서 호출(앱 재시작 시뮬레이션)해도 EncryptedSP 통해 동일 키 복원.
 *  3) SQLCipher Room DB에 쓰고, 닫고, 새 passphrase 인스턴스로 다시 열어 동일 row 읽기.
 *
 * 실기기 또는 에뮬레이터에서 `./gradlew :app:connectedDebugAndroidTest` 로 실행.
 */
@RunWith(AndroidJUnit4::class)
class SqlCipherPassphrasePocTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // 이전 테스트 잔재 제거: EncryptedSP 안의 passphrase 항목 + DB 파일.
        context.getSharedPreferences(SECURE_PREFS_FILE, Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.deleteDatabase(TEST_DB_NAME)
        System.loadLibrary("sqlcipher")
    }

    @Test
    fun passphrase_isStable_withinSameInstance() {
        val provider = SecurePassphraseProvider(context)
        val first = provider.getOrCreatePassphrase()
        val second = provider.getOrCreatePassphrase()

        assertEquals("passphrase는 32바이트여야 함", 32, first.size)
        assertArrayEquals("같은 인스턴스 재호출 시 동일해야 함", first, second)
    }

    @Test
    fun passphrase_isPersisted_acrossInstances() {
        val first = SecurePassphraseProvider(context).getOrCreatePassphrase()
        // 앱 재시작 시뮬레이션: 새 인스턴스가 EncryptedSP에서 같은 값을 복원해야 한다.
        val second = SecurePassphraseProvider(context).getOrCreatePassphrase()
        assertArrayEquals("재시작 시뮬레이션에서도 passphrase가 동일해야 함", first, second)
    }

    @Test
    fun sqlcipher_canWriteAndReadAfterReopen() = runBlocking {
        val passphrase = SecurePassphraseProvider(context).getOrCreatePassphrase()

        // 첫 오픈 — write. SupportOpenHelperFactory가 키를 소비하므로 copyOf로 방어.
        val db1 = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB_NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase.copyOf()))
            .fallbackToDestructiveMigration()
            .build()
        val now = Instant.now().toEpochMilli()
        db1.principleDao().upsert(
            PrincipleEntity(
                id = "poc-1",
                category = "DISCIPLINE",
                ruleText = "PoC 원칙",
                weight = 5,
                isActive = true,
                orderIndex = 0,
                createdAt = now,
                updatedAt = now,
            )
        )
        db1.close()

        // 재오픈 — 새 SecurePassphraseProvider 인스턴스로 passphrase 복원 후 read.
        val passphrase2 = SecurePassphraseProvider(context).getOrCreatePassphrase()
        val db2 = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB_NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase2.copyOf()))
            .fallbackToDestructiveMigration()
            .build()
        val loaded = db2.principleDao().findById("poc-1")
        db2.close()

        assertNotNull("재시작 후 row 조회 가능해야 함", loaded)
        assertEquals("PoC 원칙", loaded?.ruleText)
    }

    private companion object {
        const val TEST_DB_NAME = "poc_sqlcipher.db"
        // SecurePassphraseProvider 내부에서 사용하는 EncryptedSharedPreferences 파일 이름.
        const val SECURE_PREFS_FILE = "aicoachstock_secure_prefs"
    }
}
