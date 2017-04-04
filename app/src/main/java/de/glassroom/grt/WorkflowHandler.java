package de.glassroom.grt;

import android.app.Activity;
import android.net.Uri;
import android.text.format.DateUtils;
import android.util.Log;
import android.webkit.WebView;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;

import org.jdom2.Element;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import de.glassroom.gpe.Chapter;
import de.glassroom.gpe.Guide;
import de.glassroom.gpe.Step;
import de.glassroom.gpe.annotations.MetadataAnnotation;
import de.glassroom.gpe.content.ContentDescriptor;
import de.glassroom.gpe.content.Hint;
import de.glassroom.gpe.content.Warning;
import de.glassroom.grt.wf.Command;
import de.glassroom.grt.wf.Slide;
import de.glassroom.grt.wf.SlideController;
import de.glassroom.grt.wf.Workflow;
import de.glassroom.grt.wf.WorkflowListener;
import de.glassroom.grt.wf.slide.DefaultSlide;
import de.glassroom.grt.wf.slide.InfoSlide;
import de.glassroom.grt.wf.slide.ListSlide;
import de.glassroom.grt.wf.slide.PrepVideoSlide;
import de.glassroom.grt.wf.slide.RecAudioSlide;
import de.glassroom.grt.wf.slide.RecPictureSlide;
import de.glassroom.grt.wf.slide.RecVideoSlide;
import de.glassroom.grt.wf.slide.SplashSlide;

public class WorkflowHandler implements WorkflowListener {

    private static class Forward implements Runnable {
        private String slideId;
        private Workflow workflow;

        public Forward(Workflow workflow, String slideId) {
            this.slideId = slideId;
        }
        @Override
        public void run() {
            workflow.setActiveSlide(slideId);
        }
    };

    private final MainActivity activity;
    private final WebView webView;
    private final Handlebars handlebars;
    private final Map<String, Template> templates;
    private final Workflow workflow;
    private final Session session;
    private boolean isMediaPlayed;

    public WorkflowHandler(final MainActivity activity, WebView webView, final Workflow workflow) {
        this.activity = activity;
        this.webView = webView;
        this.workflow = workflow;
        this.webView.addJavascriptInterface(new Object() {
            public void executeCommand(String key) {
                Log.i("WorkflowHandler", "Received JS call to execute command: " + key);
                activity.stopRecognizing();
                execute(key);
            }

            public void log(String text) {
                Log.i("<WebView>", text);
            }

            public void mediaPlaybackComplete() {
                activity.reinitializeVoiceCommands();
                /*final Runnable reload = new Runnable() {
                    @Override
                    public void run() {
                        workflow.setActiveSlide(session.getActiveSlide().getId());
                        // handleDefaultSlide((DefaultSlide) session.getActiveSlide());
                    }
                };
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        activity.runOnUiThread(reload);
                    }
                }, 500);*/
            }
        }, "JSInterface");

        this.templates = new HashMap<String, Template>();
        handlebars = new Handlebars();

        session = new Session();
        isMediaPlayed = false;

        this.workflow.registerWorkflowListener(this);
    }

    @Override
    public void slideChanged(Workflow workflow, Slide slide) {
        Log.i("WorkflowHandler", "Slide " + slide.getId() + " is now active.");
        session.setActiveSlide(slide);

        String templatePath = slide.getTemplatePath();
        if (templatePath != null) {
            Template template = templates.get(templatePath);
            if (template == null) try {
                template = handlebars.compile(Uri.fromFile(new File("//assets/templates/" + templatePath)).getPath());
                templates.put(templatePath, template);
            } catch (Exception e) {
                Log.e("WorkflowHandler", "Failed to load template: " + templatePath);
            }
        }

        switch (slide.getType()) {
            case SPLASH:
                handleSplashSlide(workflow, (SplashSlide) slide);
                break;
            case INFO:
                handleInfoSlide(workflow, (InfoSlide) slide);
                break;
            case DEFAULT:
                handleDefaultSlide((DefaultSlide) slide);
                break;
            case RECAUDIO:
                handleRecAudioSlide((RecAudioSlide) slide);
                break;
            case RECVIDEO:
                handleRecVideoSlide((RecVideoSlide) slide);
                break;
            case RECPICTURE:
                handleRecPictureSlide((RecPictureSlide) slide);
                break;
            case PREPVIDEO:
                handlePrepVideoSlide((PrepVideoSlide) slide);
                break;
            case LIST:
                handleListSlide((ListSlide) slide);
                break;
            default:
                Log.w("WorkflowHandler", "Unsupported slide type.");
        }
    }

    private void performSlideSpecificActions() {
        Slide slide = session.getActiveSlide();
        Guide activeGuide = session.getActiveGuide();
        Step activeStep = session.getActiveStep();
        ContentDescriptor activeContent = session.getActiveContentDescriptor();

        switch (slide.getId()) {
            case "create-confirm-name":
                // Perform text to speech

                break;
            case "create-confirm-support":
                activeGuide.setDescription("de_DE", "My description");
                break;

        }
    }

    private void handleInfoSlide(final Workflow workflow, InfoSlide slide) {
        Template template = templates.get(slide.getTemplatePath());
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        Element properties = slide.getRawProperties();
        String body = properties.getChildText("body", properties.getNamespace());
        data.put("body", body);
        try {
            webView.loadDataWithBaseURL("file:///android_asset/", template.apply(data), "text/html", "utf-8", "");
        } catch (IOException e) {
            Log.e("WorkflowHandler", "Failed to apply template: " + slide.getTemplatePath());
        }

        forward(workflow, slide.getDelay(), slide.getTarget());
    }

    private void handleSplashSlide(final Workflow workflow, SplashSlide slide) {
        Template template = templates.get(slide.getTemplatePath());
        String data = "";
        try {
            webView.loadDataWithBaseURL("file:///android_asset/", template.apply(data), "text/html", "utf-8", "");
        } catch (IOException e) {
            Log.e("WorkflowHandler", "Failed to apply template: " + slide.getTemplatePath());
        }

        forward(workflow, slide.getDelay(), slide.getTarget());
    }

    private void handleDefaultSlide(DefaultSlide slide) {
        Template template = templates.get(slide.getTemplatePath());
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        Element properties = slide.getRawProperties();
        String body = properties.getChildText("body", properties.getNamespace());
        data.put("body", body);
        Guide guide = session.getActiveGuide();
        if (guide != null) {
            data.put("guide", extractGuideData(guide));
        }
        ContentDescriptor content = session.getActiveContentDescriptor();
        if (content != null) {
            data.put("content", extractContentData(content));
        }
        SlideController controller = slide.getController();
        data.put("commands", createCommandList(controller));
        File tempFile = session.getTempFile();
        if (tempFile != null) {
            data.put("mediaPath", Uri.fromFile(tempFile).toString());
        }

        String text;
        switch (slide.getId()) {
            case "create-confirm-name":
            case "create-confirm-support":
            case "step-confirm-description":
            case "step-confirm-warning":
            case "step-confirm-note":
                // text = "Beispieltext 123";
                // session.setRecognizedText(text);
                // data.put("text", text);
                data.put("text", session.getRecognizedText());
        }

        try {
            webView.loadDataWithBaseURL("file:///android_asset/", template.apply(data), "text/html", "utf-8", null);
            activity.listenToVoiceCommand(controller.getCommands());
        } catch (IOException e) {
            Log.e("WorkflowHandler", "Failed to apply template: " + slide.getTemplatePath());
        } catch (IllegalStateException e) {
            Log.w("WorkflowHandler", "Failed to initialize voice commands: " + e.getMessage());
        }
    }

    private static Map<String, Object> extractGuideData(Guide guide) {
        Map<String, Object> map = new LinkedHashMap<>();
        MetadataAnnotation metadata = guide.getMetadata();
        if (metadata != null) {
            map.put("title", metadata.getTitle("de_DE"));
        }
        map.put("steps", guide.getNodes().size() - 2);
        long lastUpdate = guide.getLastUpdate().getTime();
        map.put("lastUpdate", DateUtils.getRelativeTimeSpanString(lastUpdate).toString());
        return map;
    }

    private static Map<String, Object> extractContentData(ContentDescriptor content) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("hasWarnings", !content.getWarnings().isEmpty());
        map.put("numWarnings", content.getWarnings().size());
        map.put("hasHints", !content.getHints().isEmpty());
        map.put("numHints", content.getHints().size());
        return map;
    }

    private void handleListSlide(final ListSlide slide) {
        Template template = templates.get(slide.getTemplatePath());
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        Element properties = slide.getRawProperties();
        String body = properties.getChildText("body", properties.getNamespace());
        data.put("body", body);

        Guide guide = session.getSelectedGuide();
        if (guide != null) {
            data.put("guide", extractGuideData(guide));
            if (!session.hasPreviousSelection()) data.put("isFirst", true);
            if (!session.hasNextSelection()) data.put("isLast", true);
            SlideController controller = slide.getController();
            data.put("commands", createCommandList(controller));
            try {
                activity.listenToVoiceCommand(controller.getCommands());
            } catch (IllegalStateException e) {
                Log.w("WorkflowHandler", "Failed to initialize voice commands: " + e.getMessage());
            }
        } else {
            boolean isGuideAvailable = session.initializeSelection();
            if (!isGuideAvailable) {
                final Runnable forwardRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if ("step-select-guide".equals(slide.getId())) {
                            workflow.setActiveSlide("step-option-newstep");
                        } else {
                            workflow.setActiveSlide(session.getPreviousSlide().getId());
                        }
                    }
                };
                Timer timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        activity.runOnUiThread(forwardRunnable);
                    }
                }, 3000);
            } else {
                guide = session.getSelectedGuide();
                data.put("guide", extractGuideData(guide));
                if (!session.hasPreviousSelection()) data.put("isFirst", true);
                if (!session.hasNextSelection()) data.put("isLast", true);
                SlideController controller = slide.getController();
                data.put("commands", createCommandList(controller));
                try {
                    activity.listenToVoiceCommand(controller.getCommands());
                } catch (IllegalStateException e) {
                    Log.w("WorkflowHandler", "Failed to initialize voice commands: " + e.getMessage());
                }
            }
        }

        try {
            webView.loadDataWithBaseURL("file:///android_asset/", template.apply(data), "text/html", "utf-8", null);
        } catch (IOException e) {
            Log.e("WorkflowHandler", "Failed to apply template: " + slide.getTemplatePath());
        }
    }

    private void handleRecAudioSlide(final RecAudioSlide slide) {
        Template template = templates.get(slide.getTemplatePath());
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        Element properties = slide.getRawProperties();
        String body = properties.getChildText("body", properties.getNamespace());
        data.put("body", body);
        try {
            webView.loadDataWithBaseURL("file:///android_asset/", template.apply(data), "text/html", "utf-8", "");
            activity.recognizeSpeech(new TextRecognitionHandler() {
                @Override
                public void textRecognized(String text) {
                    Log.i("WorkflowHandler", "Setting recognized text to: " + text);
                    session.setRecognizedText(text);
                    forward(workflow, 0, slide.getNext());
                }

                @Override
                public void onError() {
                    Log.i("WorkflowHandler", "Aborting voice input.");
                    session.setRecognizedText("<Auf Grund eines Fehlers konnte der Text nicht erfasst werden.>");
                    forward(workflow, 0, slide.getNext());
                }
            });
        } catch (IOException e) {
            Log.e("WorkflowHandler", "Failed to apply template: " + slide.getTemplatePath());
        }
    }

    private void handleRecVideoSlide(RecVideoSlide slide) {
        File tempFile = PersistenceHandler.createTempFile("mp4");
        session.setTempFile(tempFile);
        activity.startCaptureVideo(tempFile);

        /*
        Template template = templates.get(slide.getTemplatePath());
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        Element properties = slide.getRawProperties();
        String body = properties.getChildText("body", properties.getNamespace());
        data.put("body", body);
        try {
            webView.loadDataWithBaseURL("file:///android_asset/", template.apply(data), "text/html", "utf-8", "");
        } catch (IOException e) {
            Log.e("WorkflowHandler", "Failed to apply template: " + slide.getTemplatePath());
        }

        forward(workflow, 3, slide.getNext());
        */
    }

    private void handleRecPictureSlide(RecPictureSlide slide) {
        File tempFile = PersistenceHandler.createTempFile("jpg");
        session.setTempFile(tempFile);
        activity.startTakePictureActivity(tempFile);
    }

    public void handlePictureTaken(int resultCode) {
        if (resultCode == Activity.RESULT_OK) {
            Log.i("WorkflowHandler", "Picture taken.");
        } else {
            Log.w("WorkflowHandler", "Failed to take picture.");
        }
        SlideController controller = session.getActiveSlide().getController();
        final String nextSlideId = controller.getCommands().get(0).getTarget();
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                workflow.setActiveSlide(nextSlideId);
            }
        });
    }

    public void handleCapturedVideo(int resultCode) {
        if (resultCode == Activity.RESULT_OK) {
            Log.i("WorkflowHandler", "Video captured.");
        } else {
            Log.w("WorkflowHandler", "Failed to capture video.");
        }
        RecVideoSlide slide = (RecVideoSlide) session.getActiveSlide();
        final String nextSlideId = slide.getNext();
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                workflow.setActiveSlide(nextSlideId);
            }
        });
    }

    private void handlePrepVideoSlide(PrepVideoSlide slide) {
        // TODO Replace with activity for preparing video.

        Template template = templates.get(slide.getTemplatePath());
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        Element properties = slide.getRawProperties();
        String body = properties.getChildText("body", properties.getNamespace());
        data.put("body", body);
        SlideController controller = slide.getController();
        data.put("commands", createCommandList(controller));
        try {
            webView.loadDataWithBaseURL("file:///android_asset/", template.apply(data), "text/html", "utf-8", "");
            activity.listenToVoiceCommand(controller.getCommands());
        } catch (IOException e) {
            Log.e("WorkflowHandler", "Failed to apply template: " + slide.getTemplatePath());
        } catch (IllegalStateException e) {
            Log.w("WorkflowHandler", "Failed to initialize voice commands: " + e.getMessage());
        }
    }

    private void forward(final Workflow workflow, int delayInSeconds, final String target) {
        final Runnable forwardRunnable = new Runnable() {
            @Override
            public void run() {
                workflow.setActiveSlide(target);
            }
        };
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                activity.runOnUiThread(forwardRunnable);
            }
        }, delayInSeconds * 1000);
    }

    private static List<Object> createCommandList(SlideController slideController) {
        List<Object> commandList = new ArrayList<Object>();
        for (Command command : slideController.getCommands()) {
            Map<String, Object> commandMap = new LinkedHashMap<String, Object>();
            commandMap.put("key", command.getKey());
            if (command.getLabel() != null) {
                commandMap.put("label", command.getLabel());
            }
            commandList.add(commandMap);
        }
        return commandList;
    }

    public void execute(final String key) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                webView.loadUrl("javascript:GRT.onCommandSelected('" + key + "');");
            }
        });
        Slide activeSlide = session.getActiveSlide();
        if (activeSlide == null) {
            Log.w("WorkflowHandler", "Commands can only be executed while a slide is active.");
            return;
        }
        SlideController controller = activeSlide.getController();
        Command command = null;
        for (Command entry : controller.getCommands()) {
            if (entry.getKey().equals(key)) {
                command = entry;
            }
        }
        if (command != null) {
            String action = command.getAction();
            if (action != null) {
                performAction(action);
            }
            final String target = command.getTarget();
            if (target != null) {
                final Runnable forwardRunnable = new Runnable() {
                    @Override
                    public void run() {
                        workflow.setActiveSlide(target);
                    }
                };
                activity.runOnUiThread(forwardRunnable);
            }
        } else {
            Log.w("WorkflowHandler", "Action " + key + " is unknown for slide " + activeSlide.getId() + ".");
        }
    }

    private void performAction(String action) {
        String recognizedText = session.getRecognizedText();
        File tempFile = session.getTempFile();
        switch (action) {
            case "back":
                final Runnable forwardRunnable = new Runnable() {
                    @Override
                    public void run() {
                        workflow.setActiveSlide(session.getPreviousSlide().getId());
                    }
                };
                activity.runOnUiThread(forwardRunnable);
                break;
            case "create-guide":
                session.setActiveGuide(new Guide(UUID.randomUUID().toString()));
                break;
            case "cancel-guide":
                session.setActiveGuide(null);
                break;
            case "confirm-text-name":
                session.getActiveGuide().setTitle("de_DE", recognizedText);
                break;
            case "confirm-text-support":
                session.getActiveGuide().setDescription("de_DE", recognizedText);
                break;
            case "complete-guide":
                session.storeActiveGuide();
                break;
            case "create-step":
                session.createNewStep();
                break;
            case "confirm-text-description":
                session.getActiveContentDescriptor().setInfo(recognizedText);
                break;
            case "accept-picture":
                if (tempFile.exists()) {
                    ContentDescriptor descriptor = session.getActiveContentDescriptor();
                    String newFileName = "picture.jpg";
                    descriptor.setMedia("image/jpeg", newFileName);
                    PersistenceHandler.moveToContentPackage(session.getActiveGuide().getId(), descriptor.getId(), tempFile, newFileName);
                    Log.i("WorkflowHandler", "Stored picture for step.");
                }
                break;
            case "discard-picture":
                tempFile.delete();
                Log.i("WorkflowHandler", "Discarded picture.");
                break;
            case "play":
                if (!isMediaPlayed) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            isMediaPlayed = true;
                            webView.loadUrl("javascript:GRT.playMedia();");
                        }
                    });
                } else {
                    activity.reinitializeVoiceCommands();
                }
                break;
            case "accept-video":
                isMediaPlayed = false;
                if (tempFile.exists()) {
                    String newFileName = "video.mp4";
                    ContentDescriptor descriptor = session.getActiveContentDescriptor();
                    if (descriptor == null) {
                        session.createNewStep();
                        descriptor = session.getActiveContentDescriptor();
                        descriptor.setMedia("video/mp4", newFileName);
                        PersistenceHandler.moveToContentPackage(session.getActiveGuide().getId(), descriptor.getId(), tempFile, newFileName);
                        session.storeActiveStep();
                    } else {
                        descriptor.setMedia("video/mp4", newFileName);
                        PersistenceHandler.moveToContentPackage(session.getActiveGuide().getId(), descriptor.getId(), tempFile, newFileName);
                    }
                    Log.i("WorkflowHandler", "Stored video for step.");
                }
                break;
            case "discard-video":
                isMediaPlayed = false;
                tempFile.delete();
                Log.i("WorkflowHandler", "Discarded video.");
                break;
            case "confirm-text-warning":
                session.getActiveContentDescriptor().addWarning(new Warning(recognizedText));
                break;
            case "confirm-text-note":
                session.getActiveContentDescriptor().addHint(new Hint(recognizedText));
                break;
            case "set-routine-and-complete":
                session.getActiveContentDescriptor().setRoutineTask(true);
                session.storeActiveStep();
                break;
            case "set-no-routine-and-complete":
                session.getActiveContentDescriptor().setRoutineTask(false);
                session.storeActiveStep();
                break;
            case "previous":
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (session.hasPreviousSelection()) {
                            session.selectPreviousGuide();
                            handleListSlide((ListSlide) session.getActiveSlide());
                        }
                    }
                });
                break;
            case "next":
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (session.hasNextSelection()) {
                            session.selectNextGuide();
                            handleListSlide((ListSlide) session.getActiveSlide());
                        }
                    }
                });
                break;
            case "select-guide":
                Guide selectedGuide = session.getSelectedGuide();
                session.resetSelection();
                session.setActiveGuide(selectedGuide);
                break;
            case "delete":
                session.deleteActiveGuide();
                break;
            case "include-guide":
                Guide guideToInstert = session.getSelectedGuide();
                Chapter chapter = new Chapter(guideToInstert.getId());
                session.insertChapter(chapter);
                session.resetSelection();
                break;
            case "exit":
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        /*PersistenceHandler.flush(new TaskCompleteHandler() {
                            @Override
                            public void taskComplete(Long result) {
                                activity.onDestroy();
                            }
                        });*/
                        activity.onDestroy();
                    }
                });
                break;
            default:
                Log.w("WorkflowHandler", "Unimplemented action: " + action);
        }
    }
}
