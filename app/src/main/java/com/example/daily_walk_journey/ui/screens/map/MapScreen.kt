package com.example.daily_walk_journey.ui.screens.map

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.daily_walk_journey.data.db.AppDatabase
import com.example.daily_walk_journey.data.db.entity.Checkpoint
import com.example.daily_walk_journey.data.db.entity.Poi
import com.example.daily_walk_journey.data.db.entity.RoutePoint
import com.example.daily_walk_journey.data.repository.PoiRepository
import com.example.daily_walk_journey.service.StepCounterService
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.time.LocalDate

private const val MAP_STYLE_URI = "https://tiles.openfreemap.org/styles/liberty"
private const val POI_SOURCE_ID = "stepsnap-pois"
private const val POI_LAYER_ID = "stepsnap-poi-circles"
private const val CHECKPOINT_SOURCE_ID = "stepsnap-checkpoints"
private const val CHECKPOINT_LAYER_ID = "stepsnap-checkpoint-circles"
private const val ROUTE_SOURCE_ID = "stepsnap-route"
private const val ROUTE_LAYER_ID = "stepsnap-route-line"
private const val LOCATION_SOURCE_ID = "stepsnap-current-location"
private const val LOCATION_LAYER_ID = "stepsnap-current-location-circle"

private val poiCategories = listOf("Kafe", "Taman", "Landmark", "Kuliner", "Lainnya")

data class MapUiState(
    val pois: List<Poi> = emptyList(),
    val checkpoints: List<Checkpoint> = emptyList(),
    val routePoints: List<RoutePoint> = emptyList()
)

class MapViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val repository = PoiRepository(db.poiDao())

    val uiState: StateFlow<MapUiState> = combine(
        repository.getTodayPois(),
        db.checkpointDao().getLocatedCheckpointsByDate(LocalDate.now().toString()),
        db.routePointDao().observeByDate(LocalDate.now().toString())
    ) { pois, checkpoints, routePoints ->
        MapUiState(pois, checkpoints, routePoints)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    fun save(poi: Poi) = viewModelScope.launch { repository.save(poi) }
    fun delete(poi: Poi) = viewModelScope.launch { repository.delete(poi) }
}

@Composable
fun MapScreen(viewModel: MapViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsState()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var loadedStyle by remember { mutableStateOf<Style?>(null) }
    var filter by remember { mutableStateOf("Semua") }
    var editing by remember { mutableStateOf<Poi?>(null) }
    var coordinate by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(poiCategories.first()) }
    var note by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf("0") }
    var photo by remember { mutableStateOf<String?>(null) }
    var currentLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var locationStatus by remember { mutableStateOf("") }
    var followCurrentLocation by remember { mutableStateOf(true) }
    var showLocationRationale by remember { mutableStateOf(false) }
    var showLocationSettings by remember { mutableStateOf(false) }
    var locationPermissionRequested by rememberSaveable { mutableStateOf(false) }
    val activity = context as? Activity

    val latestState by rememberUpdatedState(state)
    val latestFilter by rememberUpdatedState(filter)
    val latestCurrentLocation by rememberUpdatedState(currentLocation)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        photo = it?.toString()
    }

    fun openForm(point: Pair<Double, Double>, poi: Poi? = null) {
        editing = poi
        coordinate = point
        name = poi?.name.orEmpty()
        category = poi?.category ?: poiCategories.first()
        note = poi?.note.orEmpty()
        rating = poi?.rating?.toString() ?: "0"
        photo = poi?.photoPath
        showForm = true
    }

    val latestOpenForm by rememberUpdatedState<(Pair<Double, Double>, Poi?) -> Unit> { point, poi ->
        openForm(point, poi)
    }

    fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    fun centerOnCurrentLocation() {
        if (!hasLocationPermission()) {
            locationStatus = "Izin lokasi diperlukan."
            return
        }

        followCurrentLocation = true
        StepCounterService.enableLocationTracking(context)
        locationStatus = "Mencari lokasi..."
        try {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token
            ).addOnSuccessListener { location: Location? ->
                if (location == null) {
                    locationStatus = "Lokasi belum tersedia. Aktifkan GPS lalu coba lagi."
                } else {
                    currentLocation = location.latitude to location.longitude
                    locationStatus = if (StepCounterService.isServiceRunning.value) {
                        "Lokasi ditemukan • rute sedang direkam"
                    } else {
                        "Lokasi ditemukan • mulai pelacakan langkah untuk merekam rute"
                    }
                }
            }.addOnFailureListener { error ->
                locationStatus = "Gagal mengambil lokasi: ${error.localizedMessage ?: "coba lagi"}"
            }
        } catch (_: SecurityException) {
            locationStatus = "Izin lokasi belum diberikan."
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it } || hasLocationPermission()) {
            centerOnCurrentLocation()
        } else {
            locationStatus = "Izin lokasi ditolak. Peta tetap bisa digunakan."
        }
    }

    fun launchLocationPermissionRequest() {
        locationPermissionRequested = true
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    fun requestLocationAccess() {
        if (hasLocationPermission()) {
            centerOnCurrentLocation()
            return
        }

        val shouldShowRationale = activity?.let {
            it.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ||
                it.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
        } == true

        when {
            shouldShowRationale -> showLocationRationale = true
            locationPermissionRequested -> showLocationSettings = true
            else -> launchLocationPermissionRequest()
        }
    }

    LaunchedEffect(Unit) {
        requestLocationAccess()
    }

    val latestRoutePoint = state.routePoints.lastOrNull()
    LaunchedEffect(latestRoutePoint?.id) {
        latestRoutePoint?.let { point ->
            currentLocation = point.latitude to point.longitude
        }
    }

    LaunchedEffect(mapLibreMap, currentLocation, followCurrentLocation) {
        val point = currentLocation
        if (followCurrentLocation && point != null) {
            mapLibreMap?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(point.first, point.second),
                    17.0
                ),
                500
            )
        }
    }

    Scaffold(floatingActionButton = {
        FloatingActionButton(onClick = {
            val target = mapLibreMap?.cameraPosition?.target
            openForm(target?.let { it.latitude to it.longitude } ?: (-6.2 to 106.8))
        }) {
            Text("+")
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { requestLocationAccess() }) {
                    Text("Lokasi saya")
                }
                Text(locationStatus, style = MaterialTheme.typography.labelSmall)
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                (listOf("Semua") + poiCategories).forEach { item ->
                    FilterChip(
                        selected = filter == item,
                        onClick = { filter = item },
                        label = { Text(item) }
                    )
                }
            }

            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { ctx ->
                    if (!MapLibre.hasInstance()) {
                        MapLibre.getInstance(ctx.applicationContext)
                    }
                    MapView(ctx).apply {
                        onCreate(null)
                        mapView = this
                        getMapAsync { map ->
                            mapLibreMap = map
                            map.cameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                                .target(LatLng(-6.2, 106.8))
                                .zoom(14.0)
                                .build()
                            map.setStyle(Style.Builder().fromUri(MAP_STYLE_URI)) { style ->
                                style.addSource(GeoJsonSource(POI_SOURCE_ID, emptyFeatureCollection()))
                                style.addSource(
                                    GeoJsonSource(CHECKPOINT_SOURCE_ID, emptyFeatureCollection())
                                )
                                style.addSource(GeoJsonSource(ROUTE_SOURCE_ID, emptyFeatureCollection()))
                                style.addSource(
                                    GeoJsonSource(LOCATION_SOURCE_ID, emptyFeatureCollection())
                                )

                                style.addLayer(
                                    LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                                        lineColor(android.graphics.Color.BLUE),
                                        lineWidth(4f),
                                        lineOpacity(0.75f)
                                    )
                                )
                                style.addLayer(
                                    CircleLayer(CHECKPOINT_LAYER_ID, CHECKPOINT_SOURCE_ID)
                                        .withProperties(
                                            circleColor(android.graphics.Color.BLUE),
                                            circleRadius(7f),
                                            circleStrokeColor(android.graphics.Color.WHITE),
                                            circleStrokeWidth(2f)
                                        )
                                )
                                style.addLayer(
                                    CircleLayer(POI_LAYER_ID, POI_SOURCE_ID).withProperties(
                                        circleColor(android.graphics.Color.rgb(30, 136, 100)),
                                        circleRadius(8f),
                                        circleStrokeColor(android.graphics.Color.WHITE),
                                        circleStrokeWidth(2f)
                                    )
                                )
                                style.addLayer(
                                    CircleLayer(LOCATION_LAYER_ID, LOCATION_SOURCE_ID)
                                        .withProperties(
                                            circleColor(android.graphics.Color.RED),
                                            circleRadius(9f),
                                            circleStrokeColor(android.graphics.Color.WHITE),
                                            circleStrokeWidth(2f)
                                        )
                                )
                                loadedStyle = style

                                map.addOnMapClickListener { point ->
                                    val screenPoint = map.projection.toScreenLocation(point)
                                    val poiId = map.queryRenderedFeatures(screenPoint, POI_LAYER_ID).firstNotNullOfOrNull { feature ->
                                        feature.getNumberProperty("poiId")?.toLong()
                                    }
                                    val poi = poiId?.let { id ->
                                        latestState.pois.firstOrNull { it.id == id }
                                    }
                                    if (poi != null) {
                                        latestOpenForm(poi.latitude to poi.longitude, poi)
                                    } else {
                                        latestOpenForm(point.latitude to point.longitude, null)
                                    }
                                    true
                                }
                            }
                        }
                    }
                },
                update = { view ->
                    if (mapView !== view) mapView = view
                    loadedStyle?.let { style ->
                        updateMapSources(
                            style = style,
                            state = latestState,
                            filter = latestFilter,
                            currentLocation = latestCurrentLocation
                        )
                    }
                }
            )

            Text(
                "© OpenStreetMap contributors • OpenFreeMap • Ketuk peta untuk menandai POI",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(8.dp)
            )

            LazyColumn(Modifier.height(150.dp)) {
                items(
                    state.pois.filter { filter == "Semua" || it.category == filter },
                    key = { it.id }
                ) { poi ->
                    TextButton(
                        onClick = {
                            mapLibreMap?.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(poi.latitude, poi.longitude),
                                    17.0
                                ),
                                500
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${poi.name} · ${poi.category} · ★ ${poi.rating}")
                    }
                }
            }
        }
    }

    mapView?.let { view ->
        DisposableEffect(view, lifecycleOwner) {
            var started = false
            var resumed = false
            var destroyed = false

            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> if (!started && !destroyed) {
                        view.onStart()
                        started = true
                    }
                    Lifecycle.Event.ON_RESUME -> if (!resumed && !destroyed) {
                        view.onResume()
                        resumed = true
                    }
                    Lifecycle.Event.ON_PAUSE -> if (resumed && !destroyed) {
                        view.onPause()
                        resumed = false
                    }
                    Lifecycle.Event.ON_STOP -> if (started && !destroyed) {
                        view.onStop()
                        started = false
                    }
                    Lifecycle.Event.ON_DESTROY -> if (!destroyed) {
                        view.onDestroy()
                        destroyed = true
                    }
                    else -> Unit
                }
            }

            lifecycleOwner.lifecycle.addObserver(observer)

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                if (!destroyed) {
                    if (resumed) view.onPause()
                    if (started) view.onStop()
                    view.onDestroy()
                }
            }
        }
    }

    if (showLocationRationale) {
        AlertDialog(
            onDismissRequest = { showLocationRationale = false },
            title = { Text("Izinkan akses lokasi") },
            text = {
                Text("Lokasi digunakan untuk memusatkan peta dan merekam garis perjalanan saat pelacakan langkah aktif.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showLocationRationale = false
                    launchLocationPermissionRequest()
                }) {
                    Text("Lanjutkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocationRationale = false }) {
                    Text("Nanti")
                }
            }
        )
    }

    if (showLocationSettings) {
        AlertDialog(
            onDismissRequest = { showLocationSettings = false },
            title = { Text("Aktifkan izin lokasi") },
            text = {
                Text("Izin lokasi ditolak permanen. Aktifkan izin lokasi melalui pengaturan aplikasi untuk mengikuti posisi dan merekam rute.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showLocationSettings = false
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                    )
                }) {
                    Text("Buka pengaturan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocationSettings = false }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showForm) {
        AlertDialog(
            onDismissRequest = { showForm = false },
            title = { Text(if (editing == null) "Tandai tempat ini" else "Edit POI") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama tempat") },
                        singleLine = true
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        poiCategories.forEach { item ->
                            FilterChip(
                                selected = category == item,
                                onClick = { category = item },
                                label = { Text(item) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(160) },
                        label = { Text("Catatan") },
                        maxLines = 2
                    )
                    OutlinedTextField(
                        value = rating,
                        onValueChange = { rating = it.filter(Char::isDigit).take(1) },
                        label = { Text("Rating 0-5") },
                        singleLine = true
                    )
                    OutlinedButton(onClick = { picker.launch("image/*") }) {
                        Text(if (photo == null) "Pilih foto" else "Foto dipilih")
                    }
                    coordinate?.let {
                        Text("%.5f, %.5f".format(it.first, it.second))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val point = coordinate
                    if (name.isNotBlank() && point != null) {
                        viewModel.save(
                            Poi(
                                id = editing?.id ?: 0,
                                name = name.trim(),
                                category = category,
                                note = note.trim().ifBlank { null },
                                rating = (rating.toFloatOrNull() ?: 0f).coerceIn(0f, 5f),
                                photoPath = photo,
                                latitude = point.first,
                                longitude = point.second,
                                timestamp = editing?.timestamp ?: System.currentTimeMillis(),
                                date = editing?.date ?: LocalDate.now().toString()
                            )
                        )
                        showForm = false
                    }
                }) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                Row {
                    editing?.let { poi ->
                        TextButton(onClick = {
                            viewModel.delete(poi)
                            showForm = false
                        }) {
                            Text("Hapus")
                        }
                    }
                    TextButton(onClick = { showForm = false }) {
                        Text("Batal")
                    }
                }
            }
        )
    }
}

private fun emptyFeatureCollection(): FeatureCollection =
    FeatureCollection.fromFeatures(emptyArray())

private fun updateMapSources(
    style: Style,
    state: MapUiState,
    filter: String,
    currentLocation: Pair<Double, Double>?
) {
    val visiblePois = state.pois.filter { filter == "Semua" || it.category == filter }
    (style.getSource(POI_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(
        FeatureCollection.fromFeatures(
            visiblePois.map { poi ->
                Feature.fromGeometry(
                    Point.fromLngLat(poi.longitude, poi.latitude),
                    JsonObject().apply { addProperty("poiId", poi.id) }
                )
            }.toTypedArray()
        )
    )

    (style.getSource(CHECKPOINT_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(
        FeatureCollection.fromFeatures(
            state.checkpoints.mapNotNull { checkpoint ->
                val latitude = checkpoint.latitude
                val longitude = checkpoint.longitude
                if (latitude == null || longitude == null) {
                    null
                } else {
                    Feature.fromGeometry(
                        Point.fromLngLat(longitude, latitude),
                        JsonObject().apply {
                            addProperty("number", checkpoint.number)
                            addProperty("steps", checkpoint.totalSteps)
                        }
                    )
                }
            }.toTypedArray()
        )
    )

    val recordedRoutePoints = state.routePoints.map { routePoint ->
        Point.fromLngLat(routePoint.longitude, routePoint.latitude)
    }
    val checkpointRoutePoints = state.checkpoints.mapNotNull { checkpoint ->
        val latitude = checkpoint.latitude
        val longitude = checkpoint.longitude
        if (latitude == null || longitude == null) null
        else Point.fromLngLat(longitude, latitude)
    }
    val routePoints = recordedRoutePoints.takeIf { it.size >= 2 } ?: checkpointRoutePoints
    val routeFeatures = if (routePoints.size >= 2) {
        arrayOf(Feature.fromGeometry(LineString.fromLngLats(routePoints)))
    } else {
        emptyArray()
    }
    (style.getSource(ROUTE_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(
        FeatureCollection.fromFeatures(routeFeatures)
    )

    val locationFeatures = currentLocation?.let { (latitude, longitude) ->
        arrayOf(Feature.fromGeometry(Point.fromLngLat(longitude, latitude)))
    } ?: emptyArray()
    (style.getSource(LOCATION_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(
        FeatureCollection.fromFeatures(locationFeatures)
    )
}
