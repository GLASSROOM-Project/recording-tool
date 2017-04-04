package de.glassroom.grt;

import android.app.Activity;
import android.os.Bundle;

import de.glassroom.grt.glassroomrecordingtool.R;

public class VideoRecordActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_record);
    }
}