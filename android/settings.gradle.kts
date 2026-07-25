// Single-module for now. If the app grows past a handful of features, split
// along these lines (each becomes its own Gradle module/build.gradle.kts):
//   :core              — network/, data/, domain/, di/, datastore/ (this file's
//                         app/src/main/java/.../core/) — everything else depends on it
//   :feature-patient    — feature/patient/
//   :feature-practitioner — feature/practitioner/ (doctor role)
//   :feature-admin      — feature/admin/
//   :feature-lead       — feature/lead/ (self-signup "health-only mode" accounts)
//   :app                — MainActivity, PoshanApplication, AppNavGraph, ui/theme —
//                         depends on :core and every :feature-* module
// The trigger for splitting is usually build-time (incremental compilation
// scoped per module) or team size (parallel ownership without merge conflicts
// across features) — not code size alone.

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PoshanForLife"
include(":app")
