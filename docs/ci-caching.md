# GitHub Actions build caching

The Android workflow uses a shared default-branch Gradle cache so pull-request builds can reuse dependencies and build tooling prepared by trusted builds on `main`.

## Gradle User Home

`gradle/actions/setup-gradle@v6` owns Gradle User Home caching. It caches downloaded Gradle distributions, dependencies, transformed artifacts, compiled build scripts, and the local build cache. The separate `actions/setup-java` Gradle cache is intentionally disabled to avoid two actions competing over the same Gradle directories.

The workflow sets:

```yaml
cache-read-only: ${{ github.ref != 'refs/heads/main' }}
```

This makes pull-request and feature-branch jobs restore cache entries without attempting to publish shared state. GitHub allows pull-request workflows to restore caches from their base branch, including the default branch, but PR-created caches are scoped to that PR merge ref and are not reusable by other branches. Keeping PR jobs read-only avoids spending cache storage on one-off PR scopes and prevents untrusted changes from publishing cache entries that a trusted main build will later restore.

When a PR is merged, the resulting `push` to `main` runs the normal test and APK build and writes a refreshed Gradle cache entry. The cache action uses commit-aware entries and reuses/deduplicates prior content, so updated dependency and transform state becomes available to later PRs without letting PR jobs write into the main cache.

## Cache warm-ups

The same workflow can also be started with `workflow_dispatch` on `main`, and it runs on a weekly schedule (Monday at 04:23 UTC). These runs execute the same unit-test and debug-APK tasks, populating the shared Gradle cache even during quiet development periods. Scheduled runs skip APK artifact upload.

A manual warm-up should be dispatched against `main` to publish to the shared default-branch cache. Dispatching against a feature branch remains read-only.

## Eruda npm archive

The Eruda 3.4.3 tarball is downloaded into `app/build/downloads`, outside Gradle User Home. It is cached separately with a key derived from the runner OS and `app/build.gradle.kts`. Pull-request builds can restore the base-branch archive. Only `main` saves this separate cache.

The Gradle download task verifies the archive's pinned SHA-512 integrity before use. This check is also performed against a restored archive; a missing or invalid cached archive is downloaded again. If the pinned version or integrity value changes, the Gradle build-file hash changes the cache key and the task retrieves the new package.

## Why PRs do not write the main cache directly

GitHub Actions deliberately separates cache scopes. A `pull_request` job can restore the base branch's cache but cannot write into that base branch's scope; any cache it saves belongs to its PR merge ref. Allowing untrusted PR code to publish into the default-branch cache would create a cache-poisoning path. Therefore, a PR contributes updated build state to the shared cache when it is merged and the trusted `push` workflow runs on `main`, not while the PR job itself is executing.

This gives PR checks fast access to the latest trusted main cache while keeping the shared cache writable only from the default branch.
