package de.glassroom.grt;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

import de.glassroom.gpe.Guide;
import de.glassroom.gpe.content.ContentDescriptor;
import de.glassroom.gpe.utils.ContentSerializer;
import de.glassroom.gpe.utils.GuideSerializer;

/**
 * Handler to store guides and media.
 */
public class PersistenceHandler {
    private static File guidesDir;
    private static File tmpDir;
    private static File sdCard;

    static {
        initialize();
    }

    private static void initialize() {
        sdCard = new File("/mnt/ext_sdcard");
        if (!sdCard.exists()) {
            sdCard = Environment.getExternalStorageDirectory();
        }
        File rootDir = new File(sdCard.getAbsolutePath() + "/glassroom");
        rootDir.mkdirs();
        if (!rootDir.exists()) {
            sdCard = Environment.getExternalStorageDirectory();
            rootDir = new File(sdCard.getAbsolutePath() + "/glassroom");
            rootDir.mkdirs();
        }
        guidesDir = new File(rootDir.getAbsolutePath() + "/guides");
        guidesDir.mkdir();
        tmpDir = new File(rootDir.getAbsolutePath() + "/tmp");
        tmpDir.mkdir();
    }

    public static List<Guide> importGuides() {
        if (sdCard == null) {
            initialize();
        }
        List<Guide> guides = new ArrayList<>();
        for (File file : guidesDir.listFiles()) {
            if (!file.isDirectory()) continue;
            File manifest = new File(file, "guide.bpmn");
            if (!manifest.exists()) continue;
            try {
                String bpmnString = readFile(manifest);
                Guide guide = GuideSerializer.readFromBPMN(bpmnString);
                guides.add(guide);
            } catch (Exception e) {
                Log.w("PersistenceHandler", "Failed to read guide manifest: " + file.getName(), e);
            }
        }
        return guides;
    }

    private static String readFile(File file) throws IOException {
        Scanner scanner = new Scanner(file).useDelimiter("\r\n");
        StringBuilder builder = new StringBuilder();
        while (scanner.hasNext()) {
            builder.append(scanner.next());
        }
        return builder.toString();
    }

    /**
     * Writes a guide manifest to the external storage.
     * @param guide Guide to persist.
     * @throws IOException Writing to the external storage failed.
     */
    public static void writeGuide(Guide guide) throws IOException {
        File guideDir = new File(guidesDir.getAbsolutePath() + "/" + guide.getId());
        if (!guideDir.exists()) guideDir.mkdir();

        File contentDir = new File(guideDir.getAbsolutePath() + "/content");
        if (!contentDir.exists()) contentDir.mkdir();

        File guideDescriptorFile = new File(guideDir, "guide.bpmn");
        FileWriter writer;
        writer  = new FileWriter(guideDescriptorFile);

        guide.getMetadata().setLastUpdate(new Date());
        String serializedGuide = GuideSerializer.writeAsBPMN(guide, false);
        writer.write(serializedGuide);
        writer.flush();
        writer.close();
    }

    /**
     * Creates a temporary file.
     * @param suffix Optional suffix to apply, e.g. "mp4".
     * @return File reference for the new file.
     */
    public static File createTempFile(String suffix) {
        File file = new File(tmpDir, UUID.randomUUID().toString() + (suffix != null ? "." + suffix : ""));
        return file;
    }

    /**
     * Moves a (temporary) file to a content package.
     * @param guideId ID of the guide the content package is located in.
     * @param packageId ID of the content package.
     * @param fileToMove (Temporary) file to move.
     * @param newFileName Optional new file name to use. If <code>null</code> the current file name will be userd.
     * @return The newly generated file.
     * @throws IllegalArgumentException The file to bo moved does not exist.
     */
    public static File moveToContentPackage(String guideId, String packageId, File fileToMove, String newFileName) throws IllegalArgumentException {
        if (!fileToMove.exists()) {
            throw new IllegalArgumentException("The given file " + fileToMove.getName() + " does not exist.");
        }

        File contentPackageDir = new File(guidesDir.getAbsolutePath() + "/" + guideId + "/content/" + packageId);
        if (!contentPackageDir.exists()) contentPackageDir.mkdirs();

        File newFile = new File(contentPackageDir, newFileName != null ? newFileName : fileToMove.getName());
        fileToMove.renameTo(newFile);

        return newFile;
    }

    /**
     * Persists a content descriptor.
     * @param guideId ID of the guide the content is related to.
     * @param contentDescriptor Content descriptor to persist.
     * @throws IOException Failed to generate
     */
    public static void writeContentDescriptor(String guideId, ContentDescriptor contentDescriptor) throws IOException {
        File contentPackageDir = new File(guidesDir.getAbsolutePath() + "/" + guideId + "/content/" + contentDescriptor.getId());
        if (!contentPackageDir.exists()) contentPackageDir.mkdirs();

        String serializedContentDescriptor = ContentSerializer.writeAsXML(contentDescriptor, false);

        File contentDescriptorFile = new File(contentPackageDir, "content.xml");
        FileWriter writer;
        writer  = new FileWriter(contentDescriptorFile);
        writer.write(serializedContentDescriptor);
        writer.flush();
        writer.close();
    }

    /**
     * Deletes a guide with all of its content.
     * @param guideId Identifier of the guide to delete.
     */
    public static void deleteGuide(String guideId) {
        File guideDir = new File(guidesDir.getAbsolutePath() + "/" + guideId);
        if (guideDir.exists()) {
            deleteDir(guideDir);
        }
    }

    private static class FlushTask extends AsyncTask<Void, Void, Long> {
        private final TaskCompleteHandler taskCompleteHandler;

        public FlushTask(TaskCompleteHandler handler) {
            taskCompleteHandler = handler;
        }

        @Override
        protected Long doInBackground(Void... params) {
            try {
                Log.d("PersistenceHandler", "Rescanning SD card ...");
                Runtime.getRuntime().exec("am broadcast -a android.intent.action.MEDIA_MOUNTED -d " + Uri.fromFile(sdCard));
                Log.d("PersistenceHandler", "SD card flushed successfully.");
                return TaskCompleteHandler.SUCCESS;
            } catch (IOException e) {
                Log.w("PersistenceHandler", "Failed to rescan SD card.");
                return TaskCompleteHandler.ERROR;
            }
        }

        @Override
        protected void onPostExecute(Long result) {
            taskCompleteHandler.taskComplete(result);
        }

        @Override
        protected void onCancelled() {
            Log.w("PersistenceHandler", "Cancelled rescan of SD card.");
            taskCompleteHandler.taskComplete(TaskCompleteHandler.CANCELLED);
        }
    };

    public static void flush(TaskCompleteHandler handler) {
        final AsyncTask task = new FlushTask(handler).execute();
        Handler backgroundHandler = new Handler();
        backgroundHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                task.cancel(true);
            }
        }, 2000);


        /*AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d("PersistenceHandler", "Rescanning SD card ...");

                    Runtime.getRuntime().exec("am broadcast -a android.intent.action.MEDIA_MOUNTED -d " + Uri.fromFile(sdCard));
                    Log.d("PersistenceHandler", "SD card flushed successfully.");
                } catch (IOException e) {
                    Log.w("PersistenceHandler", "Failed to rescan SD card.");
                }
            }
        });*/
    }

    private static void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) { //some JVMs return null for empty dirs
            for(File f : files) {
                if(f.isDirectory()) {
                    deleteDir(f);
                } else {
                    f.delete();
                }
            }
        }
        dir.delete();
    }
}
