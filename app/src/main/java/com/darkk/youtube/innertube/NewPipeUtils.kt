package com.darkk.youtube.innertube

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.CancellableCall
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import java.io.IOException
import io.ktor.http.URLBuilder
import io.ktor.http.parseQueryString

private class NewPipeDownloaderImpl : Downloader() {

    private val client = OkHttpClient.Builder().build()
    private val acceptLanguageHeader: String
        get() {
            val locale = java.util.Locale.getDefault()
            return "${locale.toLanguageTag()},${locale.language};q=0.9"
        }

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder = okhttp3.Request.Builder()
            .method(httpMethod, dataToSend?.toRequestBody())
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
            .addHeader("Accept-Language", acceptLanguageHeader)

        headers.forEach { (headerName, headerValueList) ->
            if (headerValueList.size > 1) {
                requestBuilder.removeHeader(headerName)
                headerValueList.forEach { headerValue ->
                    requestBuilder.addHeader(headerName, headerValue)
                }
            } else if (headerValueList.size == 1) {
                requestBuilder.header(headerName, headerValueList[0])
            }
        }

        val response = client.newCall(requestBuilder.build()).execute()

        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", url)
        }

        val responseBodyToReturn = response.body?.string()

        val latestUrl = response.request.url.toString()
        return Response(response.code, response.message, response.headers.toMultimap(), responseBodyToReturn, responseBodyToReturn?.toByteArray(), latestUrl)
    }

    override fun executeAsync(request: Request, callback: org.schabi.newpipe.extractor.downloader.Downloader.AsyncCallback?): CancellableCall {
        val okHttpRequestBuilder = okhttp3.Request.Builder()
            .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
            .url(request.url())
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
            .addHeader("Accept-Language", acceptLanguageHeader)

        request.headers().forEach { (headerName, headerValueList) ->
            if (headerValueList.size > 1) {
                okHttpRequestBuilder.removeHeader(headerName)
                headerValueList.forEach { headerValue ->
                    okHttpRequestBuilder.addHeader(headerName, headerValue)
                }
            } else if (headerValueList.size == 1) {
                okHttpRequestBuilder.header(headerName, headerValueList[0])
            }
        }

        val call = client.newCall(okHttpRequestBuilder.build())
        
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                callback?.onError(e)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val responseBodyToReturn = response.body?.string()
                val latestUrl = response.request.url.toString()
                val npResponse = Response(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    responseBodyToReturn,
                    responseBodyToReturn?.toByteArray(),
                    latestUrl
                )
                callback?.onSuccess(npResponse)
            }
        })
        
        return CancellableCall(call)
    }
}

object NewPipeUtils {
    private var initialized = false

    fun initIfNeeded() {
        if (!initialized) {
            NewPipe.init(
                NewPipeDownloaderImpl(),
                org.schabi.newpipe.extractor.localization.Localization.fromLocale(java.util.Locale.getDefault())
            )
            initialized = true
        }
    }

    fun getSignatureTimestamp(videoId: String): Int? {
        initIfNeeded()
        return try {
            YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
        } catch (e: Exception) {
            android.util.Log.e("NewPipeUtils", "Failed to get signature timestamp", e)
            null
        }
    }

    fun getStreamUrl(format: PlayerResponse.StreamingData.Format, videoId: String): String? {
        initIfNeeded()
        return try {
            val urlStr = format.url ?: format.signatureCipher?.let { signatureCipher ->
                val params = parseQueryString(signatureCipher)
                val obfuscatedSignature = params["s"] ?: throw ParsingException("Could not parse cipher signature")
                val signatureParam = params["sp"] ?: throw ParsingException("Could not parse cipher signature parameter")
                val urlBuilder = params["url"]?.let { URLBuilder(it) } ?: throw ParsingException("Could not parse cipher url")
                urlBuilder.parameters[signatureParam] = YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, obfuscatedSignature)
                urlBuilder.toString()
            } ?: throw ParsingException("Could not find format url")

            YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, urlStr)
        } catch (e: Exception) {
            android.util.Log.e("NewPipeUtils", "getStreamUrl failed for video $videoId: ${e.message}", e)
            format.url // fallback
        }
    }
}
