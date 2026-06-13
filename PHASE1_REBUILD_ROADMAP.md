# Phase 1 Rebuild Roadmap

## Current Architecture Snapshot

- Single Android module: `:app`.
- UI is Compose-first with manual screen state in `MainActivity`.
- Network/data extraction is concentrated in `innertube/YouTubeApi.kt`, `InnerTubeClient.kt`, and `NewPipeUtils.kt`.
- App state is split between `YouTubeViewModel` SharedPreferences state and `LocalRepository` JSON files.
- Player, Home feed, Search, Shorts, Library, Playlists, Channel, and Subscriptions are currently tightly coupled through one shared `YouTubeViewModel`.

## Phase 1 Fixes Already Started

- Removed unused AppCompat and Navigation Compose dependencies.
- Removed Android Studio template tests and scratch extractor/reflection tests.
- Replaced the AppCompat XML theme parent with a platform no-action-bar Material parent.
- Removed stale manifest entry for missing `PlayerActivity`.
- Fixed search query state so typed suggestions update the shared query.
- Stabilized Home feed list keys and bottom paging trigger.
- Removed noisy success/debug logs from the player/channel API path.
- Reworked the first recommendation merge to prioritize recent watch history deterministically and dedupe merged results.
- Increased watch-history retention from 20 to 100 ids.
- Fixed Shorts duration parsing and deduped the Shorts feed.
- Loaded local repository data in Shorts before like/history operations.
- Scoped Shorts watch-history delay to the current page so swiping away cancels the write.
- Prevented Shorts from attaching a stale `playerState` item to the visible page.
- Replaced `GlobalScope` in the player quality-swap path with a composition coroutine scope.
- Fixed compile errors in player drag handling and Shorts comment list imports.

## Bugs Found During Audit

- Home fallback recommendation was random-topic based and could drift into unrelated content.
- Search input updated suggestions but did not update `_searchQuery` until submit.
- Home feed pagination trigger was tied directly to `layoutInfo` in a way that could fire repeatedly during recomposition.
- Shorts and regular Player share `playerState`, which can still cause cross-screen coupling in later phases.
- Shorts history/likes used `LocalRepository` without calling `loadData()`.
- Player quality swap used `GlobalScope`, risking delayed work after the composable leaves composition.
- Manifest declared `.PlayerActivity`, but no source class exists.
- XML theme depended on AppCompat even though the app is Compose-only.
- Scratch tests included reflection experiments, print debugging, network extractor calls, and one invalid-encoding file.
- API layer still has remaining warning-level deprecations around NewPipe extractor fields.
- Gradle warns that AGP 8.6.0 is tested only up to compileSdk 35 while the app uses compileSdk 36.
- Kotlin warns that `-Xcontext-receivers` should migrate to `-Xcontext-parameters`.

## Next Module Order

1. Home feed and recommendations
   - Move recommendation state out of raw watch-history ids and into a persistent interest profile.
   - Store watched title, channel, tags/keywords, duration progress, and last watched time.
   - Add ranked feed blending with caps for unrelated categories.
   - Add feed request cancellation and stale-response guards.

2. Player
   - Split regular player state from Shorts player state.
   - Introduce a playback/session manager for ExoPlayer ownership.
   - Persist watch progress and resume position.
   - Normalize quality/audio source creation into one reusable function.

3. Search
   - Add request cancellation for fast typing and stale search responses.
   - Add result type filtering, continuation ownership, and empty/error retry states.
   - Fix deprecated AutoMirrored icons in search/top bars.

4. Shorts
   - Use an independent Shorts player pipeline.
   - Preload next/previous short streams.
   - Add proper comments loading per short instead of reading shared regular-player comments.
   - Add share/more menu actions wired to real handlers.

5. Local data and library
   - Replace split SharedPreferences/JSON state with one consistent repository boundary.
   - Add queue, watch later, downloads, and playlist persistence models.
   - Add migration path for existing user data.

6. Cleanup and build health
   - Remove remaining unused imports after module refactors.
   - Fix deprecations from NewPipe field access and Compose icons/components.
   - Decide between compileSdk 35 or newer AGP with official compileSdk 36 support.
   - Enable configuration cache after plugin compatibility is checked.
