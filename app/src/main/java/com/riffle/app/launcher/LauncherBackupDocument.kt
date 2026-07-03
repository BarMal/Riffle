package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.json.JSONObject
import org.json.JSONTokener

data class LauncherBackupDocument(
    val homeLayoutSet: HomeLayoutSet,
    val launcherSettings: LauncherSettings,
)

fun encodeLauncherBackupDocument(document: LauncherBackupDocument): String =
    JSONObject()
        .put("type", LAUNCHER_BACKUP_DOCUMENT_TYPE)
        .put("version", LAUNCHER_BACKUP_DOCUMENT_VERSION)
        .put("homeLayouts", JSONObject(encodeHomeLayoutSet(document.homeLayoutSet)))
        .put("settings", JSONObject(encodeLauncherSettings(document.launcherSettings)))
        .toString()

fun decodeLauncherBackupDocument(value: String): LauncherBackupDocument =
    runCatching {
        val json = JSONObject(JSONTokener(value))
        require(json.optString("type") == LAUNCHER_BACKUP_DOCUMENT_TYPE) {
            "Unsupported launcher backup type"
        }
        require(json.optInt("version") == LAUNCHER_BACKUP_DOCUMENT_VERSION) {
            "Unsupported launcher backup version"
        }
        require(json.has("homeLayouts")) {
            "Launcher backup missing home layouts"
        }
        require(json.has("settings")) {
            "Launcher backup missing settings"
        }

        LauncherBackupDocument(
            homeLayoutSet = decodeHomeLayoutSet(json.getJSONObject("homeLayouts").toString()),
            launcherSettings = decodeLauncherSettings(json.getJSONObject("settings").toString()),
        )
    }.getOrElse { error ->
        when (error) {
            is IllegalArgumentException -> throw error
            else -> throw IllegalArgumentException("Invalid launcher backup document", error)
        }
    }

private const val LAUNCHER_BACKUP_DOCUMENT_TYPE = "riffleLauncherBackup"
private const val LAUNCHER_BACKUP_DOCUMENT_VERSION = 1
