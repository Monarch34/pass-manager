package com.passmanager.kit

/**
 * The framework's own identity, and the reason this file exists at all.
 *
 * Kotlin/Native will not link a module that has no sources of its own. An umbrella whose
 * entire job is to re-export other modules therefore produces nothing: every
 * `linkReleaseFramework*` task reports NO-SOURCE, `assemble…XCFramework` reports
 * UP-TO-DATE because there was nothing to do, and the whole build stays green while
 * producing no framework. One declaration here is what turns the umbrella into something
 * that actually links.
 *
 * It is not only ballast. Swift gets one symbol it can read without touching a vault, which
 * is the cheapest possible check that the framework in the app bundle is the one that was
 * built — a stale `.xcframework` copied into an Xcode project otherwise looks identical to
 * a fresh one until behaviour differs.
 */
object PassManagerKit {

    /**
     * Matches the application version. Deliberately a plain constant rather than something
     * read from a build-generated file: this is compiled into the binary, so it describes
     * the framework the app is actually linked against rather than whatever the build
     * directory happened to contain at packaging time.
     */
    const val VERSION: String = "2.0.0"
}
