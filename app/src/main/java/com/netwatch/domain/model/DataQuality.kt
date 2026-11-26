package com.netwatch.domain.model

/**
 * How trustworthy a value is. Never upgrade ESTIMATED or UNAVAILABLE to REAL.
 */
enum class DataConfidence {
    /** Taken directly from a public system API without reinterpretation. */
    REAL,

    /** Derived by this app (for example, attributing device-wide mobile deltas
     *  to the default data SIM). Must be labeled in the UI. */
    ESTIMATED,

    /** The public API surface cannot provide this value. */
    UNAVAILABLE,
}

enum class DataScope {
    DEVICE_WIDE,
    ATTRIBUTED_TO_DEFAULT_DATA_SIM,
    PER_APP,
    HOTSPOT_SESSION,
    UNKNOWN,
}

enum class NetworkKind {
    MOBILE,
    WIFI,
    TOTAL,
}

enum class SimRole {
    PRIMARY,
    BACKUP,
}

enum class SimIdentificationStatus {
    IDENTIFIED,
    ABSENT,
    PERMISSION_REQUIRED,
    UNKNOWN,
}

enum class AlertType {
    HIGH_DOWNLOAD_SPEED,
    ROLLING_WINDOW,
    PLAN_USAGE,
}

/**
 * Why a value is missing. Never collapse these into a bare “不可用” in the UI.
 */
enum class MissingDataReason {
    /** Needed for NetworkStatsManager device-wide / per-app totals. */
    USAGE_ACCESS_MISSING,

    /** Permission is fine (or not required); this SIM has no attributed samples yet. */
    AWAITING_ATTRIBUTION,

    /** Public APIs cannot provide this, e.g. directed traffic or official per-subscription split. */
    API_UNSUPPORTED,
}
