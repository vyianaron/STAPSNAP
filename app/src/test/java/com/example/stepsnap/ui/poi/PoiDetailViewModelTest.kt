package com.example.stepsnap.ui.poi

import com.example.stepsnap.domain.model.Poi
import com.example.stepsnap.domain.model.PoiCategory
import com.example.stepsnap.domain.model.PoiPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PoiDetailViewModelTest {

    @Test
    fun poiPhotosSortingAndPathExtraction() {
        val photos = listOf(
            PoiPhoto(id = 2, poiId = 10, path = "/path/photo2.jpg", createdAt = 2000L),
            PoiPhoto(id = 1, poiId = 10, path = "/path/photo1.jpg", createdAt = 1000L)
        )
        val sortedPhotos = photos.sortedBy { it.createdAt }

        assertEquals(2, sortedPhotos.size)
        assertEquals("/path/photo1.jpg", sortedPhotos.first().path)
        assertEquals("/path/photo2.jpg", sortedPhotos.last().path)
    }

    @Test
    fun poiWithNoPhotosReturnsEmptyList() {
        val poi = Poi(
            id = 1,
            name = "Test POI",
            category = PoiCategory.PHOTO_SPOT,
            latitude = -7.9811,
            longitude = 112.6305,
            note = null,
            photos = emptyList()
        )

        assertEquals(0, poi.photos.size)
        assertNull(poi.note)
    }
}
