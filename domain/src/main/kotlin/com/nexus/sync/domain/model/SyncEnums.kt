package com.nexus.sync.domain.model

enum class SyncStatus {
    SYNCED,
    PENDING_SYNC,
    IN_CONFLICT,
    ERROR
}

enum class SyncOperationType {
    CREATE,
    UPDATE,
    DELETE
}

enum class ConflictResolutionStrategy(val displayName: String) {
    LAST_WRITE_WINS("Last-Write-Wins"),
    SERVER_AUTHORITATIVE("Server-Authoritative")
}
