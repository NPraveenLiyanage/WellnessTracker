# WellnessTracker Report

## Description
A simple MVVM Android app to track daily habits and moods. Data persists via SharedPreferences. Users mark habits complete, log moods, and visualize weekly mood trends. A home-screen widget shows today's habit progress.

## Features
- Habit tracking
  - Add, edit, delete habits for today
  - Mark complete with instant progress recalculation
  - Progress bar and percentage for the current day
  - Empty state when no habits exist
- Mood logging
  - Add mood via emoji and optional note
  - Share selected mood via implicit intent
  - Weekly trend line chart (last 7 days) using MPAndroidChart
- Navigation
  - BottomNavigationView switching between Habits, Mood, and Settings via NavController
- Persistence
  - SharedPreferences JSON-backed storage for habits (per-date) and all moods
  - Data survives process death and device restarts
- Home-screen widget
  - AppWidgetProvider (WellnessWidget) shows today's habit progress
  - Taps open app to Habits tab; auto-refreshes on app open and habit changes
- Optional sensor bonus
  - Accelerometer shake detection in HabitFragment auto-adds a neutral mood with note (debounced)
- Responsiveness and state
  - Rotation-safe RecyclerView scroll state and selection
  - Tablet (sw600dp) two-pane habit layout

## Screenshots
- habit_screen.png
- mood_screen.png
- settings_screen.png
- widget_preview.png

## Implementation Notes
- Architecture: MVVM with LiveData; persistence via SharedPreferences helper.
- Navigation: AndroidX Navigation with a single NavHost and bottom nav.
- Widget: wellness_widget_info.xml provider, RemoteViews layout widget_wellness.xml, and WellnessWidget.
- Code quality: clear names, minimal redundancy, and localized strings.

