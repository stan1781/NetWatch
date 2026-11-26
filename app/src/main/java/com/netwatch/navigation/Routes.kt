package com.netwatch.navigation

object Routes {
    const val Dashboard = "dashboard"
    const val Statistics = "statistics"
    const val Settings = "settings"
    const val SimPlan = "settings/sim/{slotIndex}"
    const val Realtime = "realtime"
    const val Diagnostics = "diagnostics"
    const val About = "about"

    fun simPlan(slotIndex: Int) = "settings/sim/$slotIndex"
}
