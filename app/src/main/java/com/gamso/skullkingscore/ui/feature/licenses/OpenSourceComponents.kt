package com.gamso.skullkingscore.ui.feature.licenses

import com.mikepenz.aboutlibraries.Libs

/** A license of a component: its name and, when known, its full text. */
data class ComponentLicense(val name: String, val text: String?)

/** An open source component (an artifact) the app is built with. */
data class Component(
    val id: String,
    val name: String,
    val version: String?,
    /** Where its sources live (source repository, else website): the artifacts of a project share it. */
    val repository: String?,
    val licenses: List<ComponentLicense>,
)

/** The components of one project, shown as one line: "kotlinx-coroutines" for its android, core and bom artifacts. */
data class Project(val name: String, val components: List<Component>) {
    /** The names of the licenses of the components, without repetition. */
    val licenseNames: List<String> get() = components.flatMap { it.licenses }.map { it.name }.distinct()
}

private const val ANDROIDX_PREFIX = "androidx."
private const val ANDROID_HOST = "android.com"
private const val ANDROIDX_KEY = "androidx"
private const val ANDROIDX_NAME = "AndroidX and Jetpack Compose"
private const val GITHUB_HOST = "github.com"
private const val GITHUB_PATH_SEGMENTS = 2
private const val MIN_NAME_LENGTH = 3

/**
 * Groups the artifacts by project, so that the 95 or so of the app come in about fifteen lines. A project is
 * identified by its source repository, which its artifacts share (a GitHub repository counts as one project
 * whatever the folder inside it). The Android artifacts all come from the same place, whatever their address.
 * The projects are sorted by name; a component without repository is a project on its own.
 */
fun groupProjects(components: List<Component>): List<Project> = components
    .groupBy { projectKey(it) }
    .map { (key, members) -> Project(projectName(key, members), members.sortedBy { it.name.lowercase() }) }
    .sortedBy { it.name.lowercase() }

private fun projectKey(component: Component): String {
    val repository = component.repository?.lowercase()?.takeIf { it.isNotBlank() } ?: return component.id
    if (component.id.startsWith(ANDROIDX_PREFIX) || ANDROID_HOST in repository) return ANDROIDX_KEY
    val address = repository.substringAfter("://").trimEnd('/').removeSuffix(".git")
    return if (address.startsWith(GITHUB_HOST)) {
        address.split('/').take(1 + GITHUB_PATH_SEGMENTS).joinToString("/")
    } else {
        address
    }
}

/**
 * The common beginning of the names of the artifacts ("kotlinx-coroutines" for "kotlinx-coroutines-android" and
 * "kotlinx-coroutines-core"), else the shortest name ("Dagger" for "Dagger" and "Hilt Android").
 */
private fun projectName(key: String, members: List<Component>): String {
    if (key == ANDROIDX_KEY) return ANDROIDX_NAME
    val names = members.map { it.name }
    if (names.size == 1) return names.single()
    val prefix = names.reduce { a, b -> a.commonPrefixWith(b) }.trimEnd('-', '_', '.', ' ')
    return if (prefix.length >= MIN_NAME_LENGTH) prefix else names.minBy { it.length }
}

/** The components listed by the AboutLibraries Gradle plugin at build time. */
fun Libs.toComponents(): List<Component> = libraries.map { library ->
    Component(
        id = library.uniqueId,
        name = library.name,
        version = library.artifactVersion,
        repository = library.scm?.url ?: library.website,
        licenses = library.licenses.map { ComponentLicense(it.name, it.licenseContent) },
    )
}
