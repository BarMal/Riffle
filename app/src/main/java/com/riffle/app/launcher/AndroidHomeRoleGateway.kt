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
        when (resolveDefaultHomePackageName()) {
            context.packageName -> HomeRoleStatus.DEFAULT_HOME
            null -> HomeRoleStatus.UNKNOWN
            else -> HomeRoleStatus.NOT_DEFAULT_HOME
        }

    fun createHomeRoleRequestIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)
                ?.takeIf { roleManager -> roleManager.isRoleAvailable(RoleManager.ROLE_HOME) }
                ?.createRequestRoleIntent(RoleManager.ROLE_HOME)
                ?: Intent(Settings.ACTION_HOME_SETTINGS)
        } else {
            Intent(Settings.ACTION_HOME_SETTINGS)
        }

    private fun resolveDefaultHomePackageName(): String? =
        context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName
}
