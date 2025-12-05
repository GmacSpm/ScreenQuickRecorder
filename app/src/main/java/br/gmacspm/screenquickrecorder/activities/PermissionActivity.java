package br.gmacspm.screenquickrecorder.activities;

import android.os.Build;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

// PermissionActivity.java
import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.widget.Toast;

import br.gmacspm.screenquickrecorder.service.RecordingService;

public class PermissionActivity extends Activity {
    private static final int REQUEST_MEDIA_PROJECTION = 1001;
    private MediaProjectionManager projectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        Intent captureIntent = projectionManager.createScreenCaptureIntent();
        startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                // inicia o RecordingService em foreground com os dados retornados
                Intent serviceIntent = new Intent(this, RecordingService.class);
                serviceIntent.putExtra(RecordingService.EXTRA_RESULT_CODE, resultCode);
                serviceIntent.putExtra(RecordingService.EXTRA_RESULT_INTENT, data);
                startForegroundService(serviceIntent);
                Toast.makeText(this, "Iniciando gravação...", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permissão de captura de tela negada", Toast.LENGTH_SHORT).show();
            }
            finish();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}
