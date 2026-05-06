import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.osmastic.PinLogical
import com.example.osmastic.StateUIViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinListDialog(
    manager: StateUIViewModel,
    pins: Set<PinLogical>
) {
    ModalBottomSheet(
        onDismissRequest = { manager.closeAnyModal() }
    ) {
        LazyColumn {
            items(pins.toList()) { pin ->
                PinListItem(
                    pin = pin,
                    onItemClick = {
                        manager.closeAnyModal()
                        // TODO: анимация к geoPoint
                    },
                    onConflictClick = {
                        manager.closeAnyModal()
                        // TODO: реброадкастинг
                    }
                )
            }
        }
    }
}

@Composable
private fun PinListItem(
    pin: PinLogical,
    onItemClick: () -> Unit,
    onConflictClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = pin.pinPhysProps.iconUnicode,
            fontSize = 24.sp
        )
        Text(
            text = pin.pinPhysProps.label.ifEmpty { "" },
            modifier = Modifier.weight(1f)
        )
        // TTL (оставшиеся минуты)
        Text(
            text = formatTTL(pin.expirationTimestamp)
        )
        Text(
            text = pin.pinLogicalId.toString()
        )
        // Конфликт-слот (кликабельный индикатор)
        Box(
            modifier = Modifier
                .size(24.dp)
                .clickable { onConflictClick() }
        ) {
            // TODO: пока крестик, потом условный рендеринг (✔ / ✗)
            Text("✔")
        }
    }
}

private fun formatTTL(timestamp: Long): String {
    if (timestamp == 0L) return "∞"
    val minutesLeft = (timestamp - System.currentTimeMillis()) / (1000 * 60)
    return if (minutesLeft <= 0) "0м" else "${minutesLeft}м"
}