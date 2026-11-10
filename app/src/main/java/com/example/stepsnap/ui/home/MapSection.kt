package com.example.stepsnap.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.toArgb
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
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.android.style.expressions.Expression

private const val MAP_STYLE_URI = "https://tiles.openfreemap.org/styles/liberty"
private const val POI_SOURCE_ID = "stepsnap-pois"
private const val POI_LAYER_ID = "stepsnap-poi-symbols"
private const val ROUTE_SOURCE_ID = "stepsnap-route"
private const val ROUTE_LAYER_ID = "stepsnap-route-line"
private const val LOCATION_SOURCE_ID = "stepsnap-current-location"
private const val LOCATION_LAYER_ID = "stepsnap-current-location-dot"

private fun PoiCategory.labelResource(): Int = when (this) {
    PoiCategory.SHOPPING -> R.string.category_shopping
    PoiCategory.PHOTO_SPOT -> R.string.category_photo_spot
    PoiCategory.PARK -> R.string.category_park
}

private fun PoiCategory.markerResource(): Int = when (this) {
    PoiCategory.SHOPPING -> R.drawable.ic_marker_shopping
    PoiCategory.PHOTO_SPOT -> R.drawable.ic_marker_camera
    PoiCategory.PARK -> R.drawable.ic_marker_park
}

private fun PoiCategory.markerImageId(): String = when (this) {
    PoiCategory.SHOPPING -> "stepsnap-shopping"
    PoiCategory.PHOTO_SPOT -> "stepsnap-camera"
    PoiCategory.PARK -> "stepsnap-park"
}

@Composable
fun MapSection(
    modifier: Modifier = Modifier,
    pois: List<Poi>,
    currentLocation: MapLocation?,
    routePoints: List<MapLocation>,
    fabPaddingBottom: androidx.compose.ui.unit.Dp = 0.dp,
    onOpenSetPointDialog: () -> Unit,
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

    LaunchedEffect(pois) {
        if (pois.size > previousPoiCount) {
            val newestPoi = pois.maxByOrNull { it.createdAt }
            if (newestPoi != null) {
                mapLibreMap?.easeCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(newestPoi.latitude, newestPoi.longitude),
                        16.0
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
                            val boxSize = 28f * density
                            val touchBox = android.graphics.RectF(
                                screenPoint.x - boxSize,
                                screenPoint.y - boxSize,
                                screenPoint.x + boxSize,
                                screenPoint.y + boxSize
                            )
                            val features = map.queryRenderedFeatures(touchBox, POI_LAYER_ID)
                            if (features.isNotEmpty()) {
                                val feature = features[0]
                                val poiId = try {
                                    feature.getNumberProperty("poiId")?.toLong()
                                } catch (_: Exception) {
                                    null
                                }
                                if (poiId != null && poiId > 0L) {
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
                            bitmapFromVector(viewContext, R.drawable.ic_poi_pin_info)?.let { bitmap ->
                                style.addImage("stepsnap-poi-pin", bitmap)
                            }
                            PoiCategory.entries.forEach { category ->
                                bitmapFromVector(viewContext, category.markerResource())?.let { bitmap ->
                                    style.addImage(category.markerImageId(), bitmap)
                                }
                            }
                            style.addSource(GeoJsonSource(POI_SOURCE_ID, emptyFeatureCollection()))
                            style.addSource(GeoJsonSource(ROUTE_SOURCE_ID, emptyFeatureCollection()))
                            style.addSource(GeoJsonSource(LOCATION_SOURCE_ID, emptyFeatureCollection()))

                            style.addLayer(
                                LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                                    lineColor(StepSnapColors.RouteBlueGray.toArgb()),
                                    lineWidth(5f)
                                )
                            )
                            style.addLayer(
                                SymbolLayer(POI_LAYER_ID, POI_SOURCE_ID).withProperties(
                                    iconImage(Expression.get("icon")),
                                    iconAllowOverlap(true),
                                    iconIgnorePlacement(true),
                                    iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                                    iconSize(0.85f),
                                    textField(Expression.get("name")),
                                    textSize(12f),
                                    textOffset(arrayOf(0f, 0.6f)),
                                    textColor(StepSnapColors.Ink.toArgb()),
                                    textHaloColor(StepSnapColors.Card.toArgb()),
                                    textHaloWidth(1.5f)
                                )
                            )
                            style.addLayer(
                                CircleLayer(LOCATION_LAYER_ID, LOCATION_SOURCE_ID).withProperties(
                                    circleColor(StepSnapColors.RouteBlueGray.toArgb()),
                                    circleRadius(8f),
                                    circleStrokeColor(StepSnapColors.Card.toArgb()),
                                    circleStrokeWidth(2f)
                                )
                            )
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
            onClick = onOpenSetPointDialog,
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

private fun emptyFeatureCollection(): FeatureCollection =
    FeatureCollection.fromFeatures(emptyArray())

private fun updateMapSources(
    style: Style,
    pois: List<Poi>,
    currentLocation: MapLocation?,
    routePoints: List<MapLocation>
) {
    val poiFeatures = pois.map { poi ->
        Feature.fromGeometry(
            Point.fromLngLat(poi.longitude, poi.latitude),
            JsonObject().apply {
                addProperty("poiId", poi.id)
                addProperty("name", poi.name)
                addProperty("icon", "stepsnap-poi-pin")
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

private fun bitmapFromVector(
    context: Context,
    @DrawableRes drawableResource: Int
): Bitmap? {
    val drawable = ContextCompat.getDrawable(context, drawableResource) ?: return null
    val density = context.resources.displayMetrics.density
    val width = (48f * density).toInt().coerceAtLeast(1)
    val height = (48f * density).toInt().coerceAtLeast(1)
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        drawable.setBounds(0, 0, width, height)
        drawable.draw(Canvas(bitmap))
    }
}
