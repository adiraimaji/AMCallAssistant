package com.adiraimaji.amcallassistant.tasker

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * The shape Tasker expects back from an edit activity. Tasker replaces
 * %variables in the bundle values named by
 * `net.dinglisch.android.tasker.extras.VARIABLE_REPLACE_KEYS`, a
 * space-separated String stored *inside* the bundle
 * (TaskerPlugin.Setting.setVariableReplaceKeys). An Intent extra or a
 * String[] there is silently ignored, which is why the greeting used to be
 * spoken as "Battery percent BATT".
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [36])
class EditResultTest {

    @Test fun theBundleTellsTaskerWhichFieldMayHoldVariables() {
        val config = PluginContract.Config(
            kind = PluginContract.KIND_ANSWER,
            greetingText = "Battery %BATT and %LOC",
        )
        val bundle: Bundle = EditResult.bundleFor(config)

        assertEquals(
            PluginContract.FIELD_CONFIG,
            bundle.getString(PluginContract.EXTRA_VARIABLE_REPLACE_FIELDS),
        )
        // The greeting stays plain, so Tasker can see the variable names.
        assertEquals(config, PluginContract.Config.parse(bundle.getString(PluginContract.FIELD_CONFIG)))
    }
}
