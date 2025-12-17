package com.twg.video.core.utils

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.margelo.nitro.video.HybridVideoPlayerSourceSpec
import com.margelo.nitro.video.PlayerTrack
import com.margelo.nitro.video.TrackType

@UnstableApi
object TrackUtils {
    fun getAvailableTextTracks(player: ExoPlayer, source: HybridVideoPlayerSourceSpec): Array<PlayerTrack> {
        return Threading.runOnMainThreadSync {
            val tracks = mutableListOf<PlayerTrack>()
            val currentTracks = player.currentTracks
            var globalTrackIndex = 0

            // Get all text tracks from the current player tracks (includes both built-in and external)
            for (trackGroup in currentTracks.groups) {
                if (trackGroup.type == C.TRACK_TYPE_TEXT) {
                    for (trackIndex in 0 until trackGroup.length) {
                        val format = trackGroup.getTrackFormat(trackIndex)
                        val trackId = format.id ?: "text-$globalTrackIndex"
                        val label = format.label ?: "Unknown ${globalTrackIndex + 1}"
                        val language = format.language
                        val isSelected = trackGroup.isTrackSelected(trackIndex)

                        val isExternal = trackId.startsWith("external-") == true

                        val finalTrackId = if (isExternal) "external-$globalTrackIndex" else trackId

                        tracks.add(
                            PlayerTrack(
                                id = finalTrackId,
                                label = label,
                                language = language,
                                selected = isSelected
                            )
                        )
                        
                        globalTrackIndex++
                    }
                }
            }

            tracks.toTypedArray()
        }
    }

    fun selectTrackById(
        player: ExoPlayer,
        type: TrackType,
        id: String?
    ) {
        return Threading.runOnMainThreadSync {
            val trackSelector = player.trackSelectionParameters.buildUpon()

            if (id == null || id.isEmpty()) {
                trackSelector.setTrackTypeDisabled(type, true)
                player.trackSelectionParameters = trackSelector.build()
                return@runOnMainThreadSync null
            }

            val currentTracks = player.currentTracks
            var trackFound = false
            var globalTrackIndex = 0

            for (trackGroup in currentTracks.groups) {
                if (trackGroup.type == type) {
                    for (trackIndex in 0 until trackGroup.length) {
                        val format = trackGroup.getTrackFormat(trackIndex)
                        val currentTrackId = format.id ?: "track-$type-$globalTrackIndex"
                        //val label = format.label ?: "Unknown ${globalTrackIndex + 1}"

                        val isExternal = currentTrackId.startsWith("external-") == true

                        val finalTrackId =
                            if (isExternal) "external-$globalTrackIndex" else currentTrackId

                        if (finalTrackId == id) {
                            // Enable this specific track
                            trackSelector.setTrackTypeDisabled(type, false)
                            trackSelector.setOverrideForType(
                                TrackSelectionOverride(
                                    trackGroup.mediaTrackGroup,
                                    listOf(trackIndex)
                                )
                            )

                            trackFound = true
                            break
                        }
                        
                        globalTrackIndex++
                    }
                    if (trackFound) {
                        break
                    }
                }
            }

            // Apply the track selection parameters regardless of whether we found a track
            player.trackSelectionParameters = trackSelector.build()
        }
    }

     fun selectTrackByIndex(
        player: ExoPlayer,
        type: TrackType,
        index: Int?
    ) {
        return Threading.runOnMainThreadSync {
            val trackSelector = player.trackSelectionParameters.buildUpon()

            if (index == null) {
                trackSelector.setTrackTypeDisabled(type, true)
                player.trackSelectionParameters = trackSelector.build()
                return@runOnMainThreadSync null
            }

            val currentTracks = player.currentTracks
            var trackFound = false
            var globalTrackIndex = 0

            for (trackGroup in currentTracks.groups) {
                if (trackGroup.type == type) {
                    for (trackIndex in 0 until trackGroup.length) {
                        if (index == globalTrackIndex) {
                            // Enable this specific track
                            trackSelector.setTrackTypeDisabled(type, false)
                            trackSelector.setOverrideForType(
                                TrackSelectionOverride(
                                    trackGroup.mediaTrackGroup,
                                    listOf(trackIndex)
                                )
                            )

                            trackFound = true
                            break
                        }
                        
                        globalTrackIndex++
                    }
                    if (trackFound) {
                        break
                    }
                }
            }

            // Apply the track selection parameters regardless of whether we found a track
            player.trackSelectionParameters = trackSelector.build()
        }
    }

    fun getSelectedTrack(player: ExoPlayer, source: HybridVideoPlayerSourceSpec): PlayerTrack? {
        return Threading.runOnMainThreadSync {
            val currentTracks = player.currentTracks
            var globalTrackIndex = 0

            // Find the currently selected text track
            for (trackGroup in currentTracks.groups) {
                if (trackGroup.type == C.TRACK_TYPE_TEXT && trackGroup.isSelected) {
                    for (trackIndex in 0 until trackGroup.length) {
                        if (trackGroup.isTrackSelected(trackIndex)) {
                            val format = trackGroup.getTrackFormat(trackIndex)
                            val trackId = format.id ?: "text-$globalTrackIndex"
                            val label = format.label ?: "Unknown ${globalTrackIndex + 1}"
                            val language = format.language

                            val isExternal = trackId.startsWith("external-") == true

                            val finalTrackId = if (isExternal) "external-$globalTrackIndex" else trackId

                            return@runOnMainThreadSync PlayerTrack(
                                id = finalTrackId,
                                label = label,
                                language = language,
                                selected = true
                            )
                        }
                        globalTrackIndex++
                    }
                } else if (trackGroup.type == C.TRACK_TYPE_TEXT) {
                    // Still need to increment global index for non-selected text track groups
                    globalTrackIndex += trackGroup.length
                }
            }

            null
        }
    }
}
