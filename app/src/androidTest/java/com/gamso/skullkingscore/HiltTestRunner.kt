package com.gamso.skullkingscore

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Starts the instrumented tests in [HiltTestApplication] instead of the app: the UI tests then get the test
 * modules of [TestDataModule] (a fresh in-memory database and settings for each test), never the app data.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(classLoader: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(classLoader, HiltTestApplication::class.java.name, context)
}
