package com.example.stepsnap.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.stepsnap.R
import com.example.stepsnap.domain.model.MapLocation
import com.example.stepsnap.domain.model.Poi
import com.example.stepsnap.domain.model.PoiCategory
import com.example.stepsnap.ui.theme.StepSnapColors
import com.google.gson.JsonObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val MAP_STYLE_URI = "https://tiles.openfreemap.org/styles/liberty"
private const val POI_SOURCE_ID = "poi-source"
private const val POI_LAYER_ID = "poi-layer"
private const val ROUTE_SOURCE_ID = "stepsnap-route"
private const val ROUTE_LAYER_ID = "stepsnap-route-line"
private const val LOCATION_SOURCE_ID = "stepsnap-current-location"
private const val LOCATION_LAYER_ID = "stepsnap-current-location-dot"

@Composable
fun MapSection(
    modifier: Modifier = Modifier,
    pois: List<Poi>,
    currentLocation: MapLocation?,
    routePoints: List<MapLocation>,
    fabPaddingBottom: androidx.compose.ui.unit.Dp = 0.dp,
    onOpenSetPointDialog: (Double, Double) -> Unit,
    onPoiSelected: (Long) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewState = remember { mutableStateOf<MapView?>(null) }
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var loadedStyle by remember { mutableStateOf<Style?>(null) }
    var centeredOnUser by remember { mutableStateOf(false) }
    var previousPoiCount by remember { mutableStateOf(pois.size) }

    val latestPois = rememberUpdatedState(pois)
    val latestLocation = rememberUpdatedState(currentLocation)
    val latestRoute = rememberUpdatedState(routePoints)
    val attribution = stringResource(R.string.map_attribution)

    LaunchedEffect(mapLibreMap, currentLocation) {
        if (!centeredOnUser && currentLocation != null) {
            mapLibreMap?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(currentLocation.latitude, currentLocation.longitude),
                    16.0
                ),
                500
            )
            centeredOnUser = true
        }
    }

    LaunchedEffect(pois, loadedStyle) {
        val style = loadedStyle ?: return@LaunchedEffect
        updateMapSources(
            style = style,
            pois = pois,
            currentLocation = currentLocation,
            routePoints = routePoints
        )
    }

    LaunchedEffect(pois, mapLibreMap) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (pois.size > previousPoiCount) {
            val newestPoi = pois.maxByOrNull { it.createdAt }
            if (newestPoi != null) {
                Log.d("POI_MAP", "Animating camera to new POI: ${newestPoi.name} at (${newestPoi.latitude}, ${newestPoi.longitude})")
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(newestPoi.latitude, newestPoi.longitude),
                        17.0
                    ),
                    1000
                )
            }
        }
        previousPoiCount = pois.size
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                if (!MapLibre.hasInstance()) {
                    MapLibre.getInstance(viewContext.applicationContext)
                }
                MapView(viewContext).apply {
                    onCreate(null)
                    mapViewState.value = this
                    getMapAsync { map ->
                        mapLibreMap = map
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(-7.981, 112.630))
                            .zoom(15.0)
                            .build()

                        map.addOnMapClickListener { point ->
                            val screenPoint = map.projection.toScreenLocation(point)
                            val density = viewContext.resources.displayMetrics.density
                            val boxSize = 24f * density
                            val touchBox = RectF(
                                screenPoint.x - boxSize,
                                screenPoint.y - boxSize,
                                screenPoint.x + boxSize,
                                screenPoint.y + boxSize
                            )
                            val features = try {
                                map.queryRenderedFeatures(touchBox, POI_LAYER_ID)
                            } catch (_: Exception) {
                                emptyList()
                            }
                            if (features.isNotEmpty()) {
                                val feature = features[0]
                                val poiId = try {
                                    feature.getNumberProperty("id")?.toLong()
                                } catch (_: Exception) {
                                    null
                                }
                                if (poiId != null && poiId > 0L) {
                                    Log.d("POI_MAP", "Tapped POI marker id=$poiId")
                                    onPoiSelected(poiId)
                                    true
                                } else {
                                    false
                                }
                            } else {
                                false
                            }
                        }

                        map.setStyle(Style.Builder().fromUri(MAP_STYLE_URI)) { style ->
                            Log.d("POI_MAP", "MapLibre style loaded successfully: $MAP_STYLE_URI")

                            style.addSource(GeoJsonSource(ROUTE_SOURCE_ID, emptyFeatureCollection()))
                            style.addSource(GeoJsonSource(LOCATION_SOURCE_ID, emptyFeatureCollection()))

                            // Route Layer
                            style.addLayer(
                                LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                                    lineColor(StepSnapColors.RouteBlueGray.toArgb()),
                                    lineWidth(5f)
                                )
                            )

                            // User Location Dot Layer (under POI layer)
                            style.addLayer(
                                CircleLayer(LOCATION_LAYER_ID, LOCATION_SOURCE_ID).withProperties(
                                    circleColor(StepSnapColors.RouteBlueGray.toArgb()),
                                    circleRadius(8f),
                                    circleStrokeColor(StepSnapColors.Card.toArgb()),
                                    circleStrokeWidth(2f)
                                )
                            )

                            // Setup POI Images, Source, and SymbolLayer
                            setupPoiLayer(viewContext, style)

                            loadedStyle = style
                            updateMapSources(
                                style = style,
                                pois = latestPois.value,
                                currentLocation = latestLocation.value,
                                routePoints = latestRoute.value
                            )
                        }
                    }
                }
            },
            update = { view ->
                if (mapViewState.value !== view) mapViewState.value = view
                loadedStyle?.let { style ->
                    updateMapSources(
                        style = style,
                        pois = pois,
                        currentLocation = currentLocation,
                        routePoints = routePoints
                    )
                }
            }
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(StepSnapColors.Card.copy(alpha = 0.92f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(StepSnapColors.Lime),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_app_logo),
                    contentDescription = null,
                    tint = StepSnapColors.Ink,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = stringResource(R.string.brand_name),
                modifier = Modifier.padding(start = 8.dp),
                fontWeight = FontWeight.Bold,
                color = StepSnapColors.Ink
            )
        }

        Text(
            text = attribution,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = fabPaddingBottom + 8.dp, start = 8.dp),
            color = StepSnapColors.Ink,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall
        )

        FloatingActionButton(
            onClick = {
                val target = mapLibreMap?.cameraPosition?.target
                val lat = currentLocation?.latitude ?: target?.latitude ?: -7.9811
                val lng = currentLocation?.longitude ?: target?.longitude ?: 112.6305
                onOpenSetPointDialog(lat, lng)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = fabPaddingBottom + 16.dp, end = 16.dp),
            shape = RoundedCornerShape(14.dp),
            containerColor = StepSnapColors.Lime,
            contentColor = StepSnapColors.Ink
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.add_spot)
            )
        }
    }

    val mapView = mapViewState.value
    DisposableEffect(mapView, lifecycleOwner) {
        if (mapView == null) {
            onDispose {}
        } else {
            var started = false
            var resumed = false
            var destroyed = false

            fun startView() {
                if (!started && !destroyed) {
                    mapView.onStart()
                    started = true
                }
            }

            fun resumeView() {
                startView()
                if (!resumed && !destroyed) {
                    mapView.onResume()
                    resumed = true
                }
            }

            fun pauseView() {
                if (resumed && !destroyed) {
                    mapView.onPause()
                    resumed = false
                }
            }

            fun stopView() {
                pauseView()
                if (started && !destroyed) {
                    mapView.onStop()
                    started = false
                }
            }

            fun destroyView() {
                stopView()
                if (!destroyed) {
                    mapView.onDestroy()
                    destroyed = true
                }
            }

            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> startView()
                    Lifecycle.Event.ON_RESUME -> resumeView()
                    Lifecycle.Event.ON_PAUSE -> pauseView()
                    Lifecycle.Event.ON_STOP -> stopView()
                    Lifecycle.Event.ON_DESTROY -> destroyView()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                startView()
            }
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                resumeView()
            }

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                destroyView()
            }
        }
    }
}

private fun setupPoiLayer(context: Context, style: Style) {
    if (style.getSource(POI_SOURCE_ID) != null) {
        Log.d("POI_MAP", "setupPoiLayer: POI source already exists, skipping initialization")
        return
    }

    Log.d("POI_MAP", "setupPoiLayer: Registering POI pin bitmaps and layers")

    // 1. Add Bitmaps BEFORE creating layers
    val bitmapShopping = createPinBitmap(context, StepSnapColors.PoiShopping.toArgb(), R.drawable.ic_cart)
    val bitmapPhoto = createPinBitmap(context, StepSnapColors.PoiPhoto.toArgb(), R.drawable.ic_camera)
    val bitmapPark = createPinBitmap(context, StepSnapColors.PoiPark.toArgb(), R.drawable.ic_tree)

    if (bitmapShopping != null) {
        style.addImage("pin_shopping", bitmapShopping)
        Log.d("POI_MAP", "Added image: pin_shopping")
    }
    if (bitmapPhoto != null) {
        style.addImage("pin_photo", bitmapPhoto)
        Log.d("POI_MAP", "Added image: pin_photo")
    }
    if (bitmapPark != null) {
        style.addImage("pin_park", bitmapPark)
        Log.d("POI_MAP", "Added image: pin_park")
    }

    // 2. Add GeoJsonSource
    val poiSource = GeoJsonSource(POI_SOURCE_ID, emptyFeatureCollection())
    style.addSource(poiSource)
    Log.d("POI_MAP", "Added source: $POI_SOURCE_ID")

    // 3. Add SymbolLayer on TOP
    val symbolLayer = SymbolLayer(POI_LAYER_ID, POI_SOURCE_ID).withProperties(
        iconImage(Expression.get("icon")),
        iconAnchor(Property.ICON_ANCHOR_BOTTOM),
        iconAllowOverlap(true),
        iconIgnorePlacement(true),
        iconSize(1.0f),
        textField(Expression.get("name")),
        textOffset(arrayOf(0f, 0.8f)),
        textAnchor(Property.TEXT_ANCHOR_TOP),
        textSize(12f),
        textColor(StepSnapColors.Ink.toArgb()),
        textHaloColor(StepSnapColors.Card.toArgb()),
        textHaloWidth(1.5f),
        PropertyFactory.textAllowOverlap(false),
        PropertyFactory.textOptional(true),
        PropertyFactory.textFont(arrayOf("Noto Sans Regular"))
    )

    style.addLayer(symbolLayer)
    Log.d("POI_MAP", "Added layer: $POI_LAYER_ID on top of style layers")
}

private fun createPinBitmap(
    context: Context,
    colorInt: Int,
    @DrawableRes iconRes: Int
): Bitmap? {
    val density = context.resources.displayMetrics.density
    val width = (42f * density).toInt().coerceAtLeast(80)
    val height = (52f * density).toInt().coerceAtLeast(100)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val cx = width / 2f
    val r = width / 2f - 4f
    val cy = r + 4f
    val tipY = height - 4f

    // Pin shape path (Circle top + pointed tail facing down)
    val pinPath = Path().apply {
        moveTo(cx, tipY)
        cubicTo(cx - r * 1.05f, cy + r * 0.75f, 4f, cy + r * 0.4f, 4f, cy)
        arcTo(RectF(4f, 4f, width - 4f, cy + r - 4f), 180f, 180f, false)
        cubicTo(width - 4f, cy + r * 0.4f, cx + r * 1.05f, cy + r * 0.75f, cx, tipY)
        close()
    }

    val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorInt
        style = Paint.Style.FILL
    }
    canvas.drawPath(pinPath, pinPaint)

    // Dark stroke outline
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(180, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    canvas.drawPath(pinPath, strokePaint)

    // Translucent white inner circle
    val innerRadius = r * 0.58f
    val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(70, 255, 255, 255)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, innerRadius, innerPaint)

    // White vector icon
    val drawable = ContextCompat.getDrawable(context, iconRes) ?: return bitmap
    val iconSize = (innerRadius * 1.35f).toInt()
    val left = (cx - iconSize / 2f).toInt()
    val top = (cy - iconSize / 2f).toInt()
    drawable.setBounds(left, top, left + iconSize, top + iconSize)
    drawable.setTint(android.graphics.Color.WHITE)
    drawable.draw(canvas)

    return bitmap
}

private fun emptyFeatureCollection(): FeatureCollection =
    FeatureCollection.fromFeatures(emptyArray())

private fun updateMapSources(
    style: Style,
    pois: List<Poi>,
    currentLocation: MapLocation?,
    routePoints: List<MapLocation>
) {
    Log.d("POI_MAP", "updateMapSources: Observed POIs count: ${pois.size}")
    pois.forEach { poi ->
        val iconKey = when (poi.category) {
            PoiCategory.SHOPPING -> "pin_shopping"
            PoiCategory.PHOTO_SPOT -> "pin_photo"
            PoiCategory.PARK -> "pin_park"
        }
        Log.d("POI_MAP", "POI id=${poi.id}, name=${poi.name}, category=${poi.category}, icon=$iconKey, lng=${poi.longitude}, lat=${poi.latitude}")
    }

    val poiFeatures = pois.map { poi ->
        val iconKey = when (poi.category) {
            PoiCategory.SHOPPING -> "pin_shopping"
            PoiCategory.PHOTO_SPOT -> "pin_photo"
            PoiCategory.PARK -> "pin_park"
        }
        Feature.fromGeometry(
            Point.fromLngLat(poi.longitude, poi.latitude),
            JsonObject().apply {
                addProperty("id", poi.id)
                addProperty("name", poi.name)
                addProperty("category", poi.category.name)
                addProperty("icon", iconKey)
            }
        )
    }
    (style.getSource(POI_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(
        FeatureCollection.fromFeatures(poiFeatures.toTypedArray())
    )

    val routeFeatures = if (routePoints.size >= 2) {
        val points = routePoints.map { Point.fromLngLat(it.longitude, it.latitude) }
        arrayOf(Feature.fromGeometry(LineString.fromLngLats(points)))
    } else {
        emptyArray()
    }
    (style.getSource(ROUTE_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(
        FeatureCollection.fromFeatures(routeFeatures)
    )

    val locationFeatures = currentLocation?.let { location ->
        arrayOf(
            Feature.fromGeometry(
                Point.fromLngLat(location.longitude, location.latitude)
            )
        )
    } ?: emptyArray()
    (style.getSource(LOCATION_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(
        FeatureCollection.fromFeatures(locationFeatures)
    )
}
