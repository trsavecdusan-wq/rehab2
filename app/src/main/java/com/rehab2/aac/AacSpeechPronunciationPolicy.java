package com.rehab2.aac;

/** Shared generation instructions and cache identity for Slovenian speech. */
public final class AacSpeechPronunciationPolicy {
    public static final String SLOVENIAN_INSTRUCTIONS =
            "Speak in standard Slovenian (sl-SI), as a native Slovenian speaker. " +
            "Use natural Slovenian word stress, vowel pronunciation and sentence intonation. " +
            "Read the supplied text exactly, clearly and calmly, as one connected utterance.";

    private AacSpeechPronunciationPolicy() {}

    public static String cacheIdentity(String languageCode, String legacyIdentity) {
        if ("sl".equals(languageCode)) {
            return legacyIdentity + "|" + SLOVENIAN_INSTRUCTIONS;
        }
        return legacyIdentity;
    }
}
