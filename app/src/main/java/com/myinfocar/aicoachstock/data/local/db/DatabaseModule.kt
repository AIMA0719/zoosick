package com.myinfocar.aicoachstock.data.local.db

import android.content.Context
import androidx.room.Room
import com.myinfocar.aicoachstock.data.local.db.alert.PriceAlertDao
import com.myinfocar.aicoachstock.data.local.db.coach.CoachMessageDao
import com.myinfocar.aicoachstock.data.local.db.coach.CoachSessionDao
import com.myinfocar.aicoachstock.data.local.db.entry.EntryChecklistDao
import com.myinfocar.aicoachstock.data.local.db.principle.PrincipleDao
import com.myinfocar.aicoachstock.data.local.db.reflection.TradeReflectionDao
import com.myinfocar.aicoachstock.data.local.db.stock.StockDao
import com.myinfocar.aicoachstock.data.local.db.trade.TradeDao
import com.myinfocar.aicoachstock.data.local.db.watchlist.WatchListDao
import com.myinfocar.aicoachstock.data.local.secure.PassphraseProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        passphraseProvider: PassphraseProvider,
    ): AppDatabase {
        // SQLCipher 네이티브 라이브러리 로드 (System.loadLibrary).
        System.loadLibrary("sqlcipher")

        val passphrase = passphraseProvider.getOrCreatePassphrase()
        val factory = SupportOpenHelperFactory(passphrase)
        return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .openHelperFactory(factory)
            // Phase 1 초기 한정. 스키마 안정화 후 Migration 작성하며 제거.
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun providePrincipleDao(db: AppDatabase): PrincipleDao = db.principleDao()

    @Provides
    fun provideTradeDao(db: AppDatabase): TradeDao = db.tradeDao()

    @Provides
    fun provideStockDao(db: AppDatabase): StockDao = db.stockDao()

    @Provides
    fun provideWatchListDao(db: AppDatabase): WatchListDao = db.watchListDao()

    @Provides
    fun provideTradeReflectionDao(db: AppDatabase): TradeReflectionDao = db.tradeReflectionDao()

    @Provides
    fun provideCoachSessionDao(db: AppDatabase): CoachSessionDao = db.coachSessionDao()

    @Provides
    fun provideCoachMessageDao(db: AppDatabase): CoachMessageDao = db.coachMessageDao()

    @Provides
    fun provideEntryChecklistDao(db: AppDatabase): EntryChecklistDao = db.entryChecklistDao()

    @Provides
    fun providePriceAlertDao(db: AppDatabase): PriceAlertDao = db.priceAlertDao()

    private const val DB_NAME = "aicoachstock.db"
}
