package com.unshoo.pixelmusic.presentation.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unshoo.pixelmusic.presentation.components.MiniPlayerHeight
import com.unshoo.pixelmusic.presentation.components.resolveNavBarOccupiedHeight
import com.unshoo.pixelmusic.presentation.navigation.Screen
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.presentation.navigation.navigateSafelyReplacing
import com.unshoo.pixelmusic.presentation.viewmodel.SmartMixViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.SmartMixUiState
import com.unshoo.pixelmusic.presentation.viewmodel.LastFmTrack
import com.unshoo.pixelmusic.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartMixScreen(
    navController: NavController,
    playerViewModel: com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel,
    viewModel: SmartMixViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Observe navigation and toast events
    LaunchedEffect(uiState.generatedPlaylistId) {
        uiState.generatedPlaylistId?.let { playlistId ->
            viewModel.clearGeneratedPlaylistId()
            // Navigate replacing the SmartMix screen so back goes to Explore
            navController.navigateSafelyReplacing(Screen.PlaylistDetail.createRoute(playlistId), Screen.SmartMix.route)
        }
    }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val backgroundBrush = remember(surfaceColor, primaryColor) {
        Brush.verticalGradient(
            colors = listOf(
                primaryColor.copy(alpha = 0.12f),
                surfaceColor.copy(alpha = 0.8f),
                surfaceColor
            ),
            endY = 1200f
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Smart Mix",
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(innerPadding)
        ) {
            if (!uiState.isLastfmLoggedIn) {
                LastfmLoggedOutState(
                    onConnectClick = {
                        navController.navigate(Screen.Accounts.route)
                    }
                )
            } else {
                SmartMixConfigurator(
                    uiState = uiState,
                    viewModel = viewModel,
                    playerViewModel = playerViewModel
                )
            }

            if (uiState.isGenerating) {
                GenerationProgressDialog(progress = uiState.generationProgress)
            }
        }
    }
}

@Composable
private fun LastfmLoggedOutState(
    onConnectClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val shape = remember { AbsoluteSmoothCornerShape(24.dp, 60) }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Connect Last.fm Account",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Smart Mix uses your Last.fm scrobble history, top tracks, recent listens, and favorite genres to build highly personalized discovery mixes for you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onConnectClick,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Link, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Go to Linked Accounts", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun SmartMixConfigurator(
    uiState: SmartMixUiState,
    viewModel: SmartMixViewModel,
    playerViewModel: PlayerViewModel
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val playerStableState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val isPlayerActive = playerStableState.currentSong != null

    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)

    val fabAreaHeight = 56.dp + 20.dp + (if (isPlayerActive) MiniPlayerHeight else 0.dp) + bottomBarHeightDp
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    // Unique per-mode colors matching app's Expressive Settings design language
    val modeColors: Map<String, Pair<Color, Color>> = if (isDark) mapOf(
        "top"             to (Color(0xFF4A3F00) to Color(0xFFFFE082)),
        "recent"          to (Color(0xFF004A77) to Color(0xFFC2E7FF)),
        "similar-tracks"  to (Color(0xFF005049) to Color(0xFF88FFD9)),
        "similar-artists" to (Color(0xFF7D5260) to Color(0xFFFFD8E4)),
        "tag"             to (Color(0xFF3E4C63) to Color(0xFFD7E3FF)),
        "mix"             to (Color(0xFF004F58) to Color(0xFF88FAFF)),
        "recommendations" to (Color(0xFF6E1B1B) to Color(0xFFFFDAD9)),
        "library"         to (Color(0xFF324F34) to Color(0xFFCBEFD0))
    ) else mapOf(
        "top"             to (Color(0xFFFFF8E1) to Color(0xFFE65100)),
        "recent"          to (Color(0xFFD7E3FF) to Color(0xFF005AC1)),
        "similar-tracks"  to (Color(0xFF88FFD9) to Color(0xFF005049)),
        "similar-artists" to (Color(0xFFFFD8E4) to Color(0xFF631835)),
        "tag"             to (Color(0xFFD7E3FF) to Color(0xFF253347)),
        "mix"             to (Color(0xFFCCE8EA) to Color(0xFF004F58)),
        "recommendations" to (Color(0xFFFFDAD9) to Color(0xFF6E1B1B)),
        "library"         to (Color(0xFFCBEFD0) to Color(0xFF042106))
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = fabAreaHeight + 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 1. Slider Card
            item {
                TrackCountSliderCard(
                    count = uiState.trackCount,
                    onCountChange = { viewModel.setTrackCount(it) }
                )
            }

            // Section heading
            item {
                Text(
                    text = "Generation Mode",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.8.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
                )
            }

            // 2. Mode items — same expressive list style as Settings
            val modes = listOf(
                ModeItemData("top",             "Top Tracks",       "Your most played tracks of all time",      Icons.Rounded.Leaderboard),
                ModeItemData("recent",          "Recent Tracks",    "What you've been listening to lately",     Icons.Rounded.History),
                ModeItemData("similar-tracks",  "Similar Tracks",   "Tracks similar to one you love",           Icons.Rounded.MusicNote),
                ModeItemData("similar-artists", "Similar Artists",  "Discover artists like your favourites",    Icons.Rounded.People),
                ModeItemData("tag",             "By Tag / Genre",   "Browse by genre like rock, lofi, jazz",    Icons.Rounded.Sell),
                ModeItemData("mix",             "My Mix",           "Smart blend of top, recent & similar",     Icons.Rounded.Shuffle),
                ModeItemData("recommendations", "My Recommendations","30 fresh tracks tailored for you",       Icons.Rounded.AutoAwesome),
                ModeItemData("library",         "My Library",       "Re-discover the sounds of your past",      Icons.Rounded.LibraryMusic)
            )

            val total = modes.size
            modes.forEachIndexed { idx, mode ->
                item(key = mode.id) {
                    val isSelected = uiState.selectedMode == mode.id
                    val colors = modeColors[mode.id]!!

                    // Settings-style grouped shape: large radius top/bottom, small radius middle
                    val itemShape = when {
                        total == 1 -> RoundedCornerShape(24.dp)
                        idx == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
                        idx == total - 1 -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                        else -> RoundedCornerShape(6.dp)
                    }

                    Surface(
                        onClick = {
                            if (isSelected) viewModel.setSelectedMode(null)
                            else viewModel.setSelectedMode(mode.id)
                        },
                        shape = itemShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainer,
                        border = if (isSelected)
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                // Solid colored circle icon — same as ExpressiveCategoryItem
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else colors.first
                                        )
                                ) {
                                    Icon(
                                        imageVector = mode.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                               else colors.second
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mode.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = GoogleSansRounded,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = mode.desc,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = (if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                 else MaterialTheme.colorScheme.onSurface).copy(alpha = 0.65f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Animated chevron / collapse icon
                                val iconRotation by animateFloatAsState(
                                    targetValue = if (isSelected) 90f else 0f,
                                    animationSpec = tween(200),
                                    label = "chevron_rotation"
                                )
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .graphicsLayer { rotationZ = iconRotation },
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Inline expanded options
                            AnimatedVisibility(
                                visible = isSelected,
                                enter = expandVertically(tween(220)) + fadeIn(tween(220)),
                                exit  = shrinkVertically(tween(180)) + fadeOut(tween(180))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 84.dp, end = 16.dp, bottom = 16.dp)
                                ) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )
                                    when (mode.id) {
                                        "top", "library" -> PeriodSelectionSection(
                                            selectedPeriod = uiState.timePeriod,
                                            onPeriodSelect = { viewModel.setTimePeriod(it) }
                                        )
                                        "recent" -> Text(
                                            text = "Fetches what you have scrobbled recently on Last.fm.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        "similar-tracks" -> SimilarTracksInputs(
                                            uiState = uiState, viewModel = viewModel,
                                            keyboardController = keyboardController
                                        )
                                        "similar-artists" -> SimilarArtistsInputs(
                                            uiState = uiState, viewModel = viewModel,
                                            keyboardController = keyboardController
                                        )
                                        "tag" -> TagInputs(
                                            uiState = uiState, viewModel = viewModel,
                                            keyboardController = keyboardController
                                        )
                                        "mix" -> Text(
                                            text = "A smart blend of your top tracks, recent plays, and tracks similar to your favorite artists.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        "recommendations" -> Text(
                                            text = "Uses your Last.fm profile to score, filter, and balance familiar sounds with new discoveries.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Gap between non-adjacent items
                    if (idx < total - 1) Spacer(Modifier.height(2.dp))
                }
            }
        }

        // FAB — always above mini player + nav bar
        val isGenerateEnabled = uiState.selectedMode != null && when (uiState.selectedMode) {
            "similar-tracks"  -> uiState.seedTrackName.isNotBlank() && uiState.seedArtistName.isNotBlank()
            "similar-artists" -> uiState.seedArtistInput.isNotBlank()
            "tag"             -> uiState.tagInput.isNotBlank()
            else              -> true
        }

        ExtendedFloatingActionButton(
            onClick = { keyboardController?.hide(); viewModel.generatePlaylist() },
            expanded = isGenerateEnabled,
            icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
            text = {
                Text(
                    text = "Generate Mix",
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GoogleSansRounded
                )
            },
            containerColor = if (isGenerateEnabled) MaterialTheme.colorScheme.primary
                             else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isGenerateEnabled) MaterialTheme.colorScheme.onPrimary
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (if (isPlayerActive) MiniPlayerHeight else 0.dp) + bottomBarHeightDp + 12.dp)
        )
    }
}

@Composable
private fun TrackCountSliderCard(
    count: Int,
    onCountChange: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Track Count",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "How many songs to generate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
                // Pill badge matching app's chip style
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Slider(
                value = count.toFloat(),
                onValueChange = { onCountChange(it.toInt()) },
                valueRange = 5f..35f,
                steps = 30,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("5", "15", "25", "35").forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodSelectionSection(
    selectedPeriod: String,
    onPeriodSelect: (String) -> Unit
) {
    val periods = listOf(
        "overall" to "All Time",
        "12month" to "12 Months",
        "6month" to "6 Months",
        "3month" to "3 Months",
        "1month" to "1 Month",
        "7day" to "7 Days"
    )

    Text(
        text = "Select Time Period",
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(periods) { (id, label) ->
            val active = selectedPeriod == id
            FilterChip(
                selected = active,
                onClick = { onPeriodSelect(id) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    labelColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(12.dp),
                border = null
            )
        }
    }
}

@Composable
private fun SimilarTracksInputs(
    uiState: SmartMixUiState,
    viewModel: SmartMixViewModel,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = uiState.seedTrackName,
            onValueChange = { viewModel.setSeedTrackName(it) },
            label = { Text("Seed Track Name") },
            placeholder = { Text("Enter track name...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                keyboardController?.hide()
                viewModel.searchSeedTrack()
            }),
            trailingIcon = {
                IconButton(onClick = {
                    keyboardController?.hide()
                    viewModel.searchSeedTrack()
                }) {
                    Icon(Icons.Rounded.Search, contentDescription = "Search")
                }
            }
        )

        OutlinedTextField(
            value = uiState.seedArtistName,
            onValueChange = { viewModel.setSeedArtistName(it) },
            label = { Text("Seed Artist Name (Optional)") },
            placeholder = { Text("Enter artist name...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Button(
            onClick = {
                keyboardController?.hide()
                viewModel.loadTopTracksForSeed()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.LibraryMusic, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Pick From My Top Tracks")
        }

        if (uiState.isSearchingTopSeeds || uiState.isSearchingSeed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }

        if (uiState.searchTrackResults.isNotEmpty()) {
            Text(
                text = "Search Results",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (track in uiState.searchTrackResults) {
                    SeedSearchResultItem(
                        title = track.name,
                        subtitle = track.artist,
                        onClick = {
                            viewModel.setSeedTrackName(track.name)
                            viewModel.setSeedArtistName(track.artist)
                        }
                    )
                }
            }
        }

        if (uiState.topTracksForSeed.isNotEmpty()) {
            Text(
                text = "My Top Tracks",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (track in uiState.topTracksForSeed) {
                    SeedSearchResultItem(
                        title = track.name,
                        subtitle = track.artist,
                        onClick = {
                            viewModel.setSeedTrackName(track.name)
                            viewModel.setSeedArtistName(track.artist)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SimilarArtistsInputs(
    uiState: SmartMixUiState,
    viewModel: SmartMixViewModel,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = uiState.seedArtistInput,
            onValueChange = { viewModel.setSeedArtistInput(it) },
            label = { Text("Seed Artist Name") },
            placeholder = { Text("Enter artist name...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                keyboardController?.hide()
                viewModel.searchSeedArtist()
            }),
            trailingIcon = {
                IconButton(onClick = {
                    keyboardController?.hide()
                    viewModel.searchSeedArtist()
                }) {
                    Icon(Icons.Rounded.Search, contentDescription = "Search")
                }
            }
        )

        Button(
            onClick = {
                keyboardController?.hide()
                viewModel.loadTopArtistsForSeed()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.People, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Pick From My Top Artists")
        }

        if (uiState.isSearchingTopSeeds || uiState.isSearchingSeed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }

        if (uiState.searchArtistResults.isNotEmpty()) {
            Text(
                text = "Search Results",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (artist in uiState.searchArtistResults) {
                    SeedSearchResultItem(
                        title = artist,
                        subtitle = "Artist",
                        onClick = {
                            viewModel.setSeedArtistInput(artist)
                        }
                    )
                }
            }
        }

        if (uiState.topArtistsForSeed.isNotEmpty()) {
            Text(
                text = "My Top Artists",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (artist in uiState.topArtistsForSeed) {
                    SeedSearchResultItem(
                        title = artist,
                        subtitle = "Artist",
                        onClick = {
                            viewModel.setSeedArtistInput(artist)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TagInputs(
    uiState: SmartMixUiState,
    viewModel: SmartMixViewModel,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?
) {
    val suggestions = listOf("pop", "rock", "hip-hop", "electronic", "jazz", "lofi", "metal", "indie", "classical", "r&b", "ambient", "punk")

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = uiState.tagInput,
            onValueChange = { viewModel.setTagInput(it) },
            label = { Text("Genre / Tag") },
            placeholder = { Text("e.g. lofi, rock...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
        )

        Text(
            text = "Suggested Genres",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            mainAxisSpacing = 8.dp,
            crossAxisSpacing = 8.dp
        ) {
            for (tag in suggestions) {
                val active = uiState.tagInput.lowercase() == tag
                FilterChip(
                    selected = active,
                    onClick = { viewModel.setTagInput(tag) },
                    label = { Text(tag) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = null
                )
            }
        }
    }
}

@Composable
private fun SeedSearchResultItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun GenerationProgressDialog(
    progress: String
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(280.dp)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Generating Mix",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = progress,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// Custom flow row layout
@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    mainAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    crossAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val layoutWidth = constraints.maxWidth

        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentRowWidth = 0

        placeables.forEach { placeable ->
            if (currentRowWidth + placeable.width > layoutWidth && currentRow.isNotEmpty()) {
                rows.add(currentRow)
                currentRow = mutableListOf()
                currentRowWidth = 0
            }
            currentRow.add(placeable)
            currentRowWidth += placeable.width + mainAxisSpacing.roundToPx()
        }
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
        }

        var height = 0
        rows.forEachIndexed { index, row ->
            val rowHeight = row.maxOfOrNull { it.height } ?: 0
            height += rowHeight
            if (index < rows.size - 1) {
                height += crossAxisSpacing.roundToPx()
            }
        }

        layout(layoutWidth, height.coerceAtLeast(0)) {
            var y = 0
            rows.forEach { row ->
                var x = 0
                val rowHeight = row.maxOfOrNull { it.height } ?: 0
                row.forEach { placeable ->
                    placeable.placeRelative(x, y + (rowHeight - placeable.height) / 2)
                    x += placeable.width + mainAxisSpacing.roundToPx()
                }
                y += rowHeight + crossAxisSpacing.roundToPx()
            }
        }
    }
}

private data class ModeItemData(
    val id: String,
    val title: String,
    val desc: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
