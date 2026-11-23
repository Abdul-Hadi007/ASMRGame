package com.example.asmrgame

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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay

@Composable
fun MakeoverGameScreen(
    onBackPressed: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.remember {
        SpriteManager.load(context)
        true
    }

    // --- State ---
    var makeoverViewRef by remember { mutableStateOf<MakeoverView?>(null) }
    var currentStep by remember { mutableStateOf(GameStep.CLEAN_DIRT) }
    var activeTool by remember { mutableStateOf<ToolType?>(ToolType.WET_WIPE) }
    var stepProgress by remember { mutableFloatStateOf(0f) }

    // Live countdown timer starting at 220 seconds
    var secondsLeft by remember { mutableIntStateOf(220) }
    var isTimerRunning by remember { mutableStateOf(true) }
    var showAddTimeAnim by remember { mutableStateOf(false) }

    // Polish & dialogs
    var isHintActive by remember { mutableStateOf(false) }
    var isLevelComplete by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    // Countdown Timer Coroutine
    LaunchedEffect(isTimerRunning) {
        while (isTimerRunning && secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        }
    }

    // Auto-dismiss hint highlight after 4 seconds
    LaunchedEffect(isHintActive) {
        if (isHintActive) {
            delay(4000L)
            isHintActive = false
        }
    }

    // Format mm:ss
    val minutes = secondsLeft / 60
    val secs = secondsLeft % 60
    val formattedTime = java.util.Locale.US.let { String.format(it, "%02d:%02d", minutes, secs) }

    // Pulsing transition for hint animation on recommended tool
    val infiniteTransition = rememberInfiniteTransition(label = "hintPulse")
    val hintScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hintScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFF7F2),
                        Color(0xFFFDE8E1),
                        Color(0xFFFAD7CE)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- 1. TOP HEADER: Objective Banner & Countdown Timer ---
            TopHeaderSection(
                step = currentStep,
                progress = stepProgress,
                timeString = formattedTime,
                isLowTime = secondsLeft < 30,
                showAddTimeAnim = showAddTimeAnim
            )

            // --- 2. CENTER: Makeover Custom Canvas View ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        MakeoverView(ctx).apply {
                            onStepProgressChanged = { step, prog ->
                                currentStep = step
                                stepProgress = prog
                            }
                            onStepCompletedListener = { completedStep ->
                                currentStep = this.currentStep
                                activeTool = this.activeTool
                                stepProgress = 0f
                            }
                            onToolDeselectRequested = {
                                activeTool = null
                            }
                            onLevelCompletedListener = {
                                isLevelComplete = true
                                isTimerRunning = false
                            }
                            makeoverViewRef = this
                        }
                    },
                    update = { view ->
                        makeoverViewRef = view
                    }
                )
            }

            // --- 3. BOTTOM: Horizontal Tool Tray with 4 Draggable/Selectable Tools ---
            ToolTray(
                selectedTool = activeTool,
                recommendedTool = currentStep.recommendedTool,
                isHintActive = isHintActive,
                hintScale = hintScale,
                onSelectTool = { tool ->
                    if (activeTool == tool) {
                        // Selecting it again deselects it!
                        activeTool = null
                        makeoverViewRef?.selectTool(null)
                    } else {
                        activeTool = tool
                        makeoverViewRef?.selectTool(tool)
                    }
                    if (isHintActive) isHintActive = false
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // --- 4. BOTTOM ACTION BUTTONS: Back, Hint, Add Time ---
            BottomActionButtons(
                onBackClicked = { showExitDialog = true },
                onHintClicked = {
                    isHintActive = true
                    makeoverViewRef?.showHint()
                },
                onAddTimeClicked = {
                    secondsLeft += 30
                    showAddTimeAnim = true
                    makeoverViewRef?.soundManager?.playChime()
                }
            )
        }

        // Reset "+30s!" animation flag after delay
        LaunchedEffect(showAddTimeAnim) {
            if (showAddTimeAnim) {
                delay(1200L)
                showAddTimeAnim = false
            }
        }

        // --- LEVEL COMPLETE POPUP ---
        if (isLevelComplete) {
            LevelCompleteDialog(
                secondsLeft = secondsLeft,
                onPlayAgain = {
                    isLevelComplete = false
                    secondsLeft = 220
                    isTimerRunning = true
                    stepProgress = 0f
                    currentStep = GameStep.CLEAN_DIRT
                    activeTool = ToolType.WET_WIPE
                    makeoverViewRef?.resetLevel()
                }
            )
        }

        // --- BACK / RESTART CONFIRMATION DIALOG ---
        if (showExitDialog) {
            ExitConfirmDialog(
                onDismiss = { showExitDialog = false },
                onRestart = {
                    showExitDialog = false
                    secondsLeft = 220
                    isTimerRunning = true
                    stepProgress = 0f
                    currentStep = GameStep.CLEAN_DIRT
                    activeTool = ToolType.WET_WIPE
                    makeoverViewRef?.resetLevel()
                },
                onExit = {
                    showExitDialog = false
                    onBackPressed()
                }
            )
        }
    }
}

// -------------------------------------------------------------
// Top Header Section: Objective Banner & Timer
// -------------------------------------------------------------

@Composable
fun TopHeaderSection(
    step: GameStep,
    progress: Float,
    timeString: String,
    isLowTime: Boolean,
    showAddTimeAnim: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Hanging wooden signboard from sprite sheet or rounded surface
            val signSprite = SpriteManager.bannerSign
            if (signSprite != null) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Image(
                        bitmap = signSprite.asImageBitmap(),
                        contentDescription = "Fix the Toes",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(58.dp)
                            .fillMaxWidth()
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White.copy(alpha = 0.92f),
                    shadowElevation = 5.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🦶 Fix the toes",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF3E2723)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = when (step) {
                                    GameStep.CLEAN_DIRT -> "1/4"
                                    GameStep.PLUCK_SPLINTERS -> "2/4"
                                    GameStep.TRIM_NAILS -> "3/4"
                                    GameStep.APPLY_CREAM -> "4/4"
                                    GameStep.COMPLETED -> "Done!"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD81B60)
                            )
                        }

                        Text(
                            text = step.subtitle,
                            fontSize = 11.sp,
                            color = Color(0xFF757575),
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Live Countdown Timer (Starts at 220s)
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = if (isLowTime) Color(0xFFFFEBEE) else Color.White.copy(alpha = 0.92f),
                    border = BorderStroke(
                        1.5.dp,
                        if (isLowTime) Color(0xFFE53935) else Color(0xFFFFCDD2)
                    ).toBorder(),
                    shadowElevation = 5.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "⏱️", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = timeString,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLowTime) Color(0xFFD32F2F) else Color(0xFF37474F)
                        )
                    }
                }

                // Floating "+30s!" pop animation
                androidx.compose.animation.AnimatedVisibility(
                    visible = showAddTimeAnim,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier.offset(y = (-30).dp)
                ) {
                    Text(
                        text = "+30s! ⏳",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF2E7D32)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Step subtitle and progress bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = step.subtitle,
                fontSize = 11.sp,
                color = Color(0xFF5D4037),
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = when (step) {
                    GameStep.CLEAN_DIRT -> "1/4"
                    GameStep.PLUCK_SPLINTERS -> "2/4"
                    GameStep.TRIM_NAILS -> "3/4"
                    GameStep.APPLY_CREAM -> "4/4"
                    GameStep.COMPLETED -> "Done!"
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD81B60)
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = Color(0xFFFF4081),
            trackColor = Color(0xFFFFEBEE)
        )
    }
}

// -------------------------------------------------------------
// Bottom Tool Tray: 4 Tools (Nail Grinder, Wet Wipe, Tweezers, Cream Tube)
// -------------------------------------------------------------

@Composable
fun ToolTray(
    selectedTool: ToolType?,
    recommendedTool: ToolType,
    isHintActive: Boolean,
    hintScale: Float,
    onSelectTool: (ToolType) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color.White.copy(alpha = 0.94f),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
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

                ToolTrayItem(
                    tool = tool,
                    isSelected = isSelected,
                    isRecommended = isRecommended,
                    scale = if (isRecommended) hintScale else 1f,
                    onClick = { onSelectTool(tool) }
                )
            }
        }
    }
}

@Composable
fun ToolTrayItem(
    tool: ToolType,
    isSelected: Boolean,
    isRecommended: Boolean,
    scale: Float,
    onClick: () -> Unit
) {
    val borderColor = when {
        isRecommended -> Color(0xFFFFB300)
        isSelected -> Color(0xFFFF4081)
        else -> Color.Transparent
    }

    val bgColor = when {
        isSelected -> Color(0xFFFCE4EC)
        else -> Color(0xFFF5F5F5)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .width(72.dp)
    ) {
        // Tool Icon (Sprite from sheet or procedural Canvas fallback)
        val toolSprite = when (tool) {
            ToolType.NAIL_GRINDER -> SpriteManager.toolGrinder
            ToolType.WET_WIPE -> SpriteManager.toolWipes
            ToolType.TWEEZERS -> SpriteManager.toolTweezers
            ToolType.CREAM_TUBE -> SpriteManager.toolCream
        }

        Box(
            modifier = Modifier.size(46.dp),
            contentAlignment = Alignment.Center
        ) {
            if (toolSprite != null) {
                Image(
                    bitmap = toolSprite.asImageBitmap(),
                    contentDescription = tool.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                )
            } else {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    when (tool) {
                        ToolType.NAIL_GRINDER -> {
                            drawRoundRect(
                                color = Color(0xFF00897B),
                                topLeft = Offset(w * 0.35f, h * 0.4f),
                                size = Size(w * 0.3f, h * 0.52f),
                                cornerRadius = CornerRadius(8f, 8f)
                            )
                            drawRect(
                                color = Color(0xFFCFD8DC),
                                topLeft = Offset(w * 0.4f, h * 0.28f),
                                size = Size(w * 0.2f, h * 0.12f)
                            )
                            drawOval(
                                color = Color(0xFFE91E63),
                                topLeft = Offset(w * 0.34f, h * 0.08f),
                                size = Size(w * 0.32f, h * 0.2f)
                            )
                        }
                        ToolType.WET_WIPE -> {
                            drawRoundRect(
                                color = Color(0xFFB3E5FC),
                                topLeft = Offset(w * 0.16f, h * 0.16f),
                                size = Size(w * 0.68f, h * 0.68f),
                                cornerRadius = CornerRadius(14f, 14f)
                            )
                            drawLine(
                                color = Color(0xFF0288D1),
                                start = Offset(w * 0.28f, h * 0.38f),
                                end = Offset(w * 0.72f, h * 0.38f),
                                strokeWidth = 3f,
                                cap = StrokeCap.Round
                            )
                        }
                        ToolType.TWEEZERS -> {
                            val leftArm = Path().apply {
                                moveTo(w * 0.36f, h * 0.85f)
                                lineTo(w * 0.32f, h * 0.45f)
                                lineTo(w * 0.46f, h * 0.12f)
                            }
                            val rightArm = Path().apply {
                                moveTo(w * 0.64f, h * 0.85f)
                                lineTo(w * 0.68f, h * 0.45f)
                                lineTo(w * 0.54f, h * 0.12f)
                            }
                            drawPath(leftArm, Color(0xFF78909C), style = Stroke(width = 5f, cap = StrokeCap.Round))
                            drawPath(rightArm, Color(0xFF78909C), style = Stroke(width = 5f, cap = StrokeCap.Round))
                        }
                        ToolType.CREAM_TUBE -> {
                            val tube = Path().apply {
                                moveTo(w * 0.28f, h * 0.85f)
                                lineTo(w * 0.72f, h * 0.85f)
                                lineTo(w * 0.62f, h * 0.32f)
                                lineTo(w * 0.38f, h * 0.32f)
                                close()
                            }
                            drawPath(tube, Color(0xFFEC407A))
                            drawRect(
                                color = Color.White,
                                topLeft = Offset(w * 0.42f, h * 0.22f),
                                size = Size(w * 0.16f, h * 0.10f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = tool.displayName,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color(0xFFC2185B) else Color(0xFF424242),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

// -------------------------------------------------------------
// Bottom Action Buttons: Back, Hint, Add Time
// -------------------------------------------------------------

@Composable
fun BottomActionButtons(
    onBackClicked: () -> Unit,
    onHintClicked: () -> Unit,
    onAddTimeClicked: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back Button
        ActionImageButton(
            sprite = SpriteManager.btnBack,
            fallbackLabel = "Back",
            fallbackIcon = "◀",
            containerColor = Color.White.copy(alpha = 0.9f),
            contentColor = Color(0xFF5D4037),
            onClick = onBackClicked
        )

        // Hint Button
        ActionImageButton(
            sprite = SpriteManager.btnHint,
            fallbackLabel = "Hint",
            fallbackIcon = "💡",
            containerColor = Color(0xFFFFF8E1),
            contentColor = Color(0xFFF57F17),
            onClick = onHintClicked
        )

        // Add Time Button (+30s)
        ActionImageButton(
            sprite = SpriteManager.btnAddTime,
            fallbackLabel = "+30s Time",
            fallbackIcon = "⏳",
            containerColor = Color(0xFFE8F5E9),
            contentColor = Color(0xFF2E7D32),
            onClick = onAddTimeClicked
        )
    }
}

@Composable
fun ActionImageButton(
    sprite: android.graphics.Bitmap?,
    fallbackLabel: String,
    fallbackIcon: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    if (sprite != null) {
        Box(
            modifier = Modifier
                .clickable { onClick() }
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = sprite.asImageBitmap(),
                contentDescription = fallbackLabel,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(54.dp)
            )
        }
    } else {
        ActionButton(
            label = fallbackLabel,
            icon = fallbackIcon,
            containerColor = containerColor,
            contentColor = contentColor,
            onClick = onClick
        )
    }
}

@Composable
fun ActionButton(
    label: String,
    icon: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.35f)).toBorder(),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

// -------------------------------------------------------------
// Level Complete Dialog
// -------------------------------------------------------------

@Composable
fun LevelCompleteDialog(
    secondsLeft: Int,
    onPlayAgain: () -> Unit
) {
    Dialog(onDismissRequest = {}) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🎉 LEVEL COMPLETE! 🎉",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFD81B60),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "All toes are perfectly cleaned, trimmed & glowing!",
                    fontSize = 13.sp,
                    color = Color(0xFF616161),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 3 Golden Stars
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "⭐", fontSize = 34.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "⭐", fontSize = 44.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "⭐", fontSize = 34.sp)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Stats Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFFCE4EC),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Satisfaction: 100%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC2185B)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Time Remaining: ${secondsLeft}s",
                            fontSize = 13.sp,
                            color = Color(0xFF880E4F)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Play Again Button
                Button(
                    onClick = onPlayAgain,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF4081)
                    ),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = "Play Again 💖",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Exit / Restart Confirmation Dialog
// -------------------------------------------------------------

@Composable
fun ExitConfirmDialog(
    onDismiss: () -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Pause & Options",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3E2723)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Would you like to restart this makeover or continue?",
                    fontSize = 13.sp,
                    color = Color(0xFF757575),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onRestart,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF80AB)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Restart Level 🔄", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCFD8DC)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Continue Game ▶", color = Color(0xFF37474F), fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onExit,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFCDD2)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Exit Game 🚪", color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Helper for BorderStroke conversion
data class BorderStroke(val width: androidx.compose.ui.unit.Dp, val color: Color) {
    fun toBorder(): androidx.compose.foundation.BorderStroke {
        return androidx.compose.foundation.BorderStroke(width, color)
    }
}
