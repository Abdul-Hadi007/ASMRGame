package com.example.asmrgame

import android.graphics.PointF
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun MakeoverGameScreen(
    onBackPressed: () -> Unit,
    initialScreen: GameScreen = GameScreen.HOME,
    debugShowAnchors: Boolean = false,
    previewPeelStage: Int? = null,
    previewToolAtAnchor: Pair<ToolType, PointF>? = null
) {
    val context = LocalContext.current
    remember {
        SpriteManager.load(context)
        true
    }

    var currentScreen by remember { mutableStateOf(initialScreen) }
    var soundEnabled by remember { mutableStateOf(true) }
    var musicEnabled by remember { mutableStateOf(true) }
    var vibrationEnabled by remember { mutableStateOf(true) }

    BackHandler {
        when (currentScreen) {
            GameScreen.HOME -> onBackPressed()
            GameScreen.LEVEL_SELECT, GameScreen.SETTINGS -> currentScreen = GameScreen.HOME
            GameScreen.GAMEPLAY -> currentScreen = GameScreen.HOME
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Shared aqua waves background across all screens
        Image(
            painter = painterResource(id = R.drawable.bg_aqua_waves),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        when (currentScreen) {
            GameScreen.HOME -> HomeScreen(
                onPlayClick = { currentScreen = GameScreen.GAMEPLAY },
                onLevelsClick = { currentScreen = GameScreen.LEVEL_SELECT },
                onSettingsClick = { currentScreen = GameScreen.SETTINGS }
            )
            GameScreen.LEVEL_SELECT -> LevelSelectScreen(
                onBack = { currentScreen = GameScreen.HOME },
                onPlayLevel1 = { currentScreen = GameScreen.GAMEPLAY }
            )
            GameScreen.SETTINGS -> SettingsScreen(
                soundEnabled = soundEnabled,
                musicEnabled = musicEnabled,
                vibrationEnabled = vibrationEnabled,
                onToggleSound = { soundEnabled = !soundEnabled },
                onToggleMusic = { musicEnabled = !musicEnabled },
                onToggleVibration = { vibrationEnabled = !vibrationEnabled },
                onDone = { currentScreen = GameScreen.HOME }
            )
            GameScreen.GAMEPLAY -> GameplayScreen(
                soundEnabled = soundEnabled,
                vibrationEnabled = vibrationEnabled,
                onToggleSound = { soundEnabled = !soundEnabled },
                onNavigateHome = { currentScreen = GameScreen.HOME },
                onNavigateLevels = { currentScreen = GameScreen.LEVEL_SELECT },
                debugShowAnchors = debugShowAnchors,
                previewPeelStage = previewPeelStage,
                previewToolAtAnchor = previewToolAtAnchor
            )
        }
    }
}

// =============================================================
// 1. HOME / TITLE SCREEN (01_home.png)
// =============================================================

@Composable
fun HomeScreen(
    onPlayClick: () -> Unit,
    onLevelsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Settings button top-right
        Image(
            painter = painterResource(id = R.drawable.btn_nav_settings),
            contentDescription = "Settings",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(56.dp)
                .clickable { onSettingsClick() }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Hanging wooden signboard
            Image(
                painter = painterResource(id = R.drawable.sign_home_logo_with_cords),
                contentDescription = "Fix the Toes",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .weight(0.28f, fill = false)
            )

            // Central circular hero preview
            Box(
                modifier = Modifier
                    .weight(0.48f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.play_home_hero_disc),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(0.88f)
                )
                Image(
                    painter = painterResource(id = R.drawable.foot_dirty),
                    contentDescription = "Foot Preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxHeight(0.86f)
                )
                Image(
                    painter = painterResource(id = R.drawable.tool_tray_wet_wipe),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = 24.dp, y = (-36).dp)
                        .size(72.dp)
                )
                Image(
                    painter = painterResource(id = R.drawable.tool_tray_nail_grinder),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = (-24).dp, y = 12.dp)
                        .size(72.dp)
                )
                Image(
                    painter = painterResource(id = R.drawable.fx_soap_bubble),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 26.dp, y = 40.dp)
                        .size(34.dp)
                )
                Image(
                    painter = painterResource(id = R.drawable.fx_golden_sparkle),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-32).dp, y = 26.dp)
                        .size(32.dp)
                )
            }

            // Play & Levels CTA Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.btn_play),
                    contentDescription = "Play",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(68.dp)
                        .clickable { onPlayClick() }
                )
                Spacer(modifier = Modifier.height(14.dp))
                Image(
                    painter = painterResource(id = R.drawable.btn_levels),
                    contentDescription = "Levels",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.54f)
                        .height(54.dp)
                        .clickable { onLevelsClick() }
                )
            }
        }
    }
}

// =============================================================
// 2. LEVEL SELECT SCREEN (02_level_select.png)
// =============================================================

@Composable
fun LevelSelectScreen(
    onBack: () -> Unit,
    onPlayLevel1: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.btn_nav_back),
                contentDescription = "Back",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(52.dp)
                    .clickable { onBack() }
            )
            Image(
                painter = painterResource(id = R.drawable.sign_levels_title),
                contentDescription = "Select Level",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .height(54.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Unlocked Level 01 Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(990f / 729f)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.frame_unlocked_level_card),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight(0.88f)
                                .aspectRatio(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.play_level_preview_disc),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            Image(
                                painter = painterResource(id = R.drawable.foot_dirty),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(0.82f)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "LEVEL 01",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2B6F6D)
                            )
                            Text(
                                text = "Muddy Toes",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF23393D)
                            )
                            Text(
                                text = "Wipe mud, pluck splinters, grind nails & peel mask",
                                fontSize = 12.sp,
                                color = Color(0xFF5C7173),
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                repeat(3) {
                                    Image(
                                        painter = painterResource(id = R.drawable.status_star_earned),
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }

                    Image(
                        painter = painterResource(id = R.drawable.btn_play_level_1),
                        contentDescription = "Play Level 1",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable { onPlayLevel1() }
                    )
                }
            }

            // Locked Level Cards
            LockedLevelRow(levelNum = "02", title = "Rough Edges", req = "Complete Level 01 to unlock")
            LockedLevelRow(levelNum = "03", title = "Trail Trouble", req = "Complete Level 02 to unlock")
            LockedLevelRow(levelNum = "04", title = "The Big Cleanup", req = "Complete Level 03 to unlock")
        }
    }
}

@Composable
private fun LockedLevelRow(levelNum: String, title: String, req: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(990f / 303f)
    ) {
        Image(
            painter = painterResource(id = R.drawable.frame_locked_level_card),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(58.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.frame_locked_level_number_tile),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    text = levelNum,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF7B8E90)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF42595B)
                )
                Text(
                    text = req,
                    fontSize = 12.sp,
                    color = Color(0xFF6B8183)
                )
            }
            Image(
                painter = painterResource(id = R.drawable.ic_lock),
                contentDescription = "Locked",
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

// =============================================================
// 3. SETTINGS SCREEN (07_settings.png)
// =============================================================

@Composable
fun SettingsScreen(
    soundEnabled: Boolean,
    musicEnabled: Boolean,
    vibrationEnabled: Boolean,
    onToggleSound: () -> Unit,
    onToggleMusic: () -> Unit,
    onToggleVibration: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.btn_nav_back),
                contentDescription = "Back",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(52.dp)
                    .clickable { onDone() }
            )
            Image(
                painter = painterResource(id = R.drawable.sign_settings_title),
                contentDescription = "Settings",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .height(54.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .aspectRatio(990f / 645f)
        ) {
            Image(
                painter = painterResource(id = R.drawable.frame_settings_panel),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                SettingsToggleRow(label = "Sound Effects", isOn = soundEnabled, onToggle = onToggleSound)
                SettingsToggleRow(label = "Music", isOn = musicEnabled, onToggle = onToggleMusic)
                SettingsToggleRow(label = "Vibration", isOn = vibrationEnabled, onToggle = onToggleVibration)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Image(
            painter = painterResource(id = R.drawable.btn_done),
            contentDescription = "Done",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth(0.68f)
                .height(60.dp)
                .clickable { onDone() }
        )

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun SettingsToggleRow(label: String, isOn: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clickable { onToggle() }
    ) {
        Image(
            painter = painterResource(id = R.drawable.frame_settings_row),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF284245)
            )
            Image(
                painter = painterResource(id = if (isOn) R.drawable.status_toggle_on else R.drawable.status_toggle_off),
                contentDescription = if (isOn) "On" else "Off",
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(34.dp)
            )
        }
    }
}

// =============================================================
// 4. GAMEPLAY SCREEN (03_gameplay.png)
// =============================================================

@Composable
fun GameplayScreen(
    soundEnabled: Boolean,
    vibrationEnabled: Boolean,
    onToggleSound: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateLevels: () -> Unit,
    debugShowAnchors: Boolean = false,
    previewPeelStage: Int? = null,
    previewToolAtAnchor: Pair<ToolType, PointF>? = null
) {
    var makeoverViewRef by remember { mutableStateOf<MakeoverView?>(null) }
    var currentStep by remember { mutableStateOf(GameStep.CLEAN_DIRT) }
    var activeTool by remember { mutableStateOf<ToolType?>(ToolType.WET_WIPE) }
    var stepProgress by remember { mutableFloatStateOf(0f) }

    var secondsLeft by remember { mutableIntStateOf(201) } // 03:21 as in mockup
    var isPaused by remember { mutableStateOf(false) }
    var showAddTimeAnim by remember { mutableStateOf(false) }
    var isHintActive by remember { mutableStateOf(false) }
    var isLevelComplete by remember { mutableStateOf(false) }
    var isTimesUp by remember { mutableStateOf(false) }

    LaunchedEffect(soundEnabled, vibrationEnabled, makeoverViewRef) {
        makeoverViewRef?.soundManager?.isSoundEnabled = soundEnabled
        makeoverViewRef?.soundManager?.isVibrationEnabled = vibrationEnabled
    }

    LaunchedEffect(isPaused, isLevelComplete, isTimesUp) {
        while (!isPaused && !isLevelComplete && !isTimesUp && secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
            if (secondsLeft == 0) {
                isTimesUp = true
            }
        }
    }

    LaunchedEffect(isHintActive) {
        if (isHintActive) {
            delay(4000L)
            isHintActive = false
        }
    }

    LaunchedEffect(showAddTimeAnim) {
        if (showAddTimeAnim) {
            delay(1200L)
            showAddTimeAnim = false
        }
    }

    val minutes = secondsLeft / 60
    val secs = secondsLeft % 60
    val formattedTime = String.format(Locale.US, "%02d:%02d", minutes, secs)

    val infiniteTransition = rememberInfiniteTransition(label = "hintPulse")
    val hintScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hintScale"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compactMode = maxHeight < 700.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = if (compactMode) 4.dp else 8.dp, bottom = if (compactMode) 6.dp else 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- Top Navigation Bar: Back, Wooden Sign, Pause ---
            Row(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.btn_nav_back),
                    contentDescription = "Back",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(if (compactMode) 42.dp else 48.dp)
                        .clickable { isPaused = true }
                )

                Image(
                    painter = painterResource(id = R.drawable.sign_gameplay_title),
                    contentDescription = "Fix the Toes",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(if (compactMode) 42.dp else 48.dp)
                )

                Image(
                    painter = painterResource(id = R.drawable.btn_nav_pause),
                    contentDescription = "Pause",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(if (compactMode) 42.dp else 48.dp)
                        .clickable { isPaused = true }
                )
            }

            Spacer(modifier = Modifier.height(if (compactMode) 4.dp else 6.dp))

            // --- Step Task Banner Card ---
            GameplayTaskBanner(
                step = currentStep,
                stepProgress = stepProgress,
                timeString = formattedTime,
                isLowTime = secondsLeft < 30,
                showAddTimeAnim = showAddTimeAnim,
                compact = compactMode
            )

            // --- Center Treatment Mat & Interactive Foot Canvas ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        MakeoverView(ctx).apply {
                            this.showAnchorOverlay = debugShowAnchors
                            this.previewPeelStage = previewPeelStage
                            this.previewToolAtAnchor = previewToolAtAnchor
                            this.soundManager.isSoundEnabled = soundEnabled
                            this.soundManager.isVibrationEnabled = vibrationEnabled
                            onStepProgressChanged = { step, prog ->
                                currentStep = step
                                stepProgress = prog
                            }
                            onStepCompletedListener = {
                                currentStep = this.currentStep
                                activeTool = this.activeTool
                                stepProgress = 0f
                            }
                            onToolDeselectRequested = {
                                activeTool = null
                            }
                            onLevelCompletedListener = {
                                isLevelComplete = true
                            }
                            makeoverViewRef = this
                        }
                    },
                    update = { view ->
                        view.showAnchorOverlay = debugShowAnchors
                        view.previewPeelStage = previewPeelStage
                        view.previewToolAtAnchor = previewToolAtAnchor
                        makeoverViewRef = view
                    }
                )
            }

            // --- Bottom Tool Tray ---
            GameplayToolTray(
                selectedTool = activeTool,
                recommendedTool = currentStep.recommendedTool,
                isHintActive = isHintActive,
                hintScale = hintScale,
                compact = compactMode,
                onSelectTool = { tool ->
                    if (activeTool == tool) {
                        activeTool = null
                        makeoverViewRef?.selectTool(null)
                    } else {
                        activeTool = tool
                        makeoverViewRef?.selectTool(tool)
                    }
                    if (isHintActive) isHintActive = false
                }
            )

            Spacer(modifier = Modifier.height(if (compactMode) 6.dp else 8.dp))

            // --- Bottom Action Buttons: Hint & + Time ---
            Row(
                modifier = Modifier
                    .widthIn(max = 540.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.btn_hint),
                    contentDescription = "Hint",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .weight(1f)
                        .height(if (compactMode) 44.dp else 52.dp)
                        .clickable {
                            isHintActive = true
                            makeoverViewRef?.showHint()
                        }
                )
                Image(
                    painter = painterResource(id = R.drawable.btn_add_time),
                    contentDescription = "+ Time",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .weight(1f)
                        .height(if (compactMode) 44.dp else 52.dp)
                        .clickable {
                            secondsLeft += 30
                            showAddTimeAnim = true
                            makeoverViewRef?.soundManager?.playChime()
                        }
                )
            }
        }

        // --- Modals ---
        if (isPaused) {
            PauseDialog(
                soundEnabled = soundEnabled,
                onToggleSound = onToggleSound,
                onResume = { isPaused = false },
                onRestart = {
                    isPaused = false
                    secondsLeft = 201
                    stepProgress = 0f
                    currentStep = GameStep.CLEAN_DIRT
                    activeTool = ToolType.WET_WIPE
                    makeoverViewRef?.resetLevel()
                },
                onLevelSelect = {
                    isPaused = false
                    onNavigateLevels()
                },
                onExitHome = {
                    isPaused = false
                    onNavigateHome()
                }
            )
        }

        if (isTimesUp) {
            TimesUpDialog(
                onAdd30s = {
                    secondsLeft = 30
                    isTimesUp = false
                    makeoverViewRef?.soundManager?.playChime()
                },
                onTryAgain = {
                    isTimesUp = false
                    secondsLeft = 201
                    stepProgress = 0f
                    currentStep = GameStep.CLEAN_DIRT
                    activeTool = ToolType.WET_WIPE
                    makeoverViewRef?.resetLevel()
                },
                onLevelSelect = {
                    isTimesUp = false
                    onNavigateLevels()
                }
            )
        }

        if (isLevelComplete) {
            LevelCompleteModal(
                onNextLevel = {
                    isLevelComplete = false
                    secondsLeft = 201
                    stepProgress = 0f
                    currentStep = GameStep.CLEAN_DIRT
                    activeTool = ToolType.WET_WIPE
                    makeoverViewRef?.resetLevel()
                },
                onLevelSelect = {
                    isLevelComplete = false
                    onNavigateLevels()
                }
            )
        }
    }
}

@Composable
private fun GameplayTaskBanner(
    step: GameStep,
    stepProgress: Float,
    timeString: String,
    isLowTime: Boolean,
    showAddTimeAnim: Boolean,
    compact: Boolean
) {
    Box(
        modifier = Modifier
            .widthIn(max = 540.dp)
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(if (compact) 84.dp else 94.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.frame_task_panel),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = step.title,
                        fontSize = if (compact) 16.sp else 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF23393D),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = step.subtitle,
                        fontSize = if (compact) 11.sp else 12.sp,
                        color = Color(0xFF536B6E),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Timer pill
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier.height(if (compact) 30.dp else 34.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.status_timer_plate),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.matchParentSize()
                        )
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_clock),
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = timeString,
                                fontSize = if (compact) 13.sp else 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isLowTime) Color(0xFFD32F2F) else Color(0xFF4B3723)
                            )
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showAddTimeAnim,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                        modifier = Modifier.offset(y = (-24).dp)
                    ) {
                        Text(
                            text = "+30s!",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }

            // Bottom step progress row: "Step X of 4" + 4 progress segments
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Step ${step.stepNumber} of 4",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF284245)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (seg in 1..4) {
                        val filled = seg < step.stepNumber ||
                            (seg == step.stepNumber && stepProgress > 0.1f) ||
                            step == GameStep.COMPLETED
                        Image(
                            painter = painterResource(
                                id = if (filled) R.drawable.status_progress_filled else R.drawable.status_progress_empty
                            ),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GameplayToolTray(
    selectedTool: ToolType?,
    recommendedTool: ToolType,
    isHintActive: Boolean,
    hintScale: Float,
    compact: Boolean,
    onSelectTool: (ToolType) -> Unit
) {
    Box(
        modifier = Modifier
            .widthIn(max = 540.dp)
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(if (compact) 86.dp else 98.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.frame_tool_tray),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tools = listOf(
                ToolType.NAIL_GRINDER,
                ToolType.WET_WIPE,
                ToolType.TWEEZERS,
                ToolType.CREAM_TUBE
            )

            for (tool in tools) {
                val isSelected = (tool == selectedTool)
                val isRecommended = (tool == recommendedTool && isHintActive)

                val iconRes = when (tool) {
                    ToolType.NAIL_GRINDER -> R.drawable.tool_tray_nail_grinder
                    ToolType.WET_WIPE -> R.drawable.tool_tray_wet_wipe
                    ToolType.TWEEZERS -> R.drawable.tool_tray_tweezers
                    ToolType.CREAM_TUBE -> R.drawable.tool_tray_cream_tube
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .scale(if (isRecommended) hintScale else 1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelectTool(tool) }
                ) {
                    Image(
                        painter = painterResource(
                            id = if (isSelected) R.drawable.frame_tool_tile_selected else R.drawable.frame_tool_tile_default
                        ),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isSelected) {
                        Image(
                            painter = painterResource(id = R.drawable.status_selected_check_badge),
                            contentDescription = "Selected",
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 3.dp, end = 3.dp)
                                .size(16.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Image(
                            painter = painterResource(id = iconRes),
                            contentDescription = tool.displayName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(0.75f)
                        )
                        Text(
                            text = tool.displayName,
                            fontSize = if (compact) 9.sp else 10.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (isSelected) Color(0xFF1F6E6D) else Color(0xFF284245),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// =============================================================
// 5. PAUSE DIALOG (04_pause.png)
// =============================================================

@Composable
fun PauseDialog(
    soundEnabled: Boolean,
    onToggleSound: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onLevelSelect: () -> Unit,
    onExitHome: () -> Unit
) {
    Dialog(
        onDismissRequest = onResume,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth(0.86f)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.frame_pause_dialog),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .matchParentSize()
                        .padding(top = 26.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.sign_pause_title),
                        contentDescription = "Paused",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(58.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Level 1 · Muddy Toes",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3E5A5E)
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Image(
                        painter = painterResource(id = R.drawable.btn_resume),
                        contentDescription = "Resume",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clickable { onResume() }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Image(
                        painter = painterResource(id = R.drawable.btn_restart),
                        contentDescription = "Restart",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clickable { onRestart() }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Image(
                        painter = painterResource(id = R.drawable.btn_level_select_pause),
                        contentDescription = "Level Select",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clickable { onLevelSelect() }
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable { onToggleSound() }
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.frame_pause_sound_row),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_sound),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sound",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF284245)
                                )
                            }
                            Image(
                                painter = painterResource(
                                    id = if (soundEnabled) R.drawable.status_toggle_on else R.drawable.status_toggle_off
                                ),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.height(30.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Main Menu",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF426265),
                        modifier = Modifier
                            .clickable { onExitHome() }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

// =============================================================
// 6. TIME'S UP DIALOG (06_times_up.png)
// =============================================================

@Composable
fun TimesUpDialog(
    onAdd30s: () -> Unit,
    onTryAgain: () -> Unit,
    onLevelSelect: () -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth(0.86f)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.frame_times_up_dialog),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .matchParentSize()
                        .padding(top = 26.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.sign_times_up_title),
                        contentDescription = "Time's Up",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(58.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Image(
                        painter = painterResource(id = R.drawable.status_timeout_clock_medallion),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(100.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Keep your cleaning progress",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF3A281A),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Watch an ad for 30 more seconds.",
                        fontSize = 13.sp,
                        color = Color(0xFF5D6D6F),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Image(
                        painter = painterResource(id = R.drawable.btn_add_30s),
                        contentDescription = "Add 30s",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clickable { onAdd30s() }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Image(
                        painter = painterResource(id = R.drawable.btn_try_again),
                        contentDescription = "Try Again",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clickable { onTryAgain() }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Level Select",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF284D4E),
                        modifier = Modifier.clickable { onLevelSelect() }
                    )
                }
            }
        }
    }
}

// =============================================================
// 7. LEVEL COMPLETE MODAL (05_level_complete.png)
// =============================================================

@Composable
fun LevelCompleteModal(
    onNextLevel: () -> Unit,
    onLevelSelect: () -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.bg_aqua_waves),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 520.dp)
                    .align(Alignment.Center)
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Image(
                    painter = painterResource(id = R.drawable.sign_complete_title),
                    contentDescription = "Level Complete!",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(64.dp)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.play_completion_hero_disc),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(0.84f)
                    )
                    Image(
                        painter = painterResource(id = R.drawable.foot_clean_happy),
                        contentDescription = "Happy Clean Foot",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxHeight(0.84f)
                    )
                }

                Image(
                    painter = painterResource(id = R.drawable.status_three_star_reward),
                    contentDescription = "3 Stars",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(64.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(78.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.frame_completion_summary),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Muddy Toes",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF23393D)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_step_completion_check),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "4 of 4 steps complete",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF4F6B6E)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Image(
                    painter = painterResource(id = R.drawable.btn_next_level),
                    contentDescription = "Next Level",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .height(58.dp)
                        .clickable { onNextLevel() }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Image(
                    painter = painterResource(id = R.drawable.btn_level_select_complete),
                    contentDescription = "Level Select",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.60f)
                        .height(48.dp)
                        .clickable { onLevelSelect() }
                )
            }
        }
    }
}
