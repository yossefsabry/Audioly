package com.audioly.shared.extraction

data class StreamInfo(
    val videoId: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val audioStreamUrl: String,
    val subtitleTracks: List<SubtitleTrack>,
)
