package com.gamso.skullkingscore.ui.feature.licenses

import kotlin.test.assertEquals
import org.junit.Test

class OpenSourceComponentsTest {

    private val apache = ComponentLicense("Apache License 2.0", "text")

    private fun component(id: String, name: String, repository: String?, license: ComponentLicense = apache) =
        Component(id, name, "1.0", repository, listOf(license))

    @Test
    fun theArtifactsOfOneGithubRepositoryFormOneProject() {
        val projects = groupProjects(
            listOf(
                component("a:coroutines-core", "kotlinx-coroutines-core", "https://github.com/Kotlin/coroutines"),
                component("a:coroutines-jvm", "kotlinx-coroutines-jvm", "https://github.com/Kotlin/coroutines/"),
            ),
        )
        assertEquals(1, projects.size)
        assertEquals("kotlinx-coroutines", projects.single().name)
        assertEquals(2, projects.single().components.size)
    }

    @Test
    fun theAndroidArtifactsFormOneProjectWhateverTheirAddress() {
        val projects = groupProjects(
            listOf(
                component("androidx.core:core", "Core", "https://cs.android.com/androidx/platform/frameworks/support"),
                component("androidx.compose.ui:ui", "Compose UI", "https://developer.android.com/jetpack"),
            ),
        )
        assertEquals(listOf("AndroidX and Jetpack Compose"), projects.map { it.name })
    }

    @Test
    fun aComponentWithoutRepositoryIsAProjectOnItsOwn() {
        val projects = groupProjects(listOf(component("x:one", "One", null), component("x:two", "Two", "")))
        assertEquals(listOf("One", "Two"), projects.map { it.name })
    }

    @Test
    fun withoutCommonPrefixTheShortestNameIsKept() {
        val projects = groupProjects(
            listOf(
                component("g:dagger", "Dagger", "https://github.com/google/dagger"),
                component("g:hilt", "Hilt Android", "https://github.com/google/dagger"),
            ),
        )
        assertEquals(listOf("Dagger"), projects.map { it.name })
    }

    @Test
    fun theLicenseNamesOfAProjectHaveNoRepetition() {
        val mit = ComponentLicense("MIT License", null)
        val project = groupProjects(
            listOf(
                component("a:one", "one-a", "https://example.org/p", apache),
                component("a:two", "one-b", "https://example.org/p", apache),
                component("a:three", "one-c", "https://example.org/p", mit),
            ),
        ).single()
        assertEquals(listOf("Apache License 2.0", "MIT License"), project.licenseNames)
    }
}
