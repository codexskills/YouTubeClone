package com.darkk.youtube.data

import android.content.Context
import com.darkk.youtube.innertube.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class UserProfile(
    var name: String = "",
    var handle: String = ""
)

@Serializable
data class Playlist(
    val id: String,
    var name: String,
    var isPrivate: Boolean = true,
    var videos: List<VideoItem> = emptyList()
)

class LocalRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val profileFile = File(context.filesDir, "user_profile.json")
    private val historyFile = File(context.filesDir, "history.json")
    private val playlistsFile = File(context.filesDir, "playlists.json")

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _history = MutableStateFlow<List<VideoItem>>(emptyList())
    val history: StateFlow<List<VideoItem>> = _history.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    suspend fun loadData() = withContext(Dispatchers.IO) {
        if (profileFile.exists()) {
            try {
                _userProfile.value = json.decodeFromString(profileFile.readText())
            } catch (e: Exception) { e.printStackTrace() }
        }

        if (historyFile.exists()) {
            try {
                _history.value = json.decodeFromString(historyFile.readText())
            } catch (e: Exception) { e.printStackTrace() }
        }

        var loadedPlaylists = emptyList<Playlist>()
        if (playlistsFile.exists()) {
            try {
                loadedPlaylists = json.decodeFromString(playlistsFile.readText())
            } catch (e: Exception) { e.printStackTrace() }
        }
        
        val defaultPlaylists = loadedPlaylists.toMutableList()
        if (defaultPlaylists.none { it.id == "liked_videos" }) {
            defaultPlaylists.add(Playlist("liked_videos", "Liked videos"))
        }
        if (defaultPlaylists.none { it.id == "watch_later" }) {
            defaultPlaylists.add(Playlist("watch_later", "Watch Later"))
        }
        
        _playlists.value = defaultPlaylists
        if (defaultPlaylists != loadedPlaylists) {
            savePlaylists()
        }
    }

    suspend fun saveProfile(name: String) = withContext(Dispatchers.IO) {
        val handle = "@${name.replace(" ", "")}-g6n"
        val profile = UserProfile(name, handle)
        _userProfile.value = profile
        profileFile.writeText(json.encodeToString(profile))
    }

    suspend fun addToHistory(video: VideoItem) = withContext(Dispatchers.IO) {
        val current = _history.value.toMutableList()
        current.removeAll { it.videoId == video.videoId }
        current.add(0, video)
        _history.value = current
        historyFile.writeText(json.encodeToString(current))
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        _history.value = emptyList()
        historyFile.writeText("[]")
    }

    suspend fun toggleLike(video: VideoItem): Boolean = withContext(Dispatchers.IO) {
        var isLiked = false
        val currentPlaylists = _playlists.value.toMutableList()
        val likedPlaylistIndex = currentPlaylists.indexOfFirst { it.id == "liked_videos" }
        if (likedPlaylistIndex != -1) {
            val likedPlaylist = currentPlaylists[likedPlaylistIndex]
            val videos = likedPlaylist.videos.toMutableList()
            if (videos.any { it.videoId == video.videoId }) {
                videos.removeAll { it.videoId == video.videoId }
            } else {
                videos.add(0, video)
                isLiked = true
            }
            currentPlaylists[likedPlaylistIndex] = likedPlaylist.copy(videos = videos)
            _playlists.value = currentPlaylists
            savePlaylists()
        }
        isLiked
    }

    suspend fun isLiked(videoId: String): Boolean {
        val likedPlaylist = _playlists.value.find { it.id == "liked_videos" }
        return likedPlaylist?.videos?.any { it.videoId == videoId } == true
    }

    suspend fun toggleVideoInPlaylist(playlistId: String, video: VideoItem): Boolean = withContext(Dispatchers.IO) {
        var isInPlaylist = false
        val currentPlaylists = _playlists.value.toMutableList()
        val index = currentPlaylists.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val pl = currentPlaylists[index]
            val videos = pl.videos.toMutableList()
            if (videos.any { it.videoId == video.videoId }) {
                videos.removeAll { it.videoId == video.videoId }
            } else {
                videos.add(0, video)
                isInPlaylist = true
            }
            currentPlaylists[index] = pl.copy(videos = videos)
            _playlists.value = currentPlaylists
            savePlaylists()
        }
        isInPlaylist
    }

    suspend fun createPlaylist(name: String) = withContext(Dispatchers.IO) {
        val currentPlaylists = _playlists.value.toMutableList()
        val newId = "pl_${System.currentTimeMillis()}"
        currentPlaylists.add(Playlist(newId, name))
        _playlists.value = currentPlaylists
        savePlaylists()
    }

    private fun savePlaylists() {
        playlistsFile.writeText(json.encodeToString(_playlists.value))
    }
}
