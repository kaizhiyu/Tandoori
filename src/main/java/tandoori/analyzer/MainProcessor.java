package tandoori.analyzer;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;

import spoon.Launcher;
import tandoori.entities.PaprikaApp;
import tandoori.entities.PaprikaClass;
import tandoori.entities.PaprikaMethod;

/**
 * Created by sarra on 21/02/17.
 */
public class MainProcessor {

    public static PaprikaApp currentApp;
    public static PaprikaClass currentClass;
    public static PaprikaMethod currentMethod;
    public static ArrayList<URL> paths;
    protected String appPath;
    protected String jarsPath;
    protected String sdkPath;

    public MainProcessor(String appName, String appVersion, String appKey, String appPath, String sdkPath, String jarsPath) {
        this.currentApp = PaprikaApp.createPaprikaApp(appName, appVersion, appKey);
        currentClass = null;
        currentMethod = null;
        this.appPath = appPath;
        this.jarsPath = jarsPath;
        this.sdkPath = sdkPath;
    }

    public void process() {
        Launcher launcher = new Launcher();
        launcher.addInputResource(appPath);
        launcher.getEnvironment().setNoClasspath(true);
        File folder = new File(jarsPath);
        try {
            paths = this.listFilesForFolder(folder);
            paths.add(new File(sdkPath).toURI().toURL());
            String[] cl = new String[paths.size()];
            for (int i = 0; i < paths.size(); i++) {
                URL url = paths.get(i);
                cl[i] = url.getPath();
            }
            launcher.getEnvironment().setSourceClasspath(cl);
            launcher.buildModel();
            ClassProcessor classProcessor = new ClassProcessor();
            InterfaceProcessor interfaceProcessor =new InterfaceProcessor();
            launcher.addProcessor(classProcessor);
            launcher.addProcessor(interfaceProcessor);
            launcher.process();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }

    }

    public ArrayList<URL> listFilesForFolder(final File folder) throws IOException {
        ArrayList<URL> jars = new ArrayList<>();
        if(folder.listFiles()==null){
            return jars;
        }
        for (final File fileEntry : folder.listFiles()) {

            jars.add(fileEntry.toURI().toURL());

        }
        return jars;
    }
}
