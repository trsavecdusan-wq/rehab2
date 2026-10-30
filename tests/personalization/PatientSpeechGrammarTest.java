import com.rehab2.patient.PatientSpeechGrammar;
import java.util.Objects;

public class PatientSpeechGrammarTest {
    static int checks;
    static void equal(Object actual, Object expected) {
        checks++;
        if (!Objects.equals(actual, expected)) throw new AssertionError(actual + " != " + expected);
    }
    public static void main(String[] args) {
        equal(PatientSpeechGrammar.resolve("Rada bi šla na sprehod.", "sl", true), "Rad bi šel na sprehod.");
        equal(PatientSpeechGrammar.resolve("Prosim, rada bi kavo.", "sl", true), "Prosim, rad bi kavo.");
        equal(PatientSpeechGrammar.resolve("Rada bi videla Dušana.", "sl", true), "Rad bi videl Dušana.");
        equal(PatientSpeechGrammar.resolve("Rada bi govorila z Julijo.", "sl", true), "Rad bi govoril z Julijo.");
        equal(PatientSpeechGrammar.resolve("Prebolela sem možgansko kap.", "sl", true), "Prebolel sem možgansko kap.");
        equal(PatientSpeechGrammar.resolve("SAMA BOM IZBRALA", "sl", true), "SAM BOM IZBRAL");
        equal(PatientSpeechGrammar.resolve("UTRUJENA SEM", "sl", true), "UTRUJEN SEM");
        equal(PatientSpeechGrammar.resolve("Kaj bi jedla?", "sl", true), "Kaj bi jedel?");
        equal(PatientSpeechGrammar.resolve("Kaj bi jedla?", "sl", false), "Kaj bi jedla?");
        equal(PatientSpeechGrammar.resolve("Kakšno vodo želiš?", "sl", true), "Kakšno vodo želiš?");
        equal(PatientSpeechGrammar.resolve("Я втомилася.", "uk", true), "Я втомився.");
        equal(PatientSpeechGrammar.resolve("Будь ласка, я хотіла б щось поїсти.", "uk", true), "Будь ласка, я хотів би щось поїсти.");
        equal(PatientSpeechGrammar.resolve("Я перенесла інсульт.", "uk", true), "Я переніс інсульт.");
        equal(PatientSpeechGrammar.resolve("Я втомилася.", "uk", false), "Я втомилася.");
        equal(PatientSpeechGrammar.resolve("I am tired.", "en", true), "I am tired.");
        equal(PatientSpeechGrammar.resolve("Rad bi počival.", "sl", true), "Rad bi počival.");
        equal(PatientSpeechGrammar.resolve("Rada bi šla.", "sl", false), "Rada bi šla.");
        equal(PatientSpeechGrammar.resolve("sladoled", "sl", true), "sladoled");
        equal(PatientSpeechGrammar.voice(false, false), "coral");
        equal(PatientSpeechGrammar.voice(false, true), "onyx");
        equal(PatientSpeechGrammar.voice(true, false), "onyx");
        equal(PatientSpeechGrammar.voice(true, true), "coral");
        equal(PatientSpeechGrammar.resolve("Nisem končala.", "sl", true), "Nisem končal.");
        equal(PatientSpeechGrammar.resolve("Nisem lačna.", "sl", true), "Nisem lačen.");
        equal(PatientSpeechGrammar.resolve("Nisem žejna.", "sl", true), "Nisem žejen.");
        equal(PatientSpeechGrammar.resolve("Prosim poglej, pokazala ti bom.", "sl", true), "Prosim poglej, pokazal ti bom.");
        equal(PatientSpeechGrammar.resolve("Opazila sem kri.", "sl", true), "Opazil sem kri.");
        equal(PatientSpeechGrammar.resolve("Strah me je, da bom padla.", "sl", true), "Strah me je, da bom padel.");
        equal(PatientSpeechGrammar.resolve("Kaj bi delala?", "sl", true), "Kaj bi delal?");
        equal(PatientSpeechGrammar.resolve("Що ти хотіла б їсти?", "uk", true), "Що ти хотів би їсти?");
        equal(PatientSpeechGrammar.resolve("Я не закінчила.", "uk", true), "Я не закінчив.");
        System.out.println(checks + " patient grammar and role checks passed");
    }
}
