// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Use alias from libs.versions.toml if available, otherwise use direct ID
    id("com.android.application") version "8.8.2" apply false // Specify version directly if no libs.toml
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false // Specify version directly
}
true