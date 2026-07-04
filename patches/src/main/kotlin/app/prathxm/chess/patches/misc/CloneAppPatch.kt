/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.patches.misc

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import org.w3c.dom.Element
import app.prathxm.chess.patches.shared.Constants.COMPATIBILITY_CHESS

val cloneAppPatch = resourcePatch(
    name = "Clone Chess.com",
    description = "Changes the package name to com.chess.prathxm, allowing the patched app to be installed side-by-side with the original Chess.com app.",
    default = false
) {
    compatibleWith(COMPATIBILITY_CHESS)

    finalize {
        val packageName = packageMetadata.packageName
        val suffix = "prathxm"
        val newPackageName = "$packageName.$suffix"

        val providerStringResources = mutableSetOf<String>()

        document("AndroidManifest.xml").use { document ->
            document.documentElement.setAttribute("package", newPackageName)

            val permissions = document.getElementsByTagName("permission")
            val usesPermissions = document.getElementsByTagName("uses-permission")

            for (i in 0 until permissions.length) {
                val it = permissions.item(i) as? Element ?: continue
                val oldName = it.getAttribute("android:name")
                val newName = when {
                    oldName.startsWith('.') -> continue
                    oldName.startsWith("$packageName.") -> oldName.replaceFirst(packageName, newPackageName)
                    else -> "${newPackageName}_$oldName"
                }
                it.setAttribute("android:name", newName)

                for (j in 0 until usesPermissions.length) {
                    val usePerm = usesPermissions.item(j) as? Element ?: continue
                    if (usePerm.getAttribute("android:name") == oldName) {
                        usePerm.setAttribute("android:name", newName)
                        break
                    }
                }
            }

            val providers = document.getElementsByTagName("provider")

            for (i in 0 until providers.length) {
                val provider = providers.item(i) as? Element ?: continue
                val authorities = provider.getAttribute("android:authorities").split(';')
                val newAuthorities = authorities.map {
                    when {
                        it.startsWith("$packageName.") -> it.replaceFirst(packageName, newPackageName)
                        it.startsWith('@') -> {
                            providerStringResources.add(it.removePrefix("@string/"))
                            it
                        }
                        else -> "${newPackageName}_$it"
                    }
                }
                provider.setAttribute("android:authorities", newAuthorities.joinToString(";"))
            }
        }

        if (providerStringResources.isNotEmpty()) {
            document("res/values/strings.xml").use { document ->
                val children = document.documentElement.childNodes
                for (i in 0 until children.length) {
                    val node = children.item(i) as? Element ?: continue

                    if (node.getAttribute("name") in providerStringResources) {
                        val authority = node.textContent
                        node.textContent = if (authority.startsWith("$packageName.")) {
                            authority.replaceFirst(packageName, newPackageName)
                        } else {
                            "${newPackageName}_$authority"
                        }
                    }
                }
            }
        }
    }
}
