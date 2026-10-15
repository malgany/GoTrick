import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.scale.ScaleContext;
import java.awt.image.BufferedImage;
import javax.swing.Icon;

public class BrandingProbe {
  public static void main(String[] args) throws Exception {
    String name = ApplicationNamesInfo.getInstance().getFullProductName();
    if (!"GoTrick".equals(name)) throw new AssertionError("Unexpected name: " + name);
    if (com.intellij.openapi.updateSettings.impl.ExternalUpdateManager.ACTUAL == null)
      throw new AssertionError("Upstream platform updates must be disabled for GoTrick");
    var info = ApplicationInfoImpl.getShadowInstance();
    if (com.intellij.ide.gdpr.ConsentOptions.getInstance().isSendingUsageStatsAllowed() == null)
      throw new AssertionError("Missing vendor consent metadata");
    if (!info.getApplicationSvgIconUrl().equals("/gotrick/icon.svg")) throw new AssertionError("Unexpected icon");
    Icon icon = AppUIUtil.loadApplicationIcon(ScaleContext.createIdentity(), 32);
    if (icon == null) throw new AssertionError("Icon not loaded");
    var pixels = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
    var graphics = pixels.createGraphics();
    try { icon.paintIcon(null, graphics, 0, 0); } finally { graphics.dispose(); }
    int visible = 0;
    for (int y=0; y<32; y++) for (int x=0; x<32; x++) if ((pixels.getRGB(x,y) >>> 24) > 0) visible++;
    if (visible < 100) throw new AssertionError("Icon empty: " + visible);
    System.out.println("PASS: product=" + name + ", icon visible pixels=" + visible);
  }
}
