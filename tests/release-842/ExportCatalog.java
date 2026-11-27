import com.rehab2.aac.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ExportCatalog {
    static String cell(String s) { return s == null ? "" : s.replace('\t',' ').replace('\n',' '); }
    public static void main(String[] args) throws Exception {
        List<String> rows = new ArrayList<>();
        rows.add("id\tlanguage\tlabel\tspeech\tquestion\ttext_review\tlistened\tcorrected\tnote");
        for (AacItem i : AacStarterContentV1.INSTANCE.items()) {
            for (String lang : List.of("sl","uk")) {
                String label=i.getLabelByLanguage().getOrDefault(lang, lang.equals("sl")?i.getLabelSl():i.getLabelUk());
                String speech=i.getSpeechTextByLanguage().getOrDefault(lang,lang.equals("sl")?i.getSpeakTextSl():i.getSpeakTextUk());
                String question=i.getQuestionByLanguage().getOrDefault(lang,lang.equals("sl")?i.getQuestionSl():i.getQuestionUk());
                rows.add(String.join("\t",i.getId(),lang,cell(label),cell(speech),cell(question),"še nepreverjeno","ne","ne","Izvoz iz prevedenega kataloga; osebne spremembe na tablici niso vključene."));
            }
        }
        Files.write(Path.of(args[0]), rows, StandardCharsets.UTF_8);
        System.out.println("Exported " + (rows.size()-1) + " SL/UK catalog rows");
    }
}
