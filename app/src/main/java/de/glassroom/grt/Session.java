package de.glassroom.grt;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import de.glassroom.gpe.Chapter;
import de.glassroom.gpe.Guide;
import de.glassroom.gpe.GuideManager;
import de.glassroom.gpe.Step;
import de.glassroom.gpe.content.ContentDescriptor;
import de.glassroom.grt.wf.Slide;

/**
 * A container for all runtime information of a user session.
 */
public class Session {

    private final GuideManager guideManager;
    private Slide previousSlide, activeSlide;
    private Guide activeGuide;
    private Step activeStep;
    private ContentDescriptor activeContentDescriptor;
    private List<Guide> selectableGuides;
    private int selectedGuideIndex;
    private File tempFile;
    private String recognizedText;

    private MainActivity activity;

    public Session() {
        guideManager = new GuideManager();
        // TODO Remove debug input.
        // guideManager.addGuide(new Guide("guide1").setTitle("de_DE", "First Guide"));
        // guideManager.addGuide(new Guide("guide2").setTitle("de_DE", "Second Guide"));
        for (Guide guide : PersistenceHandler.importGuides()) {
            guideManager.addGuide(guide);
        }
        selectedGuideIndex = -1;
    }

    public GuideManager getGuideManager() {
        return guideManager;
    }

    public void setActiveSlide(Slide slide) {
        previousSlide = activeSlide;
        activeSlide = slide;
    }

    public Slide getActiveSlide() {
        return activeSlide;
    }

    public Slide getPreviousSlide() {
        return previousSlide;
    }

    public boolean hasPreviousSlide() {
        return previousSlide != null;
    }

    public void setActiveGuide(Guide guide) {
        this.activeGuide = guide;
        this.activeStep = null;
        this.previousSlide = null;
        this.activeContentDescriptor = null;
    }

    public Guide getActiveGuide() {
        return activeGuide;
    }

    public Step createNewStep() throws IllegalStateException {
        if (activeGuide == null) {
            throw new IllegalStateException("Failed to create new step: No active guide.");
        }
        activeStep = new Step();
        activeContentDescriptor = new ContentDescriptor(UUID.randomUUID().toString(), "de_DE");
        return activeStep;
    }

    public void setActiveStep(Step step) {
        this.activeStep = step;
    }

    public Step getActiveStep() {
        return activeStep;
    }

    public void storeActiveStep() {
        activeStep.setContentPackage("de_DE", activeContentDescriptor.getId());
        try {
            PersistenceHandler.writeContentDescriptor(activeGuide.getId(), activeContentDescriptor);
        } catch (IOException e) {
            Log.e("Session", "Failed to write content descriptor: " + activeContentDescriptor.getId(), e);
        }
        activeGuide.addNode(activeStep);
        activeStep = null;
        activeContentDescriptor = null;
    }

    public ContentDescriptor getActiveContentDescriptor() {
        return activeContentDescriptor;
    }

    public void storeActiveGuide() {
        guideManager.addGuide(activeGuide);
        try {
            PersistenceHandler.writeGuide(activeGuide);
        } catch (IOException e) {
            Log.e("Session", "Failed to persist guide: " + activeGuide.getId(), e);
        }
        activeGuide = null;
    }

    public void deleteActiveGuide() {
        if (activeGuide == null) {
            return;
        }
        String id = activeGuide.getId();
        guideManager.deleteGuide(id);
        PersistenceHandler.deleteGuide(id);
        activeGuide = null;
    }

    /**
     * Initializes a list of selectable guides and selects the first entry.
     * @return <code>true</code> if at least one guide is selectable, <code>false</code> otherwise.
     */
    public boolean initializeSelection() {
        selectableGuides = new ArrayList<>();
        for (String guideId : guideManager.getGuideIds()) {
            if (activeGuide == null || !guideId.equals(activeGuide.getId())) {
                selectableGuides.add(guideManager.getGuide(guideId));
            }
        }
        if (!selectableGuides.isEmpty()) {
            selectedGuideIndex = 0;
            return true;
        } else {
            selectedGuideIndex = -1;
            return false;
        }
    }

    public Guide getSelectedGuide() {
        return selectedGuideIndex >= 0 ? selectableGuides.get(selectedGuideIndex) : null;
    }

    public boolean hasNextSelection() {
        return selectedGuideIndex >= 0 ? selectableGuides.size() > selectedGuideIndex + 1 : false;
    }

    public boolean hasPreviousSelection() {
        return selectedGuideIndex > 0;
    }

    public Guide selectNextGuide() throws IllegalStateException {
        if (!hasNextSelection()) {
            throw new IllegalStateException("No successor available.");
        }
        selectedGuideIndex += 1;
        return getSelectedGuide();
    }

    public Guide selectPreviousGuide() throws IllegalStateException {
        if (!hasPreviousSelection()) {
            throw new IllegalStateException("No predecessor available.");
        }
        selectedGuideIndex -= 1;
        return getSelectedGuide();
    }

    public void resetSelection() {
        selectableGuides = null;
        selectedGuideIndex = -1;
    }

    public void insertChapter(Chapter chapter) throws IllegalStateException {
        if (activeGuide == null) {
            throw new IllegalStateException("Cannot insert chapter: No slide active.");
        }
        activeGuide.addNode(chapter);
    }

    public void setTempFile(File file) {
        this.tempFile = file;
    }

    public File getTempFile() {
        return tempFile;
    }

    public void setRecognizedText(String text) {
        this.recognizedText = text;
    }

    public String getRecognizedText() {
        return recognizedText;
    }
}
