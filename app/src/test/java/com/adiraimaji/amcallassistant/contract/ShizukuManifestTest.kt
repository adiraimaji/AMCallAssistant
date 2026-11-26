package com.adiraimaji.amcallassistant.contract

import org.junit.Assert.assertEquals
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Shizuku and its forks only hand their binder to a client whose provider declares
 * INTERACT_ACROSS_USERS_FULL, and only list that client in their authorised-apps
 * screen. Without it the app is invisible until a permission request happens to
 * register it (Shizuku-Next's server filters on this permission when delivering
 * the binder and building the app list).
 */
class ShizukuManifestTest {

    @Test
    fun shizukuProviderDeclaresThePermissionShizukuLooksFor() {
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(Repo.file("app/src/main/AndroidManifest.xml"))
        val providers = document.getElementsByTagName("provider")
        val shizuku = (0 until providers.length).map { providers.item(it) }
            .firstOrNull { it.attributes.getNamedItemNS(ANDROID, "name")?.nodeValue == "rikka.shizuku.ShizukuProvider" }
            ?: error("ShizukuProvider is not declared")

        assertEquals(
            "android.permission.INTERACT_ACROSS_USERS_FULL",
            shizuku.attributes.getNamedItemNS(ANDROID, "permission")?.nodeValue,
        )
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
