package com.gamso.skullkingscore.ui.feature.licenses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gamso.skullkingscore.R
import com.gamso.skullkingscore.ui.components.SkullScaffold
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.util.withJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The open source components the app is built with, grouped by project, with their license. The list is generated
 * from the dependencies at build time (AboutLibraries Gradle plugin) and read from a raw resource: it needs no
 * network at all.
 */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val projects by produceState(emptyList<Project>()) {
        value = withContext(Dispatchers.Default) {
            groupProjects(Libs.Builder().withJson(context, R.raw.aboutlibraries).build().toComponents())
        }
    }
    var opened by remember { mutableStateOf<Component?>(null) }

    SkullScaffold(title = stringResource(R.string.licenses_title), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.licenses_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(projects, key = { it.name }) { project ->
                ProjectCard(project, onOpen = { opened = it })
            }
        }
    }

    opened?.let { LicenseDialog(it, onDismiss = { opened = null }) }
}

/** A project: its line shows its name and licenses. A project of several artifacts unfolds to list them. */
@Composable
private fun ProjectCard(project: Project, onOpen: (Component) -> Unit) {
    var expanded by rememberSaveable(project.name) { mutableStateOf(false) }
    val single = project.components.singleOrNull()
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { if (single != null) onOpen(single) else expanded = !expanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    subtitle(project),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (single == null) {
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand),
                )
            }
        }
        if (expanded) {
            Column {
                project.components.forEach { component ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ComponentRow(component, onClick = { onOpen(component) })
                }
            }
        }
    }
}

/** The licenses of the project, then its number of components when it has several. */
@Composable
private fun subtitle(project: Project): String {
    val licenses = project.licenseNames.joinToString()
    val count = project.components.size
    return if (count > 1) {
        pluralStringResource(R.plurals.licenses_components, count, count) + " · " + licenses
    } else {
        licenses
    }
}

@Composable
private fun ComponentRow(component: Component, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(component.name, style = MaterialTheme.typography.bodyLarge)
        }
        component.version?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

/** The name and the full text of the license(s) of a component. */
@Composable
private fun LicenseDialog(component: Component, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(component.name) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                component.licenses.forEach { license ->
                    Text(license.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    license.text?.let { Text(it, fontSize = LICENSE_TEXT_SIZE.sp, fontFamily = FontFamily.Monospace) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

private const val LICENSE_TEXT_SIZE = 11
