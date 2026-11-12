import com.rehab2.aac.AacStarterContentV1;
import com.rehab2.aac.AacItem;
import com.rehab2.aac.AacAuditedIcons;
import org.json.*;
import java.nio.file.*;
import java.util.*;
public final class ReviewedRuntimeTest {
 public static void main(String[] args) throws Exception {
  List<AacItem> items=AacStarterContentV1.INSTANCE.items();
  if(items.size()!=487) throw new AssertionError("Item count");
  Map<String,AacItem> byId=new HashMap<>();
  for(AacItem item:items) byId.put(item.getId(),item);
  JSONArray rows=new JSONObject(Files.readString(Path.of(args[0]))).getJSONArray("entries");
  for(int i=0;i<rows.length();i++) {
   JSONObject row=rows.getJSONObject(i); AacItem item=byId.get(row.getString("id"));
   if(!row.getString("imagePath").equals(item.getImagePath())) throw new AssertionError("Runtime path: "+item.getId()+" -> "+item.getImagePath());
   if(!item.getIconSource().name().equals("SYSTEM")) throw new AssertionError("Source: "+item.getId());
  }
  for(int i=0;i<rows.length();i++) {
   JSONObject row=rows.getJSONObject(i); AacItem item=byId.get(row.getString("id"));
   if(row.has("ukSpeechRepair") && !row.isNull("ukSpeechRepair") && !row.getString("ukSpeechRepair").equals(item.getSpeechTextByLanguage().get("uk"))) throw new AssertionError("UK speech: "+item.getId());
   if(row.has("genderVariants")) for(String sex:new String[]{"female","male"}) {
    String expected=row.getJSONObject("genderVariants").getJSONObject(sex).getString("imagePath");
    if(!expected.equals(AacAuditedIcons.INSTANCE.variant(item,sex.equals("male")))) throw new AssertionError("Gender: "+item.getId()+sex);
   }
  }
  for(String id:new String[]{"wc_pee","wc_poop","please_more","please_cigarettes","please_buy_cigarettes","please_blanket","please_blanket_laundry","please_blanket_bed"}) {
   AacItem item=byId.get(id);
   if(item==null || !item.getImagePath().equals("system/aac_"+id+".png")) throw new AssertionError("New image path "+id);
   if(item.getLabelSl().isBlank() || item.getSpeakTextSl().isBlank() || item.getLabelByLanguage().get("uk").isBlank() || item.getSpeechTextByLanguage().get("uk").isBlank()) throw new AssertionError("Missing SL/UK content "+id);
  }
  for(String parent:new String[]{"wc","please"})
   if(!byId.get(parent).getChildren().contains("wc_diaper_change")) throw new AssertionError("Shared diaper record "+parent);
  String[] fixed={"yes","dont_understand","no","thank_you","help"};
  for(int i=0;i<fixed.length;i++) {
   if(!Objects.equals(byId.get(fixed[i]).getFixedTopRowPosition(),i+1)) throw new AssertionError("Fixed row "+fixed[i]);
  }
  if(byId.get("wc").getPlacements().stream().noneMatch(p -> p.getPageId().equals("page_1") && p.getPosition5x5()==6)) throw new AssertionError("Toilet slot 6");
  System.out.println("PASS: 487 runtime items, "+rows.length()+" effective image paths/sources, fixed row");
 }
}
