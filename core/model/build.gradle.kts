// Pure Kotlin. No Android dependency, by design: the domain models must be
// usable and testable without an emulator, and nothing below this module in the
// graph may pull Android in.
plugins {
    id("cleanarch.jvm.library")
}
