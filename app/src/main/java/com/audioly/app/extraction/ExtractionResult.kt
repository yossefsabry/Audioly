package com.audioly.app.extraction

sealed class ExtractionResult {
    data class Success(val streamInfo: StreamInfo) : ExtractionResult()
    sealed class Failure : ExtractionResult() {
        data class InvalidUrl(val url: String) : Failure()
        data class VideoUnavailable(val videoId: String) : Failure()
        data class AgeRestricted(val videoId: String) : Failure()
        data class NetworkError(val cause: Throwable) : Failure()
        data class ExtractionFailed(val cause: Throwable) : Failure()
    }
}
