import com.rehab2.aac.AacSpeechRequestPolicy;
public final class SpeechRequestPolicyTest {
    private static int checks;
    private static void expect(boolean expected, String id, int serial, boolean released) {
        checks++;
        if (AacSpeechRequestPolicy.isCurrent(id, serial, released) != expected)
            throw new AssertionError("Request check " + checks);
    }
    public static void main(String[] args) {
        expect(true,"aac_1",1,false);
        expect(false,"aac_1",2,false); // Completion/error from interrupted utterance.
        expect(false,"aac_2",1,false);
        expect(false,null,1,false);
        expect(false,"",1,false);
        expect(false,"aac_01",1,false);
        expect(false,"aac_1suffix",1,false);
        expect(false,"aac_1",1,true);
        expect(false,"aac_0",0,true);
        expect(true,"aac_2147483647",Integer.MAX_VALUE,false);
        expect(true,"aac_-2147483648",Integer.MIN_VALUE,false);
        expect(false,"aac_2147483647",Integer.MIN_VALUE,false);
        System.out.println("PASS: " + checks + " real TTS callback policy checks");
    }
}
