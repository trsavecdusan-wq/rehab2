import com.rehab2.aac.AacStarterContentV1;
import com.rehab2.aac.AacItem;
import org.json.*;
import java.nio.file.*;
import java.util.*;
public final class ReviewedRuntimeTest {
 public static void main(String[] args) throws Exception {
  List<AacItem> items=AacStarterContentV1.INSTANCE.items();
  if(items.size()!=479) throw new AssertionError("Item count");
  Map<String,AacItem> byId=new HashMap<>();
  for(AacItem item:items) byId.put(item.getId(),item);
  JSONArray rows=new JSONObject(Files.readString(Path.of(args[0]))).getJSONArray("entries");
  for(int i=0;i<rows.length();i++) {
   JSONObject row=rows.getJSONObject(i); AacItem item=byId.get(row.getString("id"));
   if(!row.getString("imagePath").equals(item.getImagePath())) throw new AssertionError("Runtime path: "+item.getId()+" -> "+item.getImagePath());
   if(!item.getIconSource().name().equals("SYSTEM")) throw new AssertionError("Source: "+item.getId());
  }
  String[] fixed={"yes","dont_understand","no","thank_you","help"};
  for(int i=0;i<fixed.length;i++) {
   if(!Objects.equals(byId.get(fixed[i]).getFixedTopRowPosition(),i+1)) throw new AssertionError("Fixed row "+fixed[i]);
  }
  if(byId.get("wc").getPlacements().stream().noneMatch(p -> p.getPageId().equals("page_1") && p.getPosition5x5()==6)) throw new AssertionError("Toilet slot 6");
  System.out.println("PASS: 479 runtime items, "+rows.length()+" effective image paths/sources, fixed row");
 }
}
