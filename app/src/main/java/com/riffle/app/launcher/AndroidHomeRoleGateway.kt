package com.riffle.app.launcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.riffle.core.domain.launcher.HomeRoleStatus

class AndroidHomeRoleGateway(
    private val context: Context,
) {
    fun getHomeRoleStatus(): HomeRoleStatus =
        homeRoleStatus(
            roleManagerStatus = homeRoleStatusFromRoleManager(),
            resolvedDefaultHomeStatus =
                homeRoleStatusFromResolvedDefaultHome(
                    appPackageName = context.packageName,
                    resolvedDefaultHomePackageName = resolveDefaultHomePackageName(),
                ),
        )

    fun createHomeRoleRequestIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)
                ?.takeIf { roleManager -> roleManager.isRoleAvailable(RoleManager.ROLE_HOME) }
                ?.createRequestRoleIntent(RoleManager.ROLE_HOME)
                ?: Intent(Settings.ACTION_HOME_SETTINGS)
        } else {
            Intent(Settings.ACTION_HOME_SETTINGS)
        }

    private fun homeRoleStatusFromRoleManager(): HomeRoleStatus? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)
                ?.let { roleManager ->
                    val isHomeRoleAvailable = roleManager.isRoleAvailable(RoleManager.ROLE_HOME)
                    homeRoleStatusFromRoleManager(
                        isHomeRoleAvailable = isHomeRoleAvailable,
                        isHomeRoleHeld =
                            isHomeRoleAvailable &&
                                roleManager.isRoleHeld(RoleManager.ROLE_HOME),
                    )
                }
        } else {
            null
        }

    private fun resolveDefaultHomePackageName(): String? =
        context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName
}

internal fun homeRoleStatusFromRoleManager(
    isHomeRoleAvailable: Boolean,
    isHomeRoleHeld: Boolean,
): HomeRoleStatus? =
    when {
        !isHomeRoleAvailable -> null
        isHomeRoleHeld -> HomeRoleStatus.DEFAULT_HOME
        else -> HomeRoleStatus.NOT_DEFAULT_HOME
    }

internal fun homeRoleStatusFromResolvedDefaultHome(
    appPackageName: String,
    resolvedDefaultHomePackageName: String?,
): HomeRoleStatus =
    when (resolvedDefaultHomePackageName) {
        in systemPermissionPackageCandidates(appPackageName) -> HomeRoleStatus.DEFAULT_HOME
        null -> HomeRoleStatus.UNKNOWN
        else -> HomeRoleStatus.NOT_DEFAULT_HOME
    }

internal fun homeRoleStatus(
    roleManagerStatus: HomeRoleStatus?,
    resolvedDefaultHomeStatus: HomeRoleStatus,
): HomeRoleStatus =
    when {
        roleManagerStatus == HomeRoleStatus.DEFAULT_HOME -> HomeRoleStatus.DEFAULT_HOME
        resolvedDefaultHomeStatus == HomeRoleStatus.DEFAULT_HOME -> HomeRoleStatus.DEFAULT_HOME
        roleManagerStatus != null -> roleManagerStatus
        else -> resolvedDefaultHomeStatus
    }
