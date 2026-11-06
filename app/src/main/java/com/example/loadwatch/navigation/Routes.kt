package com.example.loadwatch.navigation

import kotlinx.serialization.Serializable

// Type-safe routes for the screens S01 to S10. Later screens attach to these nodes without changing the graph's
// shape.

// Session wrapper
@Serializable data object SplashRoute

// sessionExpired shows a short explanation when the API ended the session and the app signed out
@Serializable data class LoginRoute(val sessionExpired: Boolean = false)

@Serializable data object AreaOnboardingRoute

// Hub and its children
@Serializable data object HomeRoute

// addFavourite opens S05 from S08 to save an area as a favourite instead of switching the active area
@Serializable data class AreaSelectorRoute(val addFavourite: Boolean = false)

@Serializable data object ScheduleRoute

// S10 Outage History opens from S06 Schedule, so it stays two taps from Home
@Serializable data object HistoryRoute

@Serializable data object ReportRoute

@Serializable data object CompareRoute

@Serializable data object SettingsRoute
