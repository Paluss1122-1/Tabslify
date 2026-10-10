package com.tabslify.core.ui

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownBlockQuote
import com.mikepenz.markdown.m3.Markdown
import com.tabslify.R
import com.tabslify.core.functions.podcastEpisodeFileName
import com.tabslify.core.functions.podcastFileCompletionKey
import com.tabslify.tabs.mediaplayer.Episode
import com.tabslify.tabs.mediaplayer.PodcastDownloadProgress
import com.tabslify.tabs.mediaplayer.PodcastFeed
import kotlinx.coroutines.launch
import java.util.Calendar

enum class AppBgScrim { STRONG, MEDIUM, LIGHT }

@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    scrim: AppBgScrim = AppBgScrim.MEDIUM,
    content: @Composable BoxScope.() -> Unit,
) {
    val isDay = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) in 11..16 }
    val bgPicture = if (isDay) R.drawable.day else R.drawable.night
    val scrimBrush = remember(scrim, isDay) {
        when (scrim) {
            AppBgScrim.STRONG -> if (isDay) {
                Brush.verticalGradient(listOf(BgScrimTop.copy(alpha = 0.92f), BgScrimBottom.copy(alpha = 0.92f)))
            } else {
                Brush.verticalGradient(listOf(BgScrimTop.copy(alpha = 0.70f), BgScrimBottom.copy(alpha = 0.85f)))
            }

            AppBgScrim.MEDIUM -> if (isDay) {
                Brush.verticalGradient(listOf(APP_COLOR.copy(alpha = 0.85f), APP_BLUE.copy(alpha = 0.45f)))
            } else {
                Brush.verticalGradient(listOf(APP_COLOR.copy(alpha = 0.70f), Color(0xFF001A93).copy(alpha = 0.70f)))
            }

            AppBgScrim.LIGHT -> if (isDay) {
                Brush.verticalGradient(listOf(APP_COLOR.copy(alpha = 0.75f), Color(0xFF001A93).copy(alpha = 0.75f)))
            } else {
                Brush.verticalGradient(listOf(APP_COLOR.copy(alpha = 0.55f), Color(0xFF001A93).copy(alpha = 0.75f)))
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = bgPicture),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimBrush)
        )
        content()
    }
}

fun parseCalloutType(content: String): Pair<CalloutType?, String> {
    val calloutPattern = Regex("^\\s*\\[!(\\w+)]\\s*(.*)$", RegexOption.DOT_MATCHES_ALL)
    val matchResult = calloutPattern.find(content.trim())
    return if (matchResult != null) {
        val type = when (matchResult.groupValues[1].uppercase()) {
            "WARNING", "CAUTION", "ATTENTION" -> CalloutType.WARNING
            "INFO" -> CalloutType.INFO
            "NOTE" -> CalloutType.NOTE
            "TIP", "HINT", "IMPORTANT" -> CalloutType.TIP
            "SUCCESS", "CHECK", "DONE" -> CalloutType.SUCCESS
            "DANGER", "ERROR" -> CalloutType.DANGER
            else -> null
        }
        type to matchResult.groupValues[2].trim()
    } else {
        null to content
    }
}

enum class CalloutType {
    WARNING, INFO, NOTE, TIP, SUCCESS, DANGER
}

fun normalizeCallouts(text: String): String {
    val calloutStart = Regex("^\\[!(\\w+)]")
    val builder = StringBuilder()
    var inCallout = false
    for (line in text.lines()) {
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith(">") -> {
                inCallout = calloutStart.containsMatchIn(trimmed.removePrefix(">").trimStart())
                builder.append(line).append('\n')
            }
            calloutStart.containsMatchIn(trimmed) -> {
                inCallout = true
                builder.append("> ").append(trimmed).append('\n')
            }
            inCallout && trimmed.isNotBlank() -> {
                builder.append("> ").append(trimmed).append('\n')
            }
            else -> {
                if (trimmed.isBlank()) inCallout = false
                builder.append(line).append('\n')
            }
        }
    }
    return builder.toString().trimEnd('\n')
}

@Composable
fun Callout(
    type: CalloutType,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val (borderColor, backgroundColor) = when (type) {
        CalloutType.WARNING -> Color(0xFFFFAB00) to Color(0xFFFFAB00).copy(alpha = 0.1f)
        CalloutType.INFO -> Color(0xFF2196F3) to Color(0xFF2196F3).copy(alpha = 0.1f)
        CalloutType.NOTE -> Color(0xFF9E9E9E) to Color(0xFF9E9E9E).copy(alpha = 0.1f)
        CalloutType.TIP -> Color(0xFF4CAF50) to Color(0xFF4CAF50).copy(alpha = 0.1f)
        CalloutType.SUCCESS -> Color(0xFF4CAF50) to Color(0xFF4CAF50).copy(alpha = 0.1f)
        CalloutType.DANGER -> Color(0xFFF44336) to Color(0xFFF44336).copy(alpha = 0.1f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor, shape = RoundedCornerShape(8.dp))
            .drawBehind {
                drawRect(
                    color = borderColor,
                    size = size.copy(width = 4.dp.toPx())
                )
            }
            .padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
    ) {
        content()
    }
}

@Composable
fun calloutAwareMarkdownComponents() = markdownComponents(
    blockQuote = {
        val rawQuote = it.content.substring(it.node.startOffset, it.node.endOffset)
        val strippedQuote = rawQuote.lines().joinToString("\n") { line ->
            line.trimStart().removePrefix(">").trimStart()
        }
        val (calloutType, remainingContent) = parseCalloutType(strippedQuote)
        if (calloutType != null) {
            Callout(type = calloutType, content = {
                Markdown(content = remainingContent)
            })
        } else {
            MarkdownBlockQuote(it.content, it.node, TextStyle.Default)
        }
    }
)

@Composable
fun PloppingButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onFinishedClick: () -> Unit = {},
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    shape: Shape = ButtonDefaults.shape,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable () -> Unit,
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val containerColor = if (enabled) colors.containerColor else colors.disabledContainerColor

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                alpha = if (enabled) 1f else 0.38f
            }
            .clip(shape)
            .background(containerColor)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput

                awaitEachGesture {
                    awaitFirstDown()

                    scope.launch {
                        scale.animateTo(
                            0.82f,
                            spring(
                                stiffness = Spring.StiffnessHigh,
                                dampingRatio = Spring.DampingRatioMediumBouncy
                            )
                        )
                    }

                    val up = waitForUpOrCancellation()

                    scope.launch {
                        scale.animateTo(
                            1f,
                            spring(
                                stiffness = Spring.StiffnessMedium,
                                dampingRatio = Spring.DampingRatioLowBouncy
                            )
                        )
                    }

                    if (up != null) {
                        onClick()
                        onFinishedClick()
                    }
                }
            }
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun NeonBox(
    modifier: Modifier = Modifier,
    cornerRadius: RoundedCornerShape = RoundedCornerShape(20.dp),
    backgroundAlpha: Float = 0.22f,
    borderWidth: Dp = 3.dp,
    glowBlur1: Float = 42f,
    glowBlur2: Float = 114f,
    neonColors: List<Color>,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    require(neonColors.size >= 2) { "neonColors must contain at least 2 colors" }

    val gradientBrush = Brush.linearGradient(
        colors = neonColors.take(2).map { it.copy(alpha = backgroundAlpha) }
    )

    Box(
        modifier = modifier
            .drawBehind {
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(0f, 0f, size.width, size.height),
                            topLeft = CornerRadius(
                                cornerRadius.topStart.toPx(
                                    size,
                                    this@drawBehind
                                )
                            ),
                            topRight = CornerRadius(
                                cornerRadius.topEnd.toPx(
                                    size,
                                    this@drawBehind
                                )
                            ),
                            bottomRight = CornerRadius(
                                cornerRadius.bottomEnd.toPx(
                                    size,
                                    this@drawBehind
                                )
                            ),
                            bottomLeft = CornerRadius(
                                cornerRadius.bottomStart.toPx(
                                    size,
                                    this@drawBehind
                                )
                            )
                        )
                    )
                }

                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.apply {
                        drawPath(
                            path.asAndroidPath(),
                            Paint().apply {
                                color = neonColors.last().copy(alpha = 0.55f).toArgb()
                                isAntiAlias = true
                                maskFilter = android.graphics.BlurMaskFilter(
                                    glowBlur1,
                                    android.graphics.BlurMaskFilter.Blur.OUTER
                                )
                            }
                        )
                        drawPath(
                            path.asAndroidPath(),
                            Paint().apply {
                                color = neonColors.first().copy(alpha = 0.65f).toArgb()
                                isAntiAlias = true
                                maskFilter = android.graphics.BlurMaskFilter(
                                    glowBlur2,
                                    android.graphics.BlurMaskFilter.Blur.OUTER
                                )
                            }
                        )
                    }
                }
            }
            .clip(cornerRadius)
            .background(gradientBrush)
            .border(
                width = borderWidth,
                brush = Brush.linearGradient(neonColors),
                shape = cornerRadius
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun FeedCard(
    feed: PodcastFeed,
    isExpanded: Boolean,
    feedEpisodes: List<Episode>?,
    loadingEpisodes: String?,
    isFavorite: Boolean,
    onToggleExpand: () -> Unit,
    onToggleFav: () -> Unit,
    onDownload: (String, String) -> Unit,
    onRemoveDownload: (String, String) -> Unit,
    onStream: (String, String) -> Unit,
    newAudioUrls: Set<String> = emptySet(),
    downloadedAudioUrls: Set<String> = emptySet(),
    activeDownloads: Map<String, PodcastDownloadProgress> = emptyMap(),
    completedKeys: Set<String> = emptySet(),
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AsyncImage(
                    model = feed.image.ifEmpty { null },
                    contentDescription = null,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2A2A32)),
                    contentScale = ContentScale.Crop,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        feed.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (feed.author.isNotEmpty()) {
                        Text(
                            feed.author,
                            fontSize = 12.sp,
                            color = Color(0xFF7A7880),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onToggleFav) {
                    Icon(
                        if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = null,
                        tint = if (isFavorite) Color(0xFFE8622A) else Color(0xFF4A4850)
                    )
                }
                IconButton(onClick = onToggleExpand) {
                    if (loadingEpisodes == feed.feedUrl) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFFE8622A)
                        )
                    } else {
                        Icon(
                            if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null, tint = Color(0xFFE8622A)
                        )
                    }
                }
            }

            if (isExpanded) {
                HorizontalDivider(color = Color.White.copy(0.07f))
                when {
                    feedEpisodes == null -> Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color(0xFFE8622A)
                        )
                    }

                    feedEpisodes.isEmpty() -> Text(
                        stringResource(R.string.keine_episoden_gefunden),
                        modifier = Modifier.padding(16.dp),
                        color = Color(0xFF7A7880),
                        fontSize = 13.sp
                    )

                    else -> feedEpisodes.forEachIndexed { idx, ep ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(modifier = Modifier.width(24.dp)) {
                                Text(
                                    "${idx + 1}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF4A4850),
                                )
                                if (ep.audioUrl in newAudioUrls) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(6.dp)
                                            .background(
                                                Color.Red,
                                                androidx.compose.foundation.shape.CircleShape
                                            )
                                    )
                                }
                            }
                            Text(
                                ep.title,
                                modifier = Modifier.weight(1f),
                                fontSize = 13.sp,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            val isDownloaded = ep.audioUrl in downloadedAudioUrls
                            val isFinished = !isDownloaded &&
                                    (ep.audioUrl in completedKeys ||
                                            podcastFileCompletionKey(podcastEpisodeFileName(ep.title)) in completedKeys)
                            if (isFinished) {
                                Icon(
                                    Icons.Default.DoneAll,
                                    contentDescription = null,
                                    tint = Color(0xFF4A4850),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            OutlinedIconButton(
                                onClick = { onStream(ep.audioUrl, ep.title) },
                                modifier = Modifier.size(36.dp),
                                border = BorderStroke(1.dp, Color.White.copy(0.15f))
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color(0xFFE8622A),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            val active = activeDownloads[ep.audioUrl]
                            if (ep.audioUrl in downloadedAudioUrls) {
                                OutlinedIconButton(
                                    onClick = { onRemoveDownload(ep.audioUrl, ep.title) },
                                    modifier = Modifier.size(36.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(0.15f))
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFFE8622A),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else if (active != null) {
                                Box(
                                    modifier = Modifier.size(36.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (active.percent >= 0) {
                                        CircularProgressIndicator(
                                            progress = { active.percent / 100f },
                                            modifier = Modifier.size(26.dp),
                                            strokeWidth = 2.5.dp,
                                            color = Color(0xFFE8622A),
                                            trackColor = Color.White.copy(0.12f)
                                        )
                                        Text(
                                            "${active.percent}",
                                            fontSize = 9.sp,
                                            color = Color.White
                                        )
                                    } else {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(22.dp),
                                            strokeWidth = 2.5.dp,
                                            color = Color(0xFFE8622A)
                                        )
                                    }
                                }
                            } else {
                                OutlinedIconButton(
                                    onClick = { onDownload(ep.audioUrl, ep.title) },
                                    modifier = Modifier.size(36.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(0.15f))
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = Color(0xFFE8622A),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        if (idx < feedEpisodes.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = Color.White.copy(0.04f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlertDialogTabslify(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
    title: String = "",
    text: String = "",
    confirmText: String = stringResource(R.string.loschen),
    oneButton: Boolean = false,
    shape: Shape = AlertDialogDefaults.shape,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties()
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (!oneButton) Color(0xFFB71C1C) else MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onConfirm)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) { Text(confirmText, color = TextPrimary, fontWeight = FontWeight.SemiBold) }
        },
        modifier = modifier,
        dismissButton = {
            if (!oneButton) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(BgCard)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) { Text(stringResource(R.string.abbrechen), color = TextSecondary) }
            }
        },
        icon = icon,
        title = {
            Text(
                title,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        shape = shape,
        containerColor = BgSurface,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties
    )
}

@Composable
fun DialogTabslify(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
    title: String = "",
    text: String = "",
    confirmText: String = stringResource(R.string.loschen),
    oneButton: Boolean = false,
    shape: Shape = RoundedCornerShape(28.dp),
    iconContentColor: Color = Color.Unspecified,
    titleContentColor: Color = Color.Unspecified,
    textContentColor: Color = Color.Unspecified
) {
    val scale = remember { Animatable(0.8f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 300)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(onClick = onDismiss)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = modifier
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                }
                .background(BgSurface, shape)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                icon?.let {
                    CompositionLocalProvider(LocalContentColor provides iconContentColor) {
                        it()
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if (title.isNotEmpty()) {
                    Text(
                        title,
                        color = if (titleContentColor != Color.Unspecified) titleContentColor else TextSecondary,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = if (text.isNotEmpty()) 16.dp else 24.dp)
                    )
                }
                if (text.isNotEmpty()) {
                    Text(
                        text,
                        color = if (textContentColor != Color.Unspecified) textContentColor else TextSecondary,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!oneButton) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(BgCard)
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) { Text(stringResource(R.string.abbrechen), color = TextSecondary) }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (!oneButton) Color(0xFFB71C1C) else MaterialTheme.colorScheme.primary)
                            .clickable(onClick = onConfirm)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) { Text(confirmText, color = TextPrimary, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

