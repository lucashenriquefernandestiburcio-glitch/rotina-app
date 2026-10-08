package app.rotina;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Reagenda os alarmes quando o Android os apaga: ao reiniciar o celular, depois de atualizar
 * o app, ao mudar o fuso ou o horário e quando o usuário libera "alarmes e lembretes".
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context contexto, Intent intent) {
        Alarmes.agendarTodos(contexto);
    }
}
