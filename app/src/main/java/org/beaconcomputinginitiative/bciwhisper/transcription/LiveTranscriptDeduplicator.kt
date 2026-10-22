/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid Live Transcript De-duplication
 * Developer Seal: CJB-DEV-2026
 */
package org.beaconcomputinginitiative.bciwhisper.transcription

class LiveTranscriptDeduplicator(
    private val maxHistoryWords: Int = 48,
    private val maxOverlapWords: Int = 18
) {
    private val history =
        ArrayList<String>()

    @Synchronized
    fun reset() {
        history.clear()
    }

    @Synchronized
    fun emitNovel(
        text: String
    ): String {
        val words =
            text
                .trim()
                .split(
                    Regex("\\s+")
                )
                .filter {
                    it.isNotBlank()
                }

        if (words.isEmpty()) {
            return ""
        }

        val historyNormalized =
            history.map(
                ::normalize
            )

        val wordsNormalized =
            words.map(
                ::normalize
            )

        val maxOverlap =
            minOf(
                maxOverlapWords,
                history.size,
                words.size
            )

        var overlap =
            0

        for (
            candidate in
            maxOverlap downTo 1
        ) {
            var matches =
                true

            for (
                index in
                0 until candidate
            ) {
                val oldToken =
                    historyNormalized[
                        historyNormalized.size -
                            candidate +
                            index
                    ]

                val newToken =
                    wordsNormalized[
                        index
                    ]

                if (
                    oldToken !=
                    newToken
                ) {
                    matches =
                        false
                    break
                }
            }

            if (matches) {
                overlap =
                    candidate
                break
            }
        }

        val novelWords =
            words.drop(
                overlap
            )

        if (
            novelWords.isEmpty()
        ) {
            return ""
        }

        history.addAll(
            novelWords
        )

        while (
            history.size >
            maxHistoryWords
        ) {
            history.removeAt(
                0
            )
        }

        return novelWords
            .joinToString(
                " "
            )
            .trim()
    }

    private fun normalize(
        token: String
    ): String {
        val stripped =
            token
                .lowercase()
                .filter {
                    it.isLetterOrDigit() ||
                        it == '\''
                }

        return stripped
            .ifBlank {
                token.lowercase()
            }
    }
}
