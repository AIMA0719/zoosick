package com.myinfocar.aicoachstock.data.remote.kis.stockmaster

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.nio.charset.Charset
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 한투 종목 마스터 zip 다운로드 + 압축 해제.
 *
 *  - 한투 공개 URL: https://new.real.download.dws.co.kr/common/master/{file}.zip
 *  - 인증 불필요(API 키 없이 누구나 다운로드 가능)
 *  - 인코딩: cp949 (EUC-KR) — 한글 종목명 디코딩
 *  - 각 zip 안에는 단일 mst/cod 파일만 들어있다고 가정 (첫 엔트리 사용)
 */
@Singleton
class KisStockMasterDownloader @Inject constructor(
    private val client: OkHttpClient,
) {

    /** zip URL을 받아 첫 엔트리를 cp949로 디코드한 텍스트를 반환. */
    suspend fun downloadAndUnzipCp949(zipUrl: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(zipUrl).build()
        client.newCall(request).execute().use { resp ->
            require(resp.isSuccessful) {
                "종목 마스터 다운로드 실패 code=${resp.code} url=$zipUrl"
            }
            val body = resp.body ?: error("응답 본문 없음: $zipUrl")
            ZipInputStream(body.byteStream()).use { zip ->
                zip.nextEntry ?: error("zip 안에 엔트리 없음: $zipUrl")
                val bytes = zip.readBytes()
                Timber.i("종목 마스터 다운로드 OK ${bytes.size}B url=$zipUrl")
                String(bytes, CP949)
            }
        }
    }

    private companion object {
        val CP949: Charset = Charset.forName("EUC-KR")
    }
}
