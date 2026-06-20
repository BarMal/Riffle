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
    fun getHomeRoleStatus(): HomeRoleStatus {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolvedHome =
            context.packageManager.resolveActivity(
                homeIntent,
                PackageManager.MATCH_DEFAULT_ONLY,
            ) ?: return HomeRoleStatus.UNKNOWN

        return if (resolvedHome.activityInfo.packageName == context.packageName) {
            HomeRoleStatus.DEFAULT_HOME
        } else {
            HomeRoleStatus.NOT_DEFAULT_HOME
        }
    }

    fun createHomeRoleRequestIntent(): Intent {
        val roleManager =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.getSystemService(RoleManager::class.java)
            } else {
                null
            }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true
        ) {
            roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        } else {
            Intent(Settings.ACTION_HOME_SETTINGS)
        }
    }
}
