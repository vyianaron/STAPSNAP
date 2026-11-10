package com.example.stepsnap.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.stepsnap.R
import com.example.stepsnap.domain.model.DailyRecap
import com.example.stepsnap.ui.theme.StepSnapColors
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RecapList(
    entries: List<DailyRecap>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = StepSnapColors.MintRecap)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.recap_title),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = StepSnapColors.Ink,
                textAlign = TextAlign.Center
            )
            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.recap_empty),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = StepSnapColors.Muted
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(290.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(entries, key = { it.date.toEpochDay() }) { entry ->
                        RecapItem(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecapItem(entry: DailyRecap) {
    val dateFormatter = DateTimeFormatter.ofPattern(
        "EEEE d MMMM yyyy",
        Locale("id", "ID")
    )
    val numberFormat = NumberFormat.getIntegerInstance(Locale("id", "ID"))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, StepSnapColors.Lime, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = StepSnapColors.Card)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (entry.photoPath == null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(StepSnapColors.GrayThumbnail),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.recap_image_placeholder),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = StepSnapColors.Ink
                    )
                }
            } else {
                AsyncImage(
                    model = java.io.File(entry.photoPath),
                    contentDescription = stringResource(R.string.recap_photo_description),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.date.format(dateFormatter),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = StepSnapColors.Ink
                )
                Text(
                    text = stringResource(R.string.recap_steps, numberFormat.format(entry.steps)),
                    style = MaterialTheme.typography.bodySmall,
                    color = StepSnapColors.Ink
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RecapListPreview() {
    val today = java.time.LocalDate.now()
    com.example.stepsnap.ui.theme.StepSnapTheme {
        RecapList(DemoHomeData.recaps(today))
    }
}
