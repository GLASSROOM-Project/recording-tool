package de.glassroom.grt;

public interface TextRecognitionHandler {
    public void textRecognized(String text);
    public void onError();
}
