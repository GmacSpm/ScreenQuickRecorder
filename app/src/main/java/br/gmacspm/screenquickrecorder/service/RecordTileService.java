package br.gmacspm.screenquickrecorder.service;

import android.app.PendingIntent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.content.Intent;
import android.widget.Toast;

import br.gmacspm.screenquickrecorder.activities.PermissionActivity;

public class RecordTileService extends TileService {
    private static final String ACTION_START = "com.example.app.ACTION_START_RECORDING";
    private static final String ACTION_STOP = "com.example.app.ACTION_STOP_RECORDING";

    @Override
    public void onClick() {
        Tile tile = getQsTile();
        if (isRecording()) {
            // parar serviço
            Intent stop = new Intent(this, RecordingService.class);
            stop.setAction(ACTION_STOP);
            startService(stop);
            tile.setState(Tile.STATE_INACTIVE);
            tile.updateTile();
            Toast.makeText(this, "Parando gravação...", Toast.LENGTH_SHORT).show();
        } else {
            // pedir permissão (abre Activity para obter MEDIA_PROJECTION permission)
            Intent intent = new Intent(this, PermissionActivity.class);
            intent.setAction(ACTION_START);
            // Garante que a Activity seja lançada em uma nova tarefa
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            // 2. Definir as flags do PendingIntent
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            // Adiciona FLAG_IMMUTABLE, exigido pelo Android S (API 31) e superior
            // M = Marshmallow (API 23), bom ponto de partida
            flags |= PendingIntent.FLAG_IMMUTABLE;

            // 3. Criar o PendingIntent
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    0, // Request Code
                    intent,
                    flags
            );

            // 4. Chamar o método atualizado e fechar o Quick Settings
            startActivity(intent);
        }
    }

    private boolean isRecording() {
        // Simples heurística: pergunte ao serviço via static flag ou mantenha estado.
        return RecordingService.isRecording();
    }
}

