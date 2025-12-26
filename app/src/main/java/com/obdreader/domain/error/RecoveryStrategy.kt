package com.obdreader.domain.error

/**
 * Suggested recovery actions for errors.
 */
enum class RecoveryAction {
    NONE,
    PROMPT_ENABLE_BLUETOOTH,
    PROMPT_ENABLE_LOCATION,
    RETRY_SCAN,
    RETRY_CONNECTION,
    AUTO_RECONNECT,
    TRY_PROTOCOLS,
    SKIP_PID,
    RETRY_WITH_DELAY,
    RESET_ADAPTER,
    PROMPT_USER,
    WAKE_ADAPTER,
    INCREASE_TIMEOUT,
    RETRY_COMMAND,
    RETRY_OPERATION
}
