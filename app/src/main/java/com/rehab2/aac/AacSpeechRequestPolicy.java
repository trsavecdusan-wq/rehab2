package com.rehab2.aac;

/** TTS events belong to the utterance submitted by the current live player. */
public final class AacSpeechRequestPolicy {
    private AacSpeechRequestPolicy() {}
    public static boolean isCurrent(String utteranceId, int serial, boolean released) {
        return !released && ("aac_" + serial).equals(utteranceId);
    }
}
