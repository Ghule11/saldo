# Project Instructions

This repository contains a private personal-finance application.

Architecture:
- Android client in /android
- Kotlin
- Jetpack Compose
- ASP.NET Core backend in /backend
- Open Banking will later use Enable Banking
- The Android application must never contain Enable Banking private API credentials
- Banking API access must go through the backend
- Secrets must never be committed to Git
- Prefer simple architecture over unnecessary abstractions
- Do not add dependencies without a concrete reason
- Keep Android UI code separate from networking and data logic
- Use Conventional Commits
- Run relevant builds and tests after changes
- Do not implement real banking access until explicitly requested

Initial milestone:
1. Android application runs on a physical Samsung device
2. Dashboard with fake financial data
3. Transaction list
4. Basic transaction categories
5. ASP.NET Core backend responds successfully
