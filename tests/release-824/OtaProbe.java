import com.rehab2.update.GitHubUpdateClient;
/** Executes the unchanged app updater against the real published GitHub release list. */
public class OtaProbe {
 public static void main(String[] args) {
  GitHubUpdateClient.ReleaseInfo release = new GitHubUpdateClient().fetchLatestRelease();
  if (!"v1.2.824".equals(release.getTagName()) || !Long.valueOf(11012).equals(release.getVersionCode()))
   throw new AssertionError("Unexpected OTA selection: "+release);
  if (release.getVersionCode() <= Long.parseLong(args[0])) throw new AssertionError("Not an upgrade");
  if (!"https://github.com/trsavecdusan-wq/rehab2/releases/download/v1.2.824/rehab-release.apk".equals(release.getApkUrl()))
   throw new AssertionError("Wrong asset: "+release.getApkUrl());
  System.out.println("PASS: real app updater selects "+release.getTagName()+" / "+release.getVersionCode()+" from installed code "+args[0]);
  System.out.println(release.getApkUrl());
 }
}
