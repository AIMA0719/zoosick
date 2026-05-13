package com.myinfocar.aicoachstock.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.myinfocar.aicoachstock.data.local.db.principle.PrincipleDao
import com.myinfocar.aicoachstock.data.local.db.principle.PrincipleEntity
import com.myinfocar.aicoachstock.data.local.db.reflection.TradeReflectionDao
import com.myinfocar.aicoachstock.data.local.db.reflection.TradeReflectionEntity
import com.myinfocar.aicoachstock.data.local.db.stock.StockDao
import com.myinfocar.aicoachstock.data.local.db.stock.StockEntity
import com.myinfocar.aicoachstock.data.local.db.trade.TradeDao
import com.myinfocar.aicoachstock.data.local.db.trade.TradeEntity
import com.myinfocar.aicoachstock.data.local.db.watchlist.WatchListDao
import com.myinfocar.aicoachstock.data.local.db.watchlist.WatchListEntity

/**
 * SQLCipher 암호화된 Room DB.
 *
 * version은 스키마 변경 시마다 +1 + Migration 작성.
 * Phase 1 초기는 fallbackToDestructiveMigration. 본 출시 전 Migration 작성하며 제거.
 */
@Database(
    entities = [
        PrincipleEntity::class,
        TradeEntity::class,
        StockEntity::class,
        WatchListEntity::class,
        TradeReflectionEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun principleDao(): PrincipleDao
    abstract fun tradeDao(): TradeDao
    abstract fun stockDao(): StockDao
    abstract fun watchListDao(): WatchListDao
    abstract fun tradeReflectionDao(): TradeReflectionDao
}
