package com.riffle.app.launcher

import com.riffle.app.launcher.exclusions.decodeExclusionRules
import com.riffle.app.launcher.exclusions.encodeExclusionRules
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.backup.WorkspaceBackup
import com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class LauncherBackupDocument(
    val homeLayoutSet: HomeLayoutSet,
    val launcherSettings: LauncherSettings,
    val hiddenAppIdentities: Set<AppIdentity> = emptySet(),
    val exportedAtEpochMillis: Long? = null,
    /** Lenses and workspaces only, never item content. Absent in backups written before workspaces. */
    val workspaceSet: WorkspaceSet? = null,
    /** Per-layout source exclusion rules (rule text only). Absent in backups written before they existed. */
    val exclusions: LayoutExclusionRules? = null,
)

fun encodeLauncherBackupDocument(document: LauncherBackupDocument): String =
    JSONObject()
        .put("type", LAUNCHER_BACKUP_DOCUMENT_TYPE)
        .put("version", LAUNCHER_BACKUP_DOCUMENT_VERSION)
        .put("exportedAtEpochMillis", document.exportedAtEpochMillis)
        .put("homeLayouts", JSONObject(encodeHomeLayoutSet(document.homeLayoutSet)))
        .put("settings", JSONObject(encodeLauncherSettings(document.launcherSettings)))
        .put("hiddenApps", JSONArray(encodeHiddenAppIdentities(document.hiddenAppIdentities)))
        .also { json ->
            WorkspaceBackup.exportSet(document.workspaceSet)
                ?.let { set -> json.put("workspaces", JSONObject(encodeWorkspaceSet(set))) }
            WorkspaceBackup.exportExclusions(document.exclusions)
                ?.let { rules -> json.put("exclusions", JSONObject(encodeExclusionRules(rules))) }
        }
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
            launcherSettings =
                json.optJSONObject("settings")?.let { decodeLauncherSettings(it.toString()) }
                    ?: LauncherSettings(),
            hiddenAppIdentities = json.optHiddenAppIdentities(),
            exportedAtEpochMillis = json.optLongOrNull("exportedAtEpochMillis"),
            // Workspace sections are best effort: a malformed or empty one reads as absent, never as a failure.
            workspaceSet = json.optWorkspaceSet(),
            exclusions = json.optExclusions(),
        )
    }.getOrElse { error ->
        when (error) {
            is IllegalArgumentException -> throw error
            else -> throw IllegalArgumentException("Invalid launcher backup document", error)
        }
    }

private fun JSONObject.optLongOrNull(name: String): Long? =
    when {
        !has(name) || isNull(name) -> null
        get(name) is Number -> getLong(name)
        else -> null
    }

private fun JSONObject.optWorkspaceSet(): WorkspaceSet? =
    runCatching {
        optJSONObject("workspaces")
            ?.let { decodeWorkspaceSet(it.toString()) }
            ?.let(WorkspaceBackup::restoreSet)
            ?.set
    }.getOrNull()

private fun JSONObject.optExclusions(): LayoutExclusionRules? =
    runCatching {
        optJSONObject("exclusions")
            ?.let { decodeExclusionRules(it.toString()) }
            ?.let(WorkspaceBackup::restoreExclusions)
    }.getOrNull()

private fun JSONObject.optHiddenAppIdentities(): Set<AppIdentity> =
    when {
        has("hiddenApps") && !isNull("hiddenApps") ->
            optJSONArray("hiddenApps")
                ?.let { array -> decodeHiddenAppIdentities(array.toString()) }
                .orEmpty()
        else -> emptySet()
    }

private const val LAUNCHER_BACKUP_DOCUMENT_TYPE = "riffleLauncherBackup"
private const val LAUNCHER_BACKUP_DOCUMENT_VERSION = 1
