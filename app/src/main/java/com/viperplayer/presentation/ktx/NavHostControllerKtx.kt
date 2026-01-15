package com.viperplayer.presentation.ktx

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.Navigator

fun NavHostController.popBackStackSafe(): Boolean {
    return if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        popBackStack()
    } else {
        false
    }
}

fun <T : Any> NavHostController.navigateSafe(
    route: T,
    navOptions: NavOptions? = null,
    navigatorExtras: Navigator.Extras? = null
) {
//    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        navigate(
            route = route,
            navOptions = navOptions,
            navigatorExtras = navigatorExtras
        )
//    }
}
