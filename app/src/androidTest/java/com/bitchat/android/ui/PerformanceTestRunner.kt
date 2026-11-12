package com.bitchat.android.ui

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Skip Application startup side effects; each fixture chooses the Activity it exercises. */
class PerformanceTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader,
        className: String,
        context: Context,
    ): Application = super.newApplication(cl, Application::class.java.name, context)
}
