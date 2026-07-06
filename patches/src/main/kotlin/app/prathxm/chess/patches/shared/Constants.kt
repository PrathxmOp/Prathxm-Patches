/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val COMPATIBILITY_CHESS = Compatibility(
        name = "Chess.com",
        packageName = "com.chess",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x81B64C, // Chess.com green
        targets = listOf(
            AppTarget(version = "4.9.49"),
            AppTarget(version = "4.9.49-googleplay"),
            AppTarget(version = "4.10.0"),
            AppTarget(version = "4.10.0-googleplay")
        )
    )
}
