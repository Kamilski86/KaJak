package de.ca.rfidchecker.feature.language

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import de.ca.rfidchecker.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(onOpenDrawer: () -> Unit) {
    val languages = listOf(
        "English" to "en",
        "Deutsch" to "de"
    )
    val currentLocale = AppCompatDelegate.getApplicationLocales().get(0)?.language ?: "en"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.language), color = Color.Black) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(languages) { (label, code) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val appLocale = LocaleListCompat.forLanguageTags(code)
                            AppCompatDelegate.setApplicationLocales(appLocale)
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, color = Color.Black)
                    RadioButton(selected = currentLocale == code, onClick = null)
                }
            }
        }
    }
}
