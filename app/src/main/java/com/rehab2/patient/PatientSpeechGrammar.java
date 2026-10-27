package com.rehab2.patient;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** First-person agreement only. Guide questions address the patient, not the guide's voice. */
public final class PatientSpeechGrammar {
    private PatientSpeechGrammar() {}
    public static String resolve(String text, String language, boolean male) {
        if (!male || text == null) return text;
        String[][] pairs;
        if ("sl".equals(language)) {
            pairs = new String[][] {
                {"rada bi počivala", "rad bi počival"}, {"rada bi povedala", "rad bi povedal"},
                {"rada bi poslušala", "rad bi poslušal"}, {"rada bi gledala", "rad bi gledal"},
                {"rada bi govorila", "rad bi govoril"}, {"rada bi poslala", "rad bi poslal"},
                {"rada bi videla", "rad bi videl"}, {"rada bi kadila", "rad bi kadil"},
                {"rada bi jedla", "rad bi jedel"}, {"rada bi pila", "rad bi pil"},
                {"rada bi šla", "rad bi šel"}, {"rada te imam", "rad te imam"},
                {"rada imam", "rad imam"}, {"rada bi", "rad bi"},
                {"sama bom izbrala", "sam bom izbral"}, {"sama izbrala", "sam izbral"},
                {"utrujena sem", "utrujen sem"}, {"lačna sem", "lačen sem"},
                {"žejna sem", "žejen sem"}, {"zaspana sem", "zaspan sem"},
                {"vesela sem", "vesel sem"}, {"žalostna sem", "žalosten sem"},
                {"prebolela sem", "prebolel sem"}, {"rojena sem", "rojen sem"},
                {"stara sem", "star sem"}, {"kaj bi jedla", "kaj bi jedel"},
                {"bi rada jedla", "bi rad jedel"}, {"si lačna", "si lačen"},
                {"si žejna", "si žejen"}, {"si utrujena", "si utrujen"}, {"si zaspana", "si zaspan"}
            };
        } else if ("uk".equals(language) || "ua".equals(language)) {
            pairs = new String[][] {
                {"я хотіла б", "я хотів би"}, {"я втомилася", "я втомився"},
                {"я втомлена", "я втомлений"}, {"я голодна", "я голодний"},
                {"я перенесла", "я переніс"}, {"я народилася", "я народився"},
                {"я сама", "я сам"}
            };
        } else return text;
        String result = text;
        for (String[] pair : pairs) {
            Matcher matcher = Pattern.compile("(?iu)(?<![\\p{L}])" + Pattern.quote(pair[0]) + "(?![\\p{L}])").matcher(result);
            StringBuffer out = new StringBuffer();
            while (matcher.find()) {
                String original = matcher.group(), replacement = pair[1];
                if (original.equals(original.toUpperCase(Locale.ROOT))) replacement = replacement.toUpperCase(Locale.ROOT);
                else if (Character.isUpperCase(original.charAt(0))) replacement = Character.toUpperCase(replacement.charAt(0)) + replacement.substring(1);
                matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(out);
            result = out.toString();
        }
        return result;
    }
    public static String voice(boolean patientMale, boolean guide) {
        return patientMale != guide ? "onyx" : "coral";
    }
}
