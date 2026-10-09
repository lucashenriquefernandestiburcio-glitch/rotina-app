package app.rotina;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Trata os botões do aviso: "Bebi", "Feito" e "Adiar 10 min" (e o aviso adiado que volta). */
public class AcaoReceiver extends BroadcastReceiver {
    static final String FEITO = "app.rotina.FEITO";
    static final String ADIAR = "app.rotina.ADIAR";
    static final String SONECA = "app.rotina.SONECA";

    @Override
    public void onReceive(Context contexto, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String acao = intent.getAction();
        int nid = intent.getIntExtra("nid", -1);
        if (!SONECA.equals(acao) && nid >= 0) {
            NotificationManager nm =
                    (NotificationManager) contexto.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(nid);
        }
        if (FEITO.equals(acao)) {
            Alarmes.registrarAcao(contexto, intent.getStringExtra("tipo"),
                    intent.getStringExtra("id"), intent.getIntExtra("ml", 0));
        } else if (ADIAR.equals(acao)) {
            Alarmes.adiar(contexto, intent.getExtras());
        } else if (SONECA.equals(acao)) {
            Alarmes.reavisar(contexto, intent.getExtras());
        }
    }
}
