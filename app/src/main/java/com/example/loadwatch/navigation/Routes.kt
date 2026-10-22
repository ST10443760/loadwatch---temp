package com.example.loadwatch.navigation

import kotlinx.serialization.Serializable

// Type-safe routes for the nine Part 2 screens (S01 to S09). The Final POE screens S10 to S14 attach to
// these nodes later without changing the graph's shape.

// Session wrapper
@Serializable data object SplashRoute

// sessionExpired shows a short explanation when the API ended the session and the app signed out
@Serializable data class LoginRoute(val sessionExpired: Boolean = false)

@Serializable data object AreaOnboardingRoute

// Hub and its children
@Serializable data object HomeRoute

@Serializable data object AreaSelectorRoute

@Serializable data object ScheduleRoute

@Serializable data object ReportRoute

@Serializable data object CompareRoute

@Serializable data object SettingsRoute
