import com.rehab2.aac.AacSystemImagePolicy;
import com.rehab2.aac.AacContactImageAliases;
import java.util.*;

public class SystemImagePolicyTest {
    static int checks;
    static void equal(Object a, Object b) { checks++; if (!Objects.equals(a,b)) throw new AssertionError(a+" != "+b); }
    public static void main(String[] args) {
        List<String> known = Arrays.asList("system/old.png", "system/female.png", "system/male.png");
        equal(AacSystemImagePolicy.select("SYSTEM","system/old.png",false,false,known,"system/female.png","system/male.png",false),"system/female.png");
        equal(AacSystemImagePolicy.select("SYSTEM","system/old.png",false,false,known,"system/female.png","system/male.png",true),"system/male.png");
        equal(AacSystemImagePolicy.select("SYSTEM","system/female.png",false,false,known,"system/female.png","system/male.png",true),"system/male.png");
        equal(AacSystemImagePolicy.select("SYSTEM","system/male.png",false,false,known,"system/female.png","system/male.png",false),"system/female.png");
        for (String source : Arrays.asList("CUSTOM","ARASAAC","SOCA","PATIENT"))
            equal(AacSystemImagePolicy.select(source,"system/old.png",false,false,known,"f","m",true),null);
        equal(AacSystemImagePolicy.select("SYSTEM","system/old.png",true,false,known,"f","m",true),null);
        equal(AacSystemImagePolicy.select("SYSTEM","system/old.png",false,true,known,"f","m",true),null);
        equal(AacSystemImagePolicy.select("SYSTEM","system/manual.png",false,false,known,"f","m",true),null);
        equal(AacSystemImagePolicy.select("SYSTEM",null,false,false,known,"f","m",true),null);
        equal(AacSystemImagePolicy.select("SYSTEM","system/old.png",false,false,known,"f","",true),null);
        equal(AacContactImageAliases.candidates("where_zana"),Arrays.asList("where_zana","person_zana","sister_zana"));
        equal(AacContactImageAliases.candidates("person_dusan").get(0),"person_dusan");
        equal(AacContactImageAliases.candidates("person_julija"),Arrays.asList("person_julija","julija"));
        equal(AacContactImageAliases.candidates("my_caregiver"),Collections.singletonList("my_caregiver"));
        equal(AacContactImageAliases.candidates("son"),Collections.singletonList("son"));
        equal(AacContactImageAliases.candidates("where_zana",false),Collections.singletonList("where_zana"));
        equal(AacContactImageAliases.candidates("person_zana").contains("person_julija"),false);
        boolean immutable=false;
        try { AacContactImageAliases.candidates("person_zana").add("other"); } catch (UnsupportedOperationException e) { immutable=true; }
        equal(immutable,true);
        System.out.println(checks+" image protection, gender switching and contact-alias checks passed");
    }
}
