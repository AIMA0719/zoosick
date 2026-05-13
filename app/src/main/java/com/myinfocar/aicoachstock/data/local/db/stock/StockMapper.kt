package com.myinfocar.aicoachstock.data.local.db.stock

import com.myinfocar.aicoachstock.domain.model.Currency
import com.myinfocar.aicoachstock.domain.model.Exchange
import com.myinfocar.aicoachstock.domain.model.Stock

fun StockEntity.toDomain(): Stock = Stock(
    ticker = ticker,
    nameKo = nameKo,
    nameEn = nameEn,
    exchange = Exchange.valueOf(exchange),
    sector = sector,
    currency = Currency.valueOf(currency),
)

fun Stock.toEntity(): StockEntity = StockEntity(
    ticker = ticker,
    nameKo = nameKo,
    nameEn = nameEn,
    exchange = exchange.name,
    sector = sector,
    currency = currency.name,
)
