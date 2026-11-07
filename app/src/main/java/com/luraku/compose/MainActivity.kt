package com.luraku.compose

import android.graphics.Paint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToInt

private val AppColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF062E6F),
    background = Color(0xFF0E0E10),
    onBackground = Color(0xFFE6E6E9),
    surface = Color(0xFF1A1A1D),
    onSurface = Color(0xFFE6E6E9),
    surfaceVariant = Color(0xFF26262A),
    onSurfaceVariant = Color(0xFFA9A9B0),
    outline = Color(0xFF3A3A40),
    secondaryContainer = Color(0xFF2B3A55),
    onSecondaryContainer = Color(0xFFD6E3FF),
    error = Color(0xFFFF8A80),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = AppColors) {
                ComposerScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposerScreen(vm: ComposerViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        vm.addImages(uris)
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Compose", fontWeight = FontWeight.SemiBold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    actions = {
                        TextButton(onClick = pick) {
                            Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Ajouter")
                        }
                        Button(
                            onClick = { vm.save() },
                            enabled = vm.layers.isNotEmpty() && !vm.busy,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Enregistrer")
                        }
                    },
                )
                if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp)) else Spacer(Modifier.height(2.dp))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val pageW = vm.pageW
                val pageH = vm.pageH
                val viewScale = min(constraints.maxWidth / pageW, constraints.maxHeight / pageH)
                val density = LocalDensity.current
                val shape = RoundedCornerShape(4.dp)
                Box(
                    Modifier
                        .size(
                            with(density) { (pageW * viewScale).toDp() },
                            with(density) { (pageH * viewScale).toDp() },
                        )
                        .shadow(12.dp, shape)
                        .clip(shape),
                    contentAlignment = Alignment.Center,
                ) {
                    Page(vm = vm, viewScale = viewScale, modifier = Modifier.fillMaxSize())
                    if (vm.layers.isEmpty()) {
                        Text(
                            "Ajoutez des photos pour commencer",
                            color = Color(0xFF6B6B72),
                            fontSize = 14.sp,
                        )
                    }
                }
            }
            BottomPanel(vm, onAdd = pick)
        }
    }
}

@Composable
private fun Page(vm: ComposerViewModel, viewScale: Float, modifier: Modifier) {
    val paint = remember { Renderer.newPaint() }
    val dark = remember { Paint().apply { color = 0xFFB0B0B0.toInt() } }
    val light = remember { Paint().apply { color = 0xFFE0E0E0.toInt() } }
    val outline = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF00E5FF.toInt()
            style = Paint.Style.STROKE
        }
    }

    Canvas(
        modifier.pointerInput(viewScale) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                vm.hitTest(down.position.x / viewScale, down.position.y / viewScale)
                    ?.let { vm.selectedId = it }
                do {
                    val event = awaitPointerEvent()
                    val cancelled = event.changes.any { it.isConsumed }
                    if (!cancelled) {
                        val zoom = event.calculateZoom()
                        val rotation = event.calculateRotation()
                        val pan = event.calculatePan()
                        val id = vm.selectedId
                        if (id != null && (zoom != 1f || rotation != 0f || pan != Offset.Zero)) {
                            vm.update(id) {
                                it.copy(
                                    cx = it.cx + pan.x / viewScale,
                                    cy = it.cy + pan.y / viewScale,
                                    scale = (it.scale * zoom).coerceIn(0.02f, 20f),
                                    rotation = it.rotation + rotation,
                                )
                            }
                            event.changes.forEach { c: PointerInputChange -> if (c.positionChanged()) c.consume() }
                        }
                    }
                } while (!cancelled && event.changes.any { it.pressed })
            }
        },
    ) {
        val w = vm.pageW
        val h = vm.pageH
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            nc.save()
            nc.scale(viewScale, viewScale)
            nc.clipRect(0f, 0f, w, h)
            val bg = vm.background.color
            if (bg != null) {
                nc.drawColor(bg)
            } else {
                val cell = 25f
                nc.drawRect(0f, 0f, w, h, light)
                var row = 0
                var y = 0f
                while (y < h) {
                    var col = 0
                    var x = 0f
                    while (x < w) {
                        if ((row + col) % 2 == 1) nc.drawRect(x, y, x + cell, y + cell, dark)
                        x += cell; col++
                    }
                    y += cell; row++
                }
            }
            vm.layers.forEach { Renderer.drawLayer(nc, it, paint) }
            vm.selected?.let { l ->
                val q = Renderer.pageCorners(l)
                outline.strokeWidth = 2f / viewScale
                for (i in 0 until 4) {
                    val j = (i + 1) % 4
                    nc.drawLine(q[2 * i], q[2 * i + 1], q[2 * j], q[2 * j + 1], outline)
                }
            }
            nc.restore()
        }
    }
}

private val PanelShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

@Composable
private fun BottomPanel(vm: ComposerViewModel, onAdd: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Surface(color = MaterialTheme.colorScheme.surface, shape = PanelShape, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(top = 12.dp)) {
            SegmentedTabs(listOf("Calques", "Ajuster", "Page"), tab, { tab = it })
            Column(
                Modifier.fillMaxWidth().height(190.dp).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (tab) {
                    0 -> LayersTab(vm, onAdd)
                    1 -> AdjustTab(vm)
                    else -> PageTab(vm)
                }
            }
        }
    }
}

@Composable
private fun SegmentedTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
    ) {
        titles.forEachIndexed { i, t ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LayersTab(vm: ComposerViewModel, onAdd: () -> Unit) {
    if (vm.layers.isEmpty()) {
        Hint("Aucune image. Vous pouvez en sélectionner plusieurs à la fois.")
        Button(onClick = onAdd) { Text("Choisir des photos") }
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(vm.layers.reversed(), key = { it.id }) { layer ->
            val selected = layer.id == vm.selectedId
            val image = remember(layer.bitmap) { layer.bitmap.asImageBitmap() }
            val thumb = RoundedCornerShape(10.dp)
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(thumb)
                    .border(
                        if (selected) 2.dp else 1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        thumb,
                    )
                    .clickable { vm.selectedId = layer.id },
            )
        }
    }
    Hint("Dessus à gauche, fond à droite")
    val id = vm.selectedId
    if (id != null) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionChip("Devant") { vm.moveUp(id) }
            ActionChip("Derrière") { vm.moveDown(id) }
            ActionChip("Miroir") { vm.update(id) { it.copy(flipped = !it.flipped) } }
            ActionChip("Dupliquer") { vm.duplicate(id) }
            ActionChip("Supprimer", destructive = true) { vm.remove(id) }
        }
    }
}

@Composable
private fun ActionChip(label: String, destructive: Boolean = false, onClick: () -> Unit) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    AssistChip(
        onClick = onClick,
        label = { Text(label, color = color) },
        border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = MaterialTheme.colorScheme.outline),
    )
}

@Composable
private fun AdjustTab(vm: ComposerViewModel) {
    val layer = vm.selected
    if (layer == null) {
        Hint("Sélectionnez une image.")
        Hint("Glisser : déplacer · Pincer : taille · Deux doigts : rotation")
        return
    }
    val id = layer.id
    LabeledSlider(
        "Taille", ln(layer.scale), ln(0.02f)..ln(20f), "${(layer.scale * 100).roundToInt()} %",
    ) { v -> vm.update(id) { it.copy(scale = exp(v)) } }
    LabeledSlider(
        "Rotation", normalize(layer.rotation), -180f..180f, "${normalize(layer.rotation).roundToInt()}°",
    ) { v -> vm.update(id) { it.copy(rotation = v) } }
    LabeledSlider(
        "Axe vertical", layer.tiltY, -80f..80f, "${layer.tiltY.roundToInt()}°",
    ) { v -> vm.update(id) { it.copy(tiltY = v) } }
    LabeledSlider(
        "Axe horizontal", layer.tiltX, -80f..80f, "${layer.tiltX.roundToInt()}°",
    ) { v -> vm.update(id) { it.copy(tiltX = v) } }
    LabeledSlider(
        "Opacité", layer.alpha, 0.05f..1f, "${(layer.alpha * 100).roundToInt()} %",
    ) { v -> vm.update(id) { it.copy(alpha = v) } }
}

@Composable
private fun PageTab(vm: ComposerViewModel) {
    Hint("Format")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Aspect.entries.forEach { a ->
            FilterChip(selected = vm.aspect == a, onClick = { vm.aspect = a }, label = { Text(a.label) })
        }
    }
    Hint("Fond")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Background.entries.forEach { b ->
            FilterChip(selected = vm.background == b, onClick = { vm.background = b }, label = { Text(b.label) })
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row {
            Text(label, Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(display, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

private fun normalize(deg: Float): Float {
    var d = deg % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}
