package com.phonehunger.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.phonehunger.app.billing.ProState

@Composable fun ProScreen(state: ProState, buy: () -> Unit, restore: () -> Unit) {
    Text(stringResource(R.string.pro), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.studio))
    Text(state.price ?: stringResource(R.string.unavailable))
    if (state.pending) Text(stringResource(R.string.purchase_pending))
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(onClick = buy, enabled = state.price != null && !state.owned, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.buy)) }
    TextButton(onClick = restore) { Text(stringResource(R.string.restore)) }
}
