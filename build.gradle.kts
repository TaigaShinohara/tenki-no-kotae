plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.4.0" apply false
    // Screenshot tests (Robolectric + Roborazzi): provides the
    // recordRoborazzi*/verifyRoborazzi* tasks for the unit tests.
    id("io.github.takahirom.roborazzi") version "1.29.0" apply false
}
