package net.meshstar

import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.JUnitCore

/** Minimal runner for the real optimized target, without instrumentation-only tracing dependencies. */
class A6ReleaseTestRunner: Instrumentation() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl,Application::class.java.name,context)
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        InstrumentationRegistry.registerInstance(this,arguments?:Bundle())
        start()
    }
    override fun onStart() {
        val result=JUnitCore.runClasses(Codec2InstrumentedTest::class.java,KeystoreInstrumentedTest::class.java,
            com.bitchat.android.ui.BarcodeScannerInstrumentedTest::class.java)
        val report=Bundle().apply { putString("stream",buildString {
            append("Tests run: ${result.runCount}, failures: ${result.failureCount}\n")
            result.failures.forEach { append(it.trace).append('\n') }
        }) }
        finish(if(result.wasSuccessful())-1 else 0,report)
    }
}
