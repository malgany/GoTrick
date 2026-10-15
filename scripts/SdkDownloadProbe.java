import dev.gotrick.toolchain.GoSdkDownload;
import java.nio.file.Path;
import kotlin.Unit;

/** Opt-in network test for the same SDK download code used by Settings. */
public class SdkDownloadProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Provide an empty SDK parent folder");
        var release = GoSdkDownload.INSTANCE.releases().get(0);
        var sdk = GoSdkDownload.INSTANCE.install(release, Path.of(args[0]), () -> Unit.INSTANCE, p -> Unit.INSTANCE);
        var process = new ProcessBuilder(sdk.resolve("bin/go.exe").toString(), "version").inheritIO().start();
        if (process.waitFor() != 0) throw new AssertionError("Downloaded Go SDK cannot execute");
        System.out.println("PASS: official SDK download, SHA-256, extraction and execution: " + release.getVersion());
    }
}
