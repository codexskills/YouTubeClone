package com.darkk.youtube.innertube

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable
import android.util.Log

/**
 * YouTube API wrapper – modeled after AirBeats' YouTube.kt object.
 * Uses InnerTube (youtube.com/youtubei/v1/) for all data fetching.
 */
object YouTubeApi {
    private val client = InnerTubeClient.httpClient
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend fun getSearchSuggestions(query: String): Result<List<String>> = runCatching {
        if (query.isBlank()) return@runCatching emptyList()
        val url = "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=${java.net.URLEncoder.encode(query, "UTF-8")}"
        val responseStr = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            java.net.URL(url).readText()
        }
        val jsonArray = Json.parseToJsonElement(responseStr).jsonArray
        if (jsonArray.size > 1) {
            val suggestionsArray = jsonArray[1].jsonArray
            suggestionsArray.map { it.jsonPrimitive.content }
        } else {
            emptyList()
        }
    }

    // ── Home feed ─────────────────────────────────────────────────────────────

    /**
     * Fetches trending/recommended videos (YouTube home feed).
     * browseId = "FEwhat_to_watch" is the standard YouTube home feed.
     */
    suspend fun getHomeFeed(continuation: String? = null, visitorData: String? = null): Result<HomeFeedPage> = runCatching {
        val body = buildJsonObject {
            put("context", InnerTubeClient.buildContext(InnerTubeClient.ClientType.WEB, visitorData))
            if (continuation != null) {
                put("continuation", continuation)
            } else {
                put("browseId", "FEwhat_to_watch")
            }
        }
        val responseStr: String = client.post("browse") {
            with(InnerTubeClient) { applyYtHeaders(InnerTubeClient.ClientType.WEB) }
            setBody(body.toString())
        }.body()

        val response: BrowseResponse = json.decodeFromString(responseStr)

        val videos = mutableListOf<VideoItem>()

        var newContinuation: String? = null

        // Parse initial feed
        if (continuation == null) {
            response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.richGridRenderer?.contents?.forEach { item ->
                    item.richItemRenderer?.content?.videoRenderer?.toVideoItem()?.let { videos.add(it) }
                    item.richSectionRenderer?.content?.richShelfRenderer?.contents?.forEach { shelfItem ->
                        shelfItem.content?.reelItemRenderer?.toVideoItem()?.let { videos.add(it) }
                    }
                    if (item.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token != null) {
                        newContinuation = item.continuationItemRenderer.continuationEndpoint.continuationCommand.token
                    }
                }
            
            if (videos.isEmpty()) {
                response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                    ?.tabRenderer?.content?.richGridRenderer?.contents?.forEach { item ->
                        item.richItemRenderer?.content?.videoRenderer?.toVideoItem()?.let { videos.add(it) }
                        item.richSectionRenderer?.content?.richShelfRenderer?.contents?.forEach { shelfItem ->
                            shelfItem.content?.reelItemRenderer?.toVideoItem()?.let { videos.add(it) }
                        }
                        if (item.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token != null) {
                            newContinuation = item.continuationItemRenderer.continuationEndpoint.continuationCommand.token
                        }
                    }
            }
        } else {
            // Parse paginated feed (onResponseReceivedActions)
            response.onResponseReceivedActions?.firstOrNull()?.appendContinuationItemsAction?.continuationItems?.forEach { item ->
                item.richItemRenderer?.content?.videoRenderer?.toVideoItem()?.let { videos.add(it) }
                item.richSectionRenderer?.content?.richShelfRenderer?.contents?.forEach { shelfItem ->
                    shelfItem.content?.reelItemRenderer?.toVideoItem()?.let { videos.add(it) }
                }
                if (item.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token != null) {
                    newContinuation = item.continuationItemRenderer.continuationEndpoint.continuationCommand.token
                }
            }
        }

        val newVisitorData = response.responseContext?.visitorData ?: visitorData

        HomeFeedPage(videos, newContinuation, newVisitorData)
    }

    // ── Search ─────────────────────────────────────────────────────────────────

    suspend fun search(query: String, continuation: String? = null, visitorData: String? = null): Result<HomeFeedPage> = runCatching {
        val body = buildJsonObject {
            put("context", InnerTubeClient.buildContext(InnerTubeClient.ClientType.WEB, visitorData))
            if (continuation != null) {
                put("continuation", continuation)
            } else {
                put("query", query)
            }
        }
        val response: SearchResponse = client.post("search") {
            with(InnerTubeClient) { applyYtHeaders(InnerTubeClient.ClientType.WEB) }
            setBody(body.toString())
        }.body()

        val videos = mutableListOf<VideoItem>()
        var newContinuation: String? = null

        if (continuation == null) {
            response.contents?.twoColumnSearchResultsRenderer?.primaryContents
                ?.sectionListRenderer?.contents?.forEach { section ->
                    section.itemSectionRenderer?.contents?.forEach { item ->
                        item.videoRenderer?.toVideoItem()?.let { videos.add(it) }
                        if (item.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token != null) {
                            newContinuation = item.continuationItemRenderer.continuationEndpoint.continuationCommand.token
                        }
                    }
                    if (section.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token != null) {
                        newContinuation = section.continuationItemRenderer.continuationEndpoint.continuationCommand.token
                    }
                }
        } else {
            response.onResponseReceivedCommands?.firstOrNull()?.appendContinuationItemsAction?.continuationItems?.forEach { item ->
                item.videoRenderer?.toVideoItem()?.let { videos.add(it) }
                if (item.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token != null) {
                    newContinuation = item.continuationItemRenderer.continuationEndpoint.continuationCommand.token
                }
            }
        }

        HomeFeedPage(videos, newContinuation, visitorData)
    }

    // ── Tracking ──────────────────────────────────────────────
    
    suspend fun pingPlayback(videoId: String, visitorData: String? = null) {
        if (visitorData == null) return
        runCatching {
            val body = buildJsonObject {
                put("context", InnerTubeClient.buildContext(InnerTubeClient.ClientType.WEB, visitorData))
                put("videoId", videoId)
            }
            client.post("next") {
                with(InnerTubeClient) { applyYtHeaders(InnerTubeClient.ClientType.WEB) }
                setBody(body.toString())
            }
        }
    }

    suspend fun getNewReleases(): Result<List<ReleaseItem>> = runCatching {
        val body = buildJsonObject {
            put("context", InnerTubeClient.buildContext(InnerTubeClient.ClientType.WEB_REMIX, null))
            put("browseId", "FEmusic_new_releases_albums")
        }
        val responseStr: String = client.post("browse") {
            with(InnerTubeClient) { applyYtHeaders(InnerTubeClient.ClientType.WEB_REMIX) }
            setBody(body.toString())
        }.body()

        val jsonObject = Json.parseToJsonElement(responseStr).jsonObject
        val contents = jsonObject["contents"]?.jsonObject
            ?.get("singleColumnBrowseResultsRenderer")?.jsonObject
            ?.get("tabs")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("tabRenderer")?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("sectionListRenderer")?.jsonObject
            ?.get("contents")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("gridRenderer")?.jsonObject
            ?.get("items")?.jsonArray

        val releases = mutableListOf<ReleaseItem>()
        contents?.forEach { item ->
            val renderer = item.jsonObject["musicTwoRowItemRenderer"]?.jsonObject
            if (renderer != null) {
                val browseId = renderer["navigationEndpoint"]?.jsonObject
                    ?.get("browseEndpoint")?.jsonObject
                    ?.get("browseId")?.jsonPrimitive?.content
                
                val title = renderer["title"]?.jsonObject
                    ?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("text")?.jsonPrimitive?.content ?: "Unknown"
                
                val artist = renderer["subtitle"]?.jsonObject
                    ?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("text")?.jsonPrimitive?.content ?: "Unknown Artist"

                val thumbnails = renderer["thumbnailRenderer"]?.jsonObject
                    ?.get("musicThumbnailRenderer")?.jsonObject
                    ?.get("thumbnail")?.jsonObject
                    ?.get("thumbnails")?.jsonArray
                val thumbnailUrl = thumbnails?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content ?: ""

                if (browseId != null) {
                    releases.add(ReleaseItem(
                        id = browseId,
                        title = title,
                        artist = artist,
                        thumbnail = thumbnailUrl,
                        isExplicit = false
                    ))
                }
            }
        }
        releases
    }

    // ── Player (stream URL resolution) ────────────────────────────────────────
    // Mirrors AirBeats YTPlayerUtils pattern: try multiple clients

    /**
     * Returns the best available stream URL for a given [videoId].
     * Tries multiple clients (ANDROID_VR_NO_AUTH, IOS, WEB) to find working streams.
     * For video playback we prefer combined formats; falls back to adaptive video+audio.
     */
    suspend fun getPlayerData(videoId: String): Result<PlayerData> = runCatching {
        // Try WEB first (since we have full cipher decryption via NewPipeExtractor)
        val signatureTimestamp = NewPipeUtils.getSignatureTimestamp(videoId)
        val webResponse = fetchPlayerResponse(videoId, InnerTubeClient.ClientType.WEB, signatureTimestamp)
        if (webResponse?.playabilityStatus?.status == "OK") {
            var playerData = buildPlayerData(videoId, webResponse, InnerTubeClient.ClientType.WEB.userAgent)
            if (playerData != null) {
                playerData = enrichWithNewPipe(playerData)
                return@runCatching playerData
            }
        }

        // Try ANDROID_VR next
        val androidVrResponse = fetchPlayerResponse(videoId, InnerTubeClient.ClientType.ANDROID_VR)
        if (androidVrResponse?.playabilityStatus?.status == "OK") {
            var playerData = buildPlayerData(videoId, androidVrResponse, InnerTubeClient.ClientType.ANDROID_VR.userAgent)
            if (playerData != null) {
                playerData = enrichWithNewPipe(playerData)
                return@runCatching playerData
            }
        }

        // Fallback: IOS
        val iosResponse = fetchPlayerResponse(videoId, InnerTubeClient.ClientType.IOS)
        if (iosResponse?.playabilityStatus?.status == "OK") {
            var playerData = buildPlayerData(videoId, iosResponse, InnerTubeClient.ClientType.IOS.userAgent)
            if (playerData != null) {
                playerData = enrichWithNewPipe(playerData)
                return@runCatching playerData
            }
        }

        throw Exception("No playable stream found for videoId: $videoId")
    }

    suspend fun getRelatedVideos(videoId: String): Result<List<VideoItem>> = runCatching {
        NewPipeUtils.initIfNeeded()
        val extractor = org.schabi.newpipe.extractor.ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
        extractor.fetchPage()
        
        val relatedItemsPage = extractor.relatedItems
        val relatedItemsList = relatedItemsPage?.items ?: emptyList()
        relatedItemsList.mapNotNull { item ->
            if (item is org.schabi.newpipe.extractor.stream.StreamInfoItem) {
                val urlStr = item.url ?: return@mapNotNull null
                val id = urlStr.substringAfter("v=", urlStr.substringAfterLast("/"))
                VideoItem(
                    videoId = id,
                    title = item.name ?: "",
                    thumbnail = item.thumbnailUrl ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                    channelName = item.uploaderName ?: "Unknown",
                    channelAvatar = item.uploaderAvatarUrl?.let { if (it.startsWith("//")) "https:$it" else it },
                    viewCount = if (item.viewCount >= 0) com.darkk.youtube.utils.FormatUtils.formatCount(item.viewCount) + " views" else "",
                    publishedAt = com.darkk.youtube.utils.FormatUtils.parseRelativeTime(item.textualUploadDate ?: ""),
                    duration = if (item.duration > 0) {
                        val h = item.duration / 3600
                        val m = (item.duration % 3600) / 60
                        val s = item.duration % 60
                        if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
                    } else "",
                    isShort = item.duration in 1..60,
                    channelUrl = item.uploaderUrl ?: ""
                )
            } else null
        }
    }

    private fun enrichWithNewPipe(data: PlayerData): PlayerData {
        return try {
            NewPipeUtils.initIfNeeded()
            val extractor = org.schabi.newpipe.extractor.ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=" + data.videoId)
            extractor.fetchPage()
            
            val relatedItemsPage = extractor.relatedItems
            val relatedItemsList = relatedItemsPage?.items ?: emptyList()
            val mappedRelated = relatedItemsList.mapNotNull { item ->
                if (item is org.schabi.newpipe.extractor.stream.StreamInfoItem) {
                    val urlStr = item.url ?: return@mapNotNull null
                    val id = urlStr.substringAfter("v=", urlStr.substringAfterLast("/"))
                    VideoItem(
                        videoId = id,
                        title = item.name ?: "",
                        thumbnail = item.thumbnailUrl ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                        channelName = item.uploaderName ?: "Unknown",
                        channelAvatar = item.uploaderAvatarUrl?.let { if (it.startsWith("//")) "https:$it" else it },
                        viewCount = if (item.viewCount >= 0) com.darkk.youtube.utils.FormatUtils.formatCount(item.viewCount) + " views" else "",
                        publishedAt = com.darkk.youtube.utils.FormatUtils.parseRelativeTime(item.textualUploadDate ?: ""),
                        duration = if (item.duration > 0) {
                            val h = item.duration / 3600
                            val m = (item.duration % 3600) / 60
                            val s = item.duration % 60
                            if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
                        } else "",
                        isShort = item.duration in 1..60,
                        channelUrl = item.uploaderUrl ?: ""
                    )
                } else null
            }
            
            var commentCountStr = ""
            var exactCommentCount = 0
            var mappedComments = emptyList<CommentData>()
            var nextCommentsPage: org.schabi.newpipe.extractor.Page? = null
            try {
                val commentsInfo = org.schabi.newpipe.extractor.comments.CommentsInfo.getInfo("https://www.youtube.com/watch?v=" + data.videoId)
                exactCommentCount = 0
                
                try {
                    val extractor = commentsInfo.commentsExtractor
                    val ajaxJsonSafeField = extractor.javaClass.getDeclaredField("ajaxJsonSafe")
                    ajaxJsonSafeField.isAccessible = true
                    val ajaxJsonSafe = ajaxJsonSafeField.get(extractor) as org.json.JSONObject
                    
                    val endpoints = ajaxJsonSafe.optJSONArray("onResponseReceivedEndpoints")
                    if (endpoints != null) {
                        for (i in 0 until endpoints.length()) {
                            val endpoint = endpoints.optJSONObject(i) ?: continue
                            val cmd = endpoint.optJSONObject("reloadContinuationItemsCommand") ?: endpoint.optJSONObject("appendContinuationItemsAction") ?: continue
                            val items = cmd.optJSONArray("continuationItems") ?: continue
                            for (j in 0 until items.length()) {
                                val item = items.optJSONObject(j) ?: continue
                                val header = item.optJSONObject("commentsHeaderRenderer") ?: continue
                                val countTextObj = header.optJSONObject("countText") ?: continue
                                val runs = countTextObj.optJSONArray("runs") ?: continue
                                for (k in 0 until runs.length()) {
                                    val run = runs.optJSONObject(k) ?: continue
                                    val text = run.optString("text", "")
                                    val digits = text.replace(Regex("\\D+"), "")
                                    if (digits.isNotEmpty()) {
                                        exactCommentCount = digits.toInt()
                                        break
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("YouTubeApi", "Failed to reflect comment count", e)
                }

                nextCommentsPage = commentsInfo.nextPage
                val commentsList = commentsInfo.relatedItems
                mappedComments = commentsList.mapNotNull { item ->
                    if (item is org.schabi.newpipe.extractor.comments.CommentsInfoItem) {
                        CommentData(
                            author = item.uploaderName ?: "Unknown",
                            authorAvatar = item.uploaderAvatarUrl ?: "",
                            text = item.commentText ?: "",
                            likeCount = if (item.likeCount >= 0) com.darkk.youtube.utils.FormatUtils.formatCount(item.likeCount.toLong()) else "",
                            time = com.darkk.youtube.utils.FormatUtils.parseRelativeTime(item.textualUploadDate ?: ""),
                            replyCount = 0
                        )
                    } else null
                }
                commentCountStr = ""
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            val newQualities = mutableListOf<VideoQuality>()
            val bestAudio = extractor.audioStreams?.maxByOrNull { it.averageBitrate }?.url ?: extractor.audioStreams?.firstOrNull()?.url
            
            extractor.videoStreams?.forEach { v ->
                val url = v.url ?: return@forEach
                val height = v.resolution?.replace("p", "")?.replace(Regex("\\D+"), "")?.toIntOrNull() ?: 0
                val label = v.resolution ?: "${height}p"
                newQualities.add(VideoQuality(label = label, videoUrl = url, audioUrl = null, height = height, isCombined = true))
            }
            
            if (bestAudio != null) {
                extractor.videoOnlyStreams?.forEach { v ->
                    val url = v.url ?: return@forEach
                    val height = v.resolution?.replace("p", "")?.replace(Regex("\\D+"), "")?.toIntOrNull() ?: 0
                    val label = v.resolution ?: "${height}p"
                    newQualities.add(VideoQuality(label = label, videoUrl = url, audioUrl = bestAudio, height = height, isCombined = false))
                }
            }
            
            val finalQualities = if (newQualities.isNotEmpty()) {
                newQualities.distinctBy { it.label }.sortedByDescending { it.height }
            } else {
                data.qualities
            }
            
            val newDefault = finalQualities.find { it.height == 360 } ?: finalQualities.maxByOrNull { it.height } ?: finalQualities.firstOrNull() ?: data.defaultQuality

            data.copy(
                qualities = finalQualities,
                defaultQuality = newDefault,
                likes = if (extractor.likeCount >= 0) com.darkk.youtube.utils.FormatUtils.formatCount(extractor.likeCount) else "",
                views = if (extractor.viewCount >= 0) com.darkk.youtube.utils.FormatUtils.formatCount(extractor.viewCount) else "",
                channelAvatar = extractor.uploaderAvatarUrl?.let { if (it.startsWith("//")) "https:$it" else it } ?: data.channelAvatar,
                channelUrl = extractor.uploaderUrl ?: data.channelUrl,
                channelSubscribers = if (extractor.uploaderSubscriberCount > 0) com.darkk.youtube.utils.FormatUtils.formatCount(extractor.uploaderSubscriberCount) else "",
                relatedVideos = mappedRelated,
                commentCount = commentCountStr,
                comments = mappedComments,
                description = extractor.description?.content ?: "",
                uploadDate = com.darkk.youtube.utils.FormatUtils.parseRelativeTime(extractor.textualUploadDate ?: ""),
                tags = extractor.tags ?: emptyList(),
                exactCommentCount = exactCommentCount,
                nextCommentsPage = nextCommentsPage
            )
        } catch (e: Exception) {
            Log.e("YouTubeApi", "Failed to enrich PlayerData with NewPipe", e)
            data
        }
    }

    suspend fun getChannelDetails(channelUrl: String): Result<ChannelData> = runCatching {
        NewPipeUtils.initIfNeeded()
        val fullUrl = if (channelUrl.startsWith("/")) "https://www.youtube.com$channelUrl" else channelUrl
        val extractor = org.schabi.newpipe.extractor.ServiceList.YouTube.getChannelExtractor(fullUrl)
        extractor.fetchPage()

        var rawAvatar = extractor.avatarUrl
        var rawBanner = extractor.bannerUrl
        if (rawAvatar.isNullOrBlank() || rawBanner.isNullOrBlank()) {
            try {
                val html: String = client.get(fullUrl).body()
                if (rawAvatar.isNullOrBlank()) {
                    val avatarMatch = """"avatar":\{"thumbnails":\[\{"url":"([^"]+)"""".toRegex().find(html)
                    rawAvatar = avatarMatch?.groups?.get(1)?.value
                }
                if (rawBanner.isNullOrBlank()) {
                    val bannerMatch = """"imageBannerViewModel":\{"image":\{"sources":\[\{"url":"([^"]+)"""".toRegex().find(html)
                        ?: """"tvBanner":\{"thumbnails":\[\{"url":"([^"]+)"""".toRegex().find(html)
                        ?: """"mobileBanner":\{"thumbnails":\[\{"url":"([^"]+)"""".toRegex().find(html)
                        ?: """"banner":\{"thumbnails":\[\{"url":"([^"]+)"""".toRegex().find(html)
                    rawBanner = bannerMatch?.groups?.get(1)?.value
                }
            } catch (e: Exception) {
                android.util.Log.e("YouTubeApi", "Failed to fallback scrape avatar/banner", e)
            }
        }
        val safeAvatarUrl = rawAvatar?.let { if (it.startsWith("//")) "https:$it" else it }?.replace("s48", "s176")?.replace("=s48", "=s176") ?: ""

        val videosList = extractor.initialPage.items.mapNotNull { item ->
            if (item is org.schabi.newpipe.extractor.stream.StreamInfoItem) {
                val urlStr = item.url ?: return@mapNotNull null
                val id = urlStr.substringAfter("v=", urlStr.substringAfterLast("/"))
                VideoItem(
                    videoId = id,
                    title = item.name ?: "",
                    thumbnail = item.thumbnailUrl ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                    channelName = item.uploaderName ?: extractor.name,
                    channelAvatar = safeAvatarUrl,
                    viewCount = if (item.viewCount >= 0) com.darkk.youtube.utils.FormatUtils.formatCount(item.viewCount) + " views" else "",
                    publishedAt = com.darkk.youtube.utils.FormatUtils.parseRelativeTime(item.textualUploadDate ?: ""),
                    duration = if (item.duration > 0) {
                        String.format("%d:%02d", item.duration / 60, item.duration % 60)
                    } else "",
                    isShort = item.duration in 1..60,
                    channelUrl = item.uploaderUrl ?: ""
                )
            } else null
        }

        val channelData = ChannelData(
            id = extractor.id ?: "",
            name = extractor.name,
            avatarUrl = safeAvatarUrl,
            bannerUrl = rawBanner?.let { if (it.startsWith("//")) "https:$it" else it } ?: "",
            subscribers = if (extractor.subscriberCount > 0) com.darkk.youtube.utils.FormatUtils.formatCount(extractor.subscriberCount) + " subscribers" else "",
            videoCount = "",
            description = extractor.description ?: "",
            isVerified = extractor.isVerified,
            handle = "",
            videos = videosList
        )

        channelData
    }

    suspend fun getMoreComments(videoId: String, page: org.schabi.newpipe.extractor.Page): Result<Pair<List<CommentData>, org.schabi.newpipe.extractor.Page?>> = runCatching {
        NewPipeUtils.initIfNeeded()
        val moreItems = org.schabi.newpipe.extractor.comments.CommentsInfo.getMoreItems(
            org.schabi.newpipe.extractor.ServiceList.YouTube,
            "https://www.youtube.com/watch?v=$videoId",
            page
        )
        val mappedComments = moreItems.items.mapNotNull { item ->
            if (item is org.schabi.newpipe.extractor.comments.CommentsInfoItem) {
                CommentData(
                    author = item.uploaderName ?: "Unknown",
                    authorAvatar = item.uploaderAvatarUrl ?: "",
                    text = item.commentText ?: "",
                    likeCount = if (item.likeCount >= 0) com.darkk.youtube.utils.FormatUtils.formatCount(item.likeCount.toLong()) else "",
                    time = com.darkk.youtube.utils.FormatUtils.parseRelativeTime(item.textualUploadDate ?: ""),
                    replyCount = 0
                )
            } else null
        }
        Pair(mappedComments, moreItems.nextPage)
    }

    private suspend fun fetchPlayerResponse(
        videoId: String,
        clientType: InnerTubeClient.ClientType,
        signatureTimestamp: Int? = null
    ): PlayerResponse? = runCatching {
        val bodyJson = buildJsonObject {
            put("context", InnerTubeClient.buildContext(clientType))
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            if (signatureTimestamp != null) {
                put("playbackContext", buildJsonObject {
                    put("contentPlaybackContext", buildJsonObject {
                        put("signatureTimestamp", signatureTimestamp)
                    })
                })
            }
        }

        client.post("player") {
            with(InnerTubeClient) { applyYtHeaders(clientType) }
            setBody(bodyJson.toString())
        }.body<PlayerResponse>()
    }.getOrNull()

    private fun buildPlayerData(videoId: String, response: PlayerResponse, userAgent: String): PlayerData? {
        val details = response.videoDetails ?: return null

        val combinedFormats = response.streamingData?.formats
            ?.filter { it.url != null || it.signatureCipher != null }
            ?.sortedByDescending { it.qualityScore }
            ?: emptyList()
            
        val adaptiveVideoFormats = response.streamingData?.adaptiveFormats
            ?.filter { it.mimeType?.startsWith("video/") == true && (it.url != null || it.signatureCipher != null) } ?: emptyList()
            
        val adaptiveAudioFormats = response.streamingData?.adaptiveFormats
            ?.filter { it.mimeType?.startsWith("audio/") == true && (it.url != null || it.signatureCipher != null) } ?: emptyList()

        val qualities = mutableListOf<VideoQuality>()

        // From combined formats (video + audio in one stream)
        combinedFormats.forEach { fmt ->
            val url = NewPipeUtils.getStreamUrl(fmt, videoId)
            if (url != null) {
                val label = fmt.qualityLabel ?: "${fmt.height}p"
                qualities.add(VideoQuality(
                    label = label,
                    videoUrl = url,
                    audioUrl = null,
                    height = fmt.height ?: 0,
                    isCombined = true
                ))
            }
        }

        // From adaptive formats (group by quality)
        adaptiveVideoFormats.forEach { vFmt ->
            val audioFmt = adaptiveAudioFormats.firstOrNull()
            if (audioFmt != null) {
                val videoUrl = NewPipeUtils.getStreamUrl(vFmt, videoId)
                val audioUrl = NewPipeUtils.getStreamUrl(audioFmt, videoId)
                if (videoUrl != null && audioUrl != null) {
                    val label = vFmt.qualityLabel ?: "${vFmt.height}p"
                    qualities.add(VideoQuality(
                        label = label,
                        videoUrl = videoUrl,
                        audioUrl = audioUrl,
                        height = vFmt.height ?: 0,
                        isCombined = false
                    ))
                }
            }
        }

        if (qualities.isEmpty()) {
            Log.e("YouTubeApi", "qualities list is empty for $videoId")
            return null
        }

        // Default: highest quality combined, or highest adaptive
        val defaultQuality = qualities.maxByOrNull { it.height } ?: qualities.first()

        return PlayerData(
            videoId = videoId,
            title = details.title ?: "Unknown",
            author = details.author ?: "Unknown",
            thumbnail = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            qualities = qualities.distinctBy { it.label },
            defaultQuality = defaultQuality,
            userAgent = userAgent
        )
    }
}

// ─── Domain models ────────────────────────────────────────────────────────────

data class HomeFeedPage(
    val videos: List<VideoItem>,
    val continuationToken: String?,
    val visitorData: String?
)

@Serializable
data class VideoItem(
    val videoId: String,
    val title: String,
    val thumbnail: String,
    val channelName: String,
    val channelAvatar: String?,
    val viewCount: String,
    val publishedAt: String,
    val duration: String,
    val isShort: Boolean = false,
    val channelUrl: String = ""
)

data class PlayerData(
    val videoId: String,
    val title: String,
    val author: String,
    val thumbnail: String,
    val qualities: List<VideoQuality>,
    val defaultQuality: VideoQuality,
    val userAgent: String,
    val likes: String = "",
    val views: String = "",
    val channelAvatar: String? = null,
    val channelUrl: String = "",
    val channelSubscribers: String = "",
    val relatedVideos: List<VideoItem> = emptyList(),
    val commentCount: String = "",
    val comments: List<CommentData> = emptyList(),
    val description: String = "",
    val uploadDate: String = "",
    val tags: List<String> = emptyList(),
    val exactCommentCount: Int = 0,
    val nextCommentsPage: org.schabi.newpipe.extractor.Page? = null
)

data class CommentData(
    val author: String,
    val authorAvatar: String,
    val text: String,
    val likeCount: String,
    val time: String,
    val replyCount: Int = 0
)

data class VideoQuality(
    val label: String,
    val videoUrl: String,
    val audioUrl: String?,
    val height: Int,
    val isCombined: Boolean
)

// ─── Extension functions ───────────────────────────────────────────────────────

private fun VideoRenderer.toVideoItem(): VideoItem? {
    val id = videoId ?: return null
    val urlStr = navigationEndpoint?.commandMetadata?.webCommandMetadata?.url ?: ""
    val isShortVideo = urlStr.contains("/shorts/")
    return VideoItem(
        videoId = id,
        title = title?.text() ?: return null,
        thumbnail = thumbnail?.bestQuality() ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
        channelName = ownerText?.text() ?: "Unknown",
        channelAvatar = channelThumbnailSupportedRenderers?.channelThumbnailWithLinkRenderer?.thumbnail?.thumbnails?.firstOrNull()?.url,
        viewCount = com.darkk.youtube.utils.FormatUtils.formatStringCount(viewCountText?.simpleText ?: ""),
        publishedAt = publishedTimeText?.simpleText ?: "",
        duration = lengthText?.simpleText ?: "",
        isShort = isShortVideo,
        channelUrl = ownerText?.runs?.firstOrNull()?.navigationEndpoint?.commandMetadata?.webCommandMetadata?.url ?: ""
    )
}

private fun com.darkk.youtube.innertube.ReelItemRenderer.toVideoItem(): VideoItem? {
    val id = videoId ?: return null
    return VideoItem(
        videoId = id,
        title = headline?.simpleText ?: "",
        thumbnail = thumbnail?.bestQuality() ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
        channelName = "Shorts",
        channelAvatar = null,
        viewCount = com.darkk.youtube.utils.FormatUtils.formatStringCount(viewCountText?.simpleText ?: ""),
        publishedAt = "",
        duration = "",
        isShort = true,
        channelUrl = ""
    )
}
