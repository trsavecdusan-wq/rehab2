package com.rehab2.aac;

/** Shared generation instructions and cache identity for Slovenian speech. */
public final class AacSpeechPronunciationPolicy {
    public static final String SLOVENIAN_INSTRUCTIONS =
            "Speak in standard Slovenian (sl-SI), as a native Slovenian speaker. " +
            "Use natural Slovenian word stress, vowel pronunciation and sentence intonation. " +
            "Read the supplied text exactly, clearly and calmly, as one connected utterance.";

    public static String instructions(String language) {
        String base;
        switch (language) {
            case "uk": base = "Speak clearly in standard Ukrainian."; break;
            case "en": base = "Speak clearly in English."; break;
            case "de": base = "Speak clearly in German."; break;
            case "hr": base = "Speak clearly in Croatian."; break;
            case "sr": base = "Speak clearly in Serbian."; break;
            default: base = SLOVENIAN_INSTRUCTIONS;
        }
        return base + " Maintain a steady neutral speaking pace throughout, including questions. " +
                "Do not add dramatic pauses or change pace for short phrases. Speed is controlled by the speed parameter.";
    }

    private AacSpeechPronunciationPolicy() {}

    public static String cacheIdentity(String languageCode, String legacyIdentity) {
        if ("sl".equals(languageCode)) {
            return legacyIdentity + "|" + SLOVENIAN_INSTRUCTIONS;
        }
        return legacyIdentity;
    }
}
