package app.rotina;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Recebe o alarme de um lembrete: mostra o aviso e agenda a próxima ocorrência. */
public class AlarmeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context contexto, Intent intent) {
        if (intent == null || !Alarmes.ACAO.equals(intent.getAction())) return;
        Alarmes.disparar(contexto, intent.getIntExtra("i", -1), intent.getLongExtra("quando", 0L));
    }
}
