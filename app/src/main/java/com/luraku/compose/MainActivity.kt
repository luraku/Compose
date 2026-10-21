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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.asImageBitmap
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ComposerScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposerScreen(vm: ComposerViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.addImage(uri)
    }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compose") },
                actions = {
                    if (vm.busy) {
                        CircularProgressIndicator(Modifier.size(24.dp).padding(end = 0.dp), strokeWidth = 2.dp)
                    }
                    IconButton(onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Icon(Icons.Filled.Add, contentDescription = "Ajouter une image") }
                    IconButton(onClick = { vm.save() }, enabled = vm.layers.isNotEmpty() && !vm.busy) {
                        Icon(Icons.Filled.Check, contentDescription = "Valider et enregistrer en PNG")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                val pageW = vm.pageW
                val pageH = vm.pageH
                val viewScale = min(constraints.maxWidth / pageW, constraints.maxHeight / pageH)
                val density = LocalDensity.current
                Page(
                    vm = vm,
                    viewScale = viewScale,
                    modifier = Modifier.size(
                        with(density) { (pageW * viewScale).toDp() },
                        with(density) { (pageH * viewScale).toDp() },
                    ),
                )
            }
            BottomPanel(vm, onAdd = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
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

@Composable
private fun BottomPanel(vm: ComposerViewModel, onAdd: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val titles = listOf("Calques", "Ajuster", "Page")
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        TabRow(selectedTabIndex = tab) {
            titles.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }
        Column(
            Modifier.fillMaxWidth().height(200.dp).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (tab) {
                0 -> LayersTab(vm, onAdd)
                1 -> AdjustTab(vm)
                else -> PageTab(vm)
            }
        }
    }
}

@Composable
private fun LayersTab(vm: ComposerViewModel, onAdd: () -> Unit) {
    if (vm.layers.isEmpty()) {
        Text("Aucune image. Touchez + pour en choisir une dans vos photos.")
        Button(onClick = onAdd) { Text("Ajouter une image") }
        return
    }
    Text("Du dessus (gauche) vers le fond (droite). Touchez pour sélectionner.", fontSize = 12.sp)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(vm.layers.reversed(), key = { it.id }) { layer ->
            val selected = layer.id == vm.selectedId
            val image = remember(layer.bitmap) { layer.bitmap.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .border(
                        if (selected) 3.dp else 1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(6.dp),
                    )
                    .clickable { vm.selectedId = layer.id },
            )
        }
    }
    val id = vm.selectedId
    if (id != null) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { vm.moveUp(id) }) { Text("Devant") }
            FilledTonalButton(onClick = { vm.moveDown(id) }) { Text("Derrière") }
            FilledTonalButton(onClick = { vm.update(id) { it.copy(flipped = !it.flipped) } }) { Text("Miroir") }
            FilledTonalButton(onClick = { vm.duplicate(id) }) { Text("Dupliquer") }
            FilledTonalButton(onClick = { vm.remove(id) }) { Text("Supprimer") }
        }
    }
}

@Composable
private fun AdjustTab(vm: ComposerViewModel) {
    val layer = vm.selected
    if (layer == null) {
        Text("Sélectionnez une image pour la régler.\nGlisser : déplacer · Pincer : taille · Deux doigts : rotation")
        return
    }
    val id = layer.id
    LabeledSlider(
        "Taille", ln(layer.scale), ln(0.02f)..ln(20f), "${(layer.scale * 100).roundToInt()} %",
    ) { v -> vm.update(id) { it.copy(scale = exp(v)) } }
    LabeledSlider(
        "Rotation centrale", normalize(layer.rotation), -180f..180f, "${normalize(layer.rotation).roundToInt()}°",
    ) { v -> vm.update(id) { it.copy(rotation = v) } }
    LabeledSlider(
        "Rotation axe vertical", layer.tiltY, -80f..80f, "${layer.tiltY.roundToInt()}°",
    ) { v -> vm.update(id) { it.copy(tiltY = v) } }
    LabeledSlider(
        "Rotation axe horizontal", layer.tiltX, -80f..80f, "${layer.tiltX.roundToInt()}°",
    ) { v -> vm.update(id) { it.copy(tiltX = v) } }
    LabeledSlider(
        "Opacité", layer.alpha, 0.05f..1f, "${(layer.alpha * 100).roundToInt()} %",
    ) { v -> vm.update(id) { it.copy(alpha = v) } }
}

@Composable
private fun PageTab(vm: ComposerViewModel) {
    Text("Format")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Aspect.entries.forEach { a ->
            FilterChip(selected = vm.aspect == a, onClick = { vm.aspect = a }, label = { Text(a.label) })
        }
    }
    Text("Fond")
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
            Text(label, Modifier.weight(1f), fontSize = 12.sp)
            Text(display, fontSize = 12.sp)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range)
    }
}

private fun normalize(deg: Float): Float {
    var d = deg % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}
