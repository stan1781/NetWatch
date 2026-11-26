# NetWatch — release shrinking is off by default.
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
