package com.example.stepsnap.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.stepsnap.R
import com.example.stepsnap.ui.theme.StepSnapColors
import java.text.NumberFormat
import java.util.Locale

@Composable
fun StepProgress(
    steps: Long,
    target: Long,
    modifier: Modifier = Modifier
) {
    val progress = if (target > 0) (steps.toFloat() / target).coerceIn(0f, 1f) else 0f
    val numberFormat = rememberNumberFormat()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.DirectionsWalk,
            contentDescription = stringResource(R.string.step_progress_description),
            tint = StepSnapColors.Ink,
            modifier = Modifier.size(44.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp)),
                color = StepSnapColors.Teal,
                trackColor = StepSnapColors.Lime
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(
                        R.string.today_steps,
                        numberFormat.format(steps)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = StepSnapColors.Ink
                )
                Text(
                    text = stringResource(
                        R.string.target_steps,
                        numberFormat.format(target)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = StepSnapColors.Ink
                )
            }
        }
    }
}

@Composable
private fun rememberNumberFormat(): NumberFormat {
    return androidx.compose.runtime.remember {
        NumberFormat.getIntegerInstance(Locale("id", "ID"))
    }
}

@Preview(showBackground = true)
@Composable
private fun StepProgressPreview() {
    com.example.stepsnap.ui.theme.StepSnapTheme {
        StepProgress(steps = 180, target = 1_000)
    }
}
