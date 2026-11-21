package com.riffle.app.launcher

import android.content.Context
import android.content.Intent
import com.riffle.app.MainActivity

/**
 * A plain request for Home: `ACTION_MAIN` + `CATEGORY_HOME` and nothing else. Deliberately no component, no package and
 * no other category: the system resolves it to the default Home, and an activity started from exactly such an intent is
 * given the home activity type, so its task is the home task the system's Home gesture and Recents animate. (An
 * explicit component, or a second category, makes the task a standard one.)
 */
internal fun implicitHomeIntent(): Intent =
    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** The app opened explicitly, as its icon did before the entry activity: for when it is not the default Home. */
internal fun openAppIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
