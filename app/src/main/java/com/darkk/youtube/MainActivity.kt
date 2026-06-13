package com.darkk.youtube

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.darkk.youtube.ui.components.drawBackdropCustomShape
import com.darkk.youtube.ui.components.layerBackdrop
import com.darkk.youtube.ui.components.rememberBackdrop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.darkk.youtube.innertube.VideoItem
import com.darkk.youtube.ui.theme.YoutubeTheme
import com.darkk.youtube.viewmodel.YouTubeViewModel
import com.darkk.youtube.data.LocalRepository
import com.darkk.youtube.ui.screens.*
import kotlinx.coroutines.launch

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        setContent {
            YoutubeTheme {
                YouTubeApp()
            }
        }
    }
}

@Composable
fun YouTubeApp() {
    val context = LocalContext.current
    val repository = remember { com.darkk.youtube.data.LocalRepository(context) }
    val viewModel: YouTubeViewModel = viewModel()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (!isGranted) {
                // Permission denied, handle if needed
            }
        }
        
        LaunchedEffect(Unit) {
            val isGranted = ContextCompat.checkSelfPermission(
                context, 
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (!isGranted) {
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var currentTab by remember { mutableStateOf(0) }
    var activeVideo by remember { mutableStateOf<VideoItem?>(null) }
    var isPlayerExpanded by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        repository.loadData()
    }
    
    var showNameDialog by remember { mutableStateOf(false) }
    val profile by repository.userProfile.collectAsState()

    // System back button when player is open +' close it entirely
    BackHandler(enabled = activeVideo != null || currentScreen !is Screen.Home || isPlayerExpanded) {
        if (isPlayerExpanded) {
            isPlayerExpanded = false
        } else if (currentScreen is Screen.PlaylistDetails) {
            currentScreen = Screen.Library
        } else if (currentScreen !is Screen.Home) {
            currentScreen = Screen.Home
            currentTab = 0
        } else if (activeVideo != null) {
            activeVideo = null
        }
    }

    val backdrop = rememberBackdrop()

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            AnimatedVisibility(
                visible = (currentScreen == Screen.Home || currentScreen is Screen.Channel || currentScreen is Screen.Library || currentScreen is Screen.PlaylistDetails || currentScreen is Screen.Subscriptions || currentScreen is Screen.AllSubscriptions || currentScreen == Screen.Shorts) && !isPlayerExpanded,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentSize(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                ) {
                    val layer = rememberGraphicsLayer()
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentSize(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // First 4 items in a pill shape
                        Box(
                            modifier = Modifier
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .drawBackdropCustomShape(
                                    backdrop = backdrop,
                                    layer = layer,
                                    luminanceAnimation = 0.3f,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                                .border(0.5.dp, Color.White.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape)
                        ) {
                            Row(
                                modifier = Modifier
                                    .wrapContentSize()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val NavItem = @Composable { index: Int, icon: androidx.compose.ui.graphics.vector.ImageVector ->
                                    val selected = currentTab == index
                                    Button(
                                        onClick = { 
                                            currentTab = index 
                                            if (index == 0) currentScreen = Screen.Home
                                            if (index == 1) currentScreen = Screen.Shorts
                                            if (index == 3) currentScreen = Screen.Subscriptions
                                        },
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (selected) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                                            contentColor = if (selected) Color(0xFFFA233B) else Color.White
                                        ),
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                                    }
                                }
                                NavItem(0, if (currentTab == 0) Icons.Filled.Home else Icons.Outlined.Home)
                                NavItem(1, Icons.Outlined.PlayArrow)
                                NavItem(2, Icons.Outlined.AddCircle)
                                NavItem(3, Icons.Outlined.Notifications)
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        // Last item in a circle shape
                        Box(
                            modifier = Modifier
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .drawBackdropCustomShape(
                                    backdrop = backdrop,
                                    layer = layer,
                                    luminanceAnimation = 0.3f,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                                .border(0.5.dp, Color.White.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape)
                        ) {
                            val selected = currentTab == 4
                            Button(
                                onClick = { 
                                    currentTab = 4
                                    if (profile?.name.isNullOrEmpty()) {
                                        showNameDialog = true
                                    } else {
                                        currentScreen = Screen.Library
                                    }
                                },
                                shape = androidx.compose.foundation.shape.CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                                    contentColor = if (selected) Color(0xFFFA233B) else Color.White
                                ),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(48.dp)
                            ) {
                                Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .layerBackdrop(backdrop)
            ) {
                // Home Screen is always underlying or Channel
                if (currentScreen is Screen.Channel) {
                    val channelUrl = (currentScreen as Screen.Channel).channelUrl
                    com.darkk.youtube.ui.screens.ChannelScreen(
                        channelUrl = channelUrl,
                        viewModel = viewModel,
                        innerPadding = innerPadding,
                        onBack = { currentScreen = Screen.Home },
                        onVideoClick = { video ->
                            activeVideo = video
                            isPlayerExpanded = true
                        }
                    )
                } else if (currentScreen is Screen.PlaylistDetails) {
                    val playlist = (currentScreen as Screen.PlaylistDetails).playlist
                    PlaylistScreen(
                        playlist = playlist,
                        repository = repository,
                        viewModel = viewModel,
                        innerPadding = innerPadding,
                        onBack = { currentScreen = Screen.Library },
                        onVideoClick = { video ->
                            activeVideo = video
                            isPlayerExpanded = true
                        },
                        onChannelClick = { url ->
                            currentScreen = Screen.Channel(url)
                        },
                        onSearchClick = {
                            currentScreen = Screen.Home
                            currentTab = 0
                            viewModel.setSearchActive(true)
                        }
                    )
                } else if (currentScreen is Screen.Subscriptions) {
                    com.darkk.youtube.ui.screens.SubscriptionsScreen(
                        viewModel = viewModel,
                        innerPadding = innerPadding,
                        onVideoClick = { video ->
                            activeVideo = video
                            isPlayerExpanded = true
                        },
                        onAllClick = {
                            currentScreen = Screen.AllSubscriptions
                        },
                        onSearchActivated = {
                            currentTab = 0
                            currentScreen = Screen.Home
                        },
                        onChannelClick = { url ->
                            currentScreen = Screen.Channel(url)
                        },
                        onNotificationsClick = {
                            currentScreen = Screen.Releases
                        }
                    )
                } else if (currentScreen is Screen.AllSubscriptions) {
                    com.darkk.youtube.ui.screens.AllSubscriptionsScreen(
                        viewModel = viewModel,
                        innerPadding = innerPadding,
                        onBack = { currentScreen = Screen.Subscriptions },
                        onChannelClick = { url ->
                            currentScreen = Screen.Channel(url)
                        }
                    )
                } else if (currentScreen is Screen.Releases) {
                    com.darkk.youtube.ui.screens.ReleasesScreen(
                        onBack = { currentScreen = Screen.Home },
                        innerPadding = innerPadding,
                        onVideoClick = { video ->
                            activeVideo = video
                            isPlayerExpanded = true
                        }
                    )
                } else if (currentScreen is Screen.Downloads) {
                    com.darkk.youtube.ui.screens.DownloadsScreen(
                        viewModel = viewModel,
                        innerPadding = innerPadding,
                        onBack = {
                            currentScreen = Screen.Library
                        }
                    )
                } else if (currentScreen is Screen.Settings) {
                    com.darkk.youtube.ui.screens.SettingsScreen(
                        onBack = { currentScreen = Screen.Library },
                        innerPadding = innerPadding
                    )
                } else if (currentScreen is Screen.History) {
                    com.darkk.youtube.ui.screens.HistoryScreen(
                        viewModel = viewModel,
                        repository = repository,
                        innerPadding = innerPadding,
                        onBack = { currentScreen = Screen.Library },
                        onVideoClick = { video ->
                            activeVideo = video
                            isPlayerExpanded = true
                        },
                        onChannelClick = { url ->
                            currentScreen = Screen.Channel(url)
                        }
                    )
                } else if (currentScreen is Screen.Library) {
                    LibraryScreen(
                        repository = repository,
                        viewModel = viewModel,
                        innerPadding = innerPadding,
                        onVideoClick = { video ->
                            activeVideo = video
                            isPlayerExpanded = true
                        },
                        onPlaylistClick = { playlist ->
                            currentScreen = Screen.PlaylistDetails(playlist)
                        },
                        onSearchClick = {
                            currentScreen = Screen.Home
                            currentTab = 0
                            viewModel.setSearchActive(true)
                        },
                        onDownloadsClick = {
                            currentScreen = Screen.Downloads
                        },
                        onHistoryClick = {
                            currentScreen = Screen.History
                        },
                        onSettingsClick = {
                            currentScreen = Screen.Settings
                        }
                    )
                } else if (currentTab == 0) {
                    HomeScreen(
                        viewModel = viewModel,
                        innerPadding = innerPadding,
                        onVideoClick = { video ->
                            activeVideo = video
                            isPlayerExpanded = true
                        },
                        onChannelClick = { url ->
                            currentScreen = Screen.Channel(url)
                        },
                        onNotificationsClick = {
                            currentScreen = Screen.Releases
                        }
                    )
                } else if (currentScreen is Screen.Shorts) {
                    ShortsScreen(
                        viewModel = viewModel,
                        innerPadding = innerPadding,
                        onChannelClick = { url ->
                            currentScreen = Screen.Channel(url)
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Not Implemented", color = Color.White)
                    }
                }
            }

            // Single PlayerScreen overlay — always mounted when a video is active.
            // isMini drives which layout (full vs mini bar) is shown internally.
            // This keeps the ExoPlayer alive across the mini/full transition.
            if (activeVideo != null) {
                PlayerScreen(
                    videoId = activeVideo!!.videoId,
                    videoTitle = activeVideo!!.title,
                    viewModel = viewModel,
                    repository = repository,
                    isMini = !isPlayerExpanded,
                    bottomNavHeight = innerPadding.calculateBottomPadding(),
                    onBack = { isPlayerExpanded = false },
                    onExpand = { isPlayerExpanded = true },
                    onClose = {
                        activeVideo = null
                        isPlayerExpanded = false
                    },
                    onChannelClick = { url ->
                        currentScreen = Screen.Channel(url)
                        isPlayerExpanded = false
                    },
                    onRelatedVideoClick = { video ->
                        activeVideo = video
                        isPlayerExpanded = true
                    }
                )
            }
        }
        
        if (showNameDialog) {
            var nameInput by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showNameDialog = false },
                title = { Text("Welcome!") },
                text = {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Enter your name") }
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        scope.launch {
                            repository.saveProfile(nameInput)
                        }
                        showNameDialog = false
                        currentScreen = Screen.Library
                    }) {
                        Text("Save")
                    }
                }
            )
        }
    }
}

sealed class Screen {
    object Home : Screen()
    object Shorts : Screen()
    data class Channel(val channelUrl: String) : Screen()
    object Library : Screen()
    data class PlaylistDetails(val playlist: com.darkk.youtube.data.Playlist) : Screen()
    object Subscriptions : Screen()
    object AllSubscriptions : Screen()
    object Downloads : Screen()
    object Releases : Screen()
    object History : Screen()
    object Settings : Screen()
}