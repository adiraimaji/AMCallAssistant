package com.adiraimaji.amcallassistant.tasker

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** What a Locale edit activity must return to the host (Tasker). */
object EditResult {
    /**
     * The bundle Tasker stores with the action. Per Tasker's own protocol
     * (TaskerPlugin.Setting.setVariableReplaceKeys), the names of the bundle
     * entries whose values may contain %variables are given as a
     * space-separated String stored *inside* this bundle, under
     * `net.dinglisch.android.tasker.extras.VARIABLE_REPLACE_KEYS`.
     *
     * Tasker then replaces every %variable in those values with its current
     * value each time the action fires, before handing the bundle to the fire
     * receiver. Putting the names on the result Intent instead (as an array)
     * does nothing: Tasker never reads them there.
     */
    fun bundleFor(config: PluginContract.Config): Bundle = Bundle().apply {
        putString(PluginContract.FIELD_CONFIG, config.serialise())
        putString(PluginContract.EXTRA_VARIABLE_REPLACE_FIELDS, PluginContract.FIELD_CONFIG)
    }

    fun send(activity: Activity, config: PluginContract.Config) {
        val result = Intent().apply {
            putExtra(PluginContract.EXTRA_BUNDLE, bundleFor(config))
            putExtra(PluginContract.EXTRA_BLURB, config.blurb())
        }
        activity.setResult(Activity.RESULT_OK, result)
        activity.finish()
    }

    fun cancel(activity: Activity) {
        activity.setResult(Activity.RESULT_CANCELED)
        activity.finish()
    }
}
