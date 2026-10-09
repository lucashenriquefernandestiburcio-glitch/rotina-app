package app.rotina;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Locale;

/**
 * Alarmes nativos da Rotina Fitness.
 *
 * A página manda a lista de lembretes (horário em minutos, dias da semana, título e texto).
 * Cada item vira UM alarme exato, para a próxima ocorrência; quando ele toca, o receptor
 * mostra a notificação e agenda a ocorrência seguinte. Assim há no máximo um alarme
 * pendente por item, em vez de um por dia da semana.
 *
 * A lista fica guardada no aparelho (SharedPreferences) para os alarmes voltarem sozinhos
 * depois de reiniciar o celular, mudar o fuso ou o horário (ver BootReceiver).
 */
final class Alarmes {

    static final String CANAL = "lembretes";
    static final String ACAO = "app.rotina.ALARME";
    private static final String PREFS = "alarmes";
    private static final int MAX_ITENS = 400;
    private static final long ATRASO_MAX_MS = 90L * 60L * 1000L;

    private Alarmes() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean ativo(Context c) {
        return prefs(c).getBoolean("ativo", false);
    }

    static int quantos(Context c) {
        return prefs(c).getInt("n", 0);
    }

    /** Guarda a lista nova (trocando a antiga) e agenda tudo. Devolve "ok" ou o motivo do erro. */
    static String salvarEAgendar(Context c, String json) {
        try {
            JSONObject entrada = new JSONObject(json);
            JSONArray origem = entrada.getJSONArray("lista");
            JSONArray limpa = new JSONArray();
            for (int k = 0; k < origem.length() && limpa.length() < MAX_ITENS; k++) {
                JSONObject o = origem.optJSONObject(k);
                if (o == null) continue;
                int m = o.optInt("m", -1);
                JSONArray dias = o.optJSONArray("dias");
                if (m < 0 || m > 1439 || dias == null) continue;
                JSONArray dl = new JSONArray();
                boolean[] visto = new boolean[7];
                for (int j = 0; j < dias.length(); j++) {
                    int d = dias.optInt(j, -1);
                    if (d >= 0 && d <= 6 && !visto[d]) {
                        visto[d] = true;
                        dl.put(d);
                    }
                }
                if (dl.length() == 0) continue;
                JSONObject item = new JSONObject();
                item.put("m", m);
                item.put("dias", dl);
                item.put("t", corta(o.optString("t", "Rotina"), 80));
                item.put("d", corta(o.optString("d", ""), 300));
                String tipo = o.optString("k", "");
                if (tipo.equals("agua") || tipo.equals("ref") || tipo.equals("cn")) item.put("k", tipo);
                item.put("id", corta(o.optString("id", ""), 40));
                int ml = o.optInt("ml", 0);
                if (ml >= 50 && ml <= 1000) item.put("ml", ml);
                limpa.put(item);
            }
            criarCanal(c);
            cancelarAgendados(c);
            prefs(c).edit()
                    .putString("lista", limpa.toString())
                    .putString("perfil", corta(entrada.optString("perfil", ""), 40))
                    .putInt("n", limpa.length())
                    .putBoolean("ativo", true)
                    .apply();
            agendarTodos(c);
            return "ok";
        } catch (Exception e) {
            return "erro: " + e.getMessage();
        }
    }

    /** Desliga tudo: cancela os alarmes pendentes e esquece a lista. */
    static void desligar(Context c) {
        cancelarAgendados(c);
        prefs(c).edit().clear().apply();
    }

    private static String corta(String s, int max) {
        if (s == null) return "";
        s = s.trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static PendingIntent intencao(Context c, int i, long quando, int flags) {
        Intent it = new Intent(c, AlarmeReceiver.class).setAction(ACAO);
        if (quando > 0) {
            it.putExtra("i", i);
            it.putExtra("quando", quando);
        }
        return PendingIntent.getBroadcast(c, i, it, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void cancelarAgendados(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        int n = quantos(c);
        for (int i = 0; i < n; i++) {
            PendingIntent pi = intencao(c, i, 0, PendingIntent.FLAG_NO_CREATE);
            if (pi != null) {
                am.cancel(pi);
                pi.cancel();
            }
        }
    }

    /** Agenda a próxima ocorrência de todos os itens da lista guardada (se estiver ligado). */
    static void agendarTodos(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean("ativo", false)) return;
        try {
            JSONArray lista = new JSONArray(p.getString("lista", "[]"));
            long agora = System.currentTimeMillis();
            for (int i = 0; i < lista.length(); i++) {
                agendarUm(c, i, lista.getJSONObject(i), agora);
            }
        } catch (Exception ignorado) {
            // lista corrompida: nada a agendar
        }
    }

    /** Próximo horário (em ms) depois de "depois" em que o item deve tocar, ou -1. */
    static long proximo(JSONObject e, long depois) {
        JSONArray dias = e.optJSONArray("dias");
        int m = e.optInt("m", -1);
        if (dias == null || m < 0 || m > 1439) return -1;
        boolean[] ok = new boolean[7];
        for (int k = 0; k < dias.length(); k++) {
            int d = dias.optInt(k, -1);
            if (d >= 0 && d <= 6) ok[d] = true;
        }
        Calendar base = Calendar.getInstance();
        base.setTimeInMillis(depois);
        for (int off = 0; off <= 7; off++) {
            Calendar x = (Calendar) base.clone();
            x.add(Calendar.DAY_OF_YEAR, off);
            x.set(Calendar.HOUR_OF_DAY, m / 60);
            x.set(Calendar.MINUTE, m % 60);
            x.set(Calendar.SECOND, 0);
            x.set(Calendar.MILLISECOND, 0);
            int dw = x.get(Calendar.DAY_OF_WEEK) - 1;
            if (ok[dw] && x.getTimeInMillis() > depois) return x.getTimeInMillis();
        }
        return -1;
    }

    static void agendarUm(Context c, int i, JSONObject e, long depois) {
        long quando = proximo(e, depois);
        if (quando < 0) return;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = intencao(c, i, quando, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            if (podeExato(c)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi);
            }
        } catch (SecurityException semPermissao) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi);
        }
    }

    /** Toca o alarme de um item: mostra a notificação (se não estiver muito atrasado) e agenda o próximo. */
    static void disparar(Context c, int i, long quando) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean("ativo", false)) return;
        try {
            JSONArray lista = new JSONArray(p.getString("lista", "[]"));
            if (i < 0 || i >= lista.length()) return;
            JSONObject e = lista.getJSONObject(i);
            long agora = System.currentTimeMillis();
            if ((quando <= 0 || agora - quando <= ATRASO_MAX_MS) && !jaFeito(c, e)) {
                Bundle ex = new Bundle();
                ex.putString("tipo", e.optString("k", ""));
                ex.putString("id", e.optString("id", ""));
                ex.putInt("ml", e.optInt("ml", 250));
                ex.putInt("idx", i);
                notificar(c, 100 + i, e.optString("t", "Rotina"), e.optString("d", ""),
                        p.getString("perfil", ""), ex);
            }
            agendarUm(c, i, e, Math.max(agora, quando) + 1000L);
        } catch (Exception ignorado) {
            // sem lista válida, não há o que fazer
        }
    }

    static boolean podeExato(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        return am.canScheduleExactAlarms();
    }

    static boolean notifOk(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.areNotificationsEnabled();
    }

    static void criarCanal(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CANAL) != null) return;
        NotificationChannel canal = new NotificationChannel(CANAL, "Lembretes da rotina",
                NotificationManager.IMPORTANCE_HIGH);
        canal.setDescription("Refeições, treinos, água e aplicação da caneta nos horários da sua rotina.");
        canal.enableVibration(true);
        nm.createNotificationChannel(canal);
    }

    /** Mostra uma notificação agora. Devolve false se o aparelho não deixa o app avisar. */
    static boolean notificar(Context c, int id, String titulo, String texto, String perfil) {
        return notificar(c, id, titulo, texto, perfil, null);
    }

    /**
     * Notificação com botões. extras (opcional) leva: tipo ("agua", "ref" ou "cn"), id do item,
     * ml (água) e idx. Com tipo válido ganha "Bebi X ml"/"Feito" e "Adiar 10 min".
     */
    static boolean notificar(Context c, int id, String titulo, String texto, String perfil,
                             Bundle extras) {
        if (!notifOk(c)) return false;
        criarCanal(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        Intent abrir = new Intent(c, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent toque = PendingIntent.getActivity(c, 0, abrir,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        int icone = c.getResources().getIdentifier("ic_notif", "drawable", c.getPackageName());
        if (icone == 0) icone = android.R.drawable.stat_notify_more;
        Notification.Builder b = new Notification.Builder(c, CANAL)
                .setSmallIcon(icone)
                .setContentTitle(titulo)
                .setContentText(texto)
                .setStyle(new Notification.BigTextStyle().bigText(texto))
                .setContentIntent(toque)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis());
        if (perfil != null && perfil.length() > 0) b.setSubText(perfil);

        String tipo = extras == null ? "" : extras.getString("tipo", "");
        if (tipo.equals("agua") || tipo.equals("ref") || tipo.equals("cn")) {
            Bundle ex = new Bundle(extras);
            ex.putInt("nid", id);
            ex.putString("t", titulo);
            ex.putString("d", texto);
            ex.putString("perfil", perfil == null ? "" : perfil);
            int ml = ex.getInt("ml", 250);
            if (ml < 50 || ml > 1000) {
                ml = 250;
                ex.putInt("ml", ml);
            }
            String rotulo = tipo.equals("agua") ? "Bebi " + ml + " ml" : "Feito";
            b.addAction(acao(c, icone, rotulo, AcaoReceiver.FEITO, ex, id * 4 + 1));
            b.addAction(acao(c, icone, "Adiar 10 min", AcaoReceiver.ADIAR, ex, id * 4 + 2));
        }
        nm.notify(id, b.build());
        return true;
    }

    private static Notification.Action acao(Context c, int icone, String rotulo, String acao,
                                            Bundle ex, int codigo) {
        Intent it = new Intent(c, AcaoReceiver.class).setAction(acao);
        it.putExtras(ex);
        PendingIntent pi = PendingIntent.getBroadcast(c, 200000 + codigo, it,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Action.Builder(Icon.createWithResource(c, icone), rotulo, pi).build();
    }

    // ----- respostas dadas nos botões do aviso e estado do dia -----

    static String chaveHoje() {
        Calendar x = Calendar.getInstance();
        return String.format(Locale.US, "%04d-%02d-%02d", x.get(Calendar.YEAR),
                x.get(Calendar.MONTH) + 1, x.get(Calendar.DAY_OF_MONTH));
    }

    /** Guarda uma resposta ("Bebi", "Feito") até a página abrir e aplicar no registro do dia. */
    static synchronized void registrarAcao(Context c, String tipo, String id, int ml) {
        try {
            SharedPreferences p = prefs(c);
            JSONArray fila = new JSONArray(p.getString("fila", "[]"));
            if (fila.length() >= 200) return;
            JSONObject o = new JSONObject();
            o.put("tipo", tipo == null ? "" : tipo);
            o.put("id", id == null ? "" : id);
            o.put("ml", ml);
            o.put("dk", chaveHoje());
            o.put("q", System.currentTimeMillis());
            fila.put(o);
            p.edit().putString("fila", fila.toString()).apply();
        } catch (Exception ignorado) {
            // sem fila, a resposta se perde, mas o app segue
        }
    }

    /** Entrega (e esvazia) a fila de respostas, em JSON. */
    static synchronized String consumirAcoes(Context c) {
        SharedPreferences p = prefs(c);
        String f = p.getString("fila", "[]");
        if (!"[]".equals(f)) p.edit().putString("fila", "[]").apply();
        return f;
    }

    /** A página informa o que já foi feito hoje: {"dk","ml","meta","feitos":[ids],"livres":[datas de pausa]}. */
    static String salvarDia(Context c, String json) {
        try {
            JSONObject o = new JSONObject(json);
            JSONObject limpo = new JSONObject();
            limpo.put("dk", corta(o.optString("dk", ""), 10));
            limpo.put("ml", Math.max(0, o.optLong("ml", 0)));
            limpo.put("meta", Math.max(0, o.optLong("meta", 0)));
            JSONArray fe = new JSONArray();
            JSONArray origem = o.optJSONArray("feitos");
            for (int k = 0; origem != null && k < origem.length() && k < 100; k++) {
                fe.put(corta(origem.optString(k, ""), 40));
            }
            limpo.put("feitos", fe);
            // dias de pausa (dia livre ou doente): sem avisos nesses dias
            JSONArray lv = new JSONArray();
            JSONArray lo = o.optJSONArray("livres");
            for (int k = 0; lo != null && k < lo.length() && k < 62; k++) {
                String d = corta(lo.optString(k, ""), 10);
                if (d.length() == 10) lv.put(d);
            }
            limpo.put("livres", lv);
            prefs(c).edit().putString("dia", limpo.toString()).apply();
            return "ok";
        } catch (Exception e) {
            return "erro: " + e.getMessage();
        }
    }

    /** Verdadeiro se o lembrete já não faz sentido: meta de água batida ou item já marcado hoje. */
    static boolean jaFeito(Context c, JSONObject e) {
        try {
            SharedPreferences p = prefs(c);
            return jaFeito(e, new JSONObject(p.getString("dia", "{}")),
                    new JSONArray(p.getString("fila", "[]")), chaveHoje());
        } catch (Exception ignorado) {
            return false; // sem estado, avisa normalmente
        }
    }

    /** Regra pura (testável): e = lembrete, dia = estado enviado pela página, fila = respostas dos botões. */
    static boolean jaFeito(JSONObject e, JSONObject dia, JSONArray fila, String hoje) {
        JSONArray livres = dia.optJSONArray("livres");
        for (int j = 0; livres != null && j < livres.length(); j++) {
            if (hoje.equals(livres.optString(j))) return true; // dia livre ou de doença: não avisa
        }
        String k = e.optString("k", "");
        if (k.length() == 0) return false;
        boolean mesmoDia = hoje.equals(dia.optString("dk", ""));
        if (k.equals("agua")) {
            long ml = mesmoDia ? dia.optLong("ml", 0) : 0;
            long meta = dia.optLong("meta", 0);
            for (int j = 0; j < fila.length(); j++) {
                JSONObject a = fila.optJSONObject(j);
                if (a != null && "agua".equals(a.optString("tipo")) && hoje.equals(a.optString("dk"))) {
                    ml += a.optLong("ml", 0);
                }
            }
            return meta > 0 && ml >= meta;
        }
        String id = e.optString("id", "");
        if (id.length() == 0) return false;
        JSONArray fe = dia.optJSONArray("feitos");
        for (int j = 0; mesmoDia && fe != null && j < fe.length(); j++) {
            if (id.equals(fe.optString(j))) return true;
        }
        for (int j = 0; j < fila.length(); j++) {
            JSONObject a = fila.optJSONObject(j);
            if (a != null && k.equals(a.optString("tipo")) && id.equals(a.optString("id"))
                    && hoje.equals(a.optString("dk"))) {
                return true;
            }
        }
        return false;
    }

    /** "Adiar 10 min": agenda o mesmo aviso de novo daqui a 10 minutos. */
    static void adiar(Context c, Bundle ex) {
        if (ex == null) return;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        Intent it = new Intent(c, AcaoReceiver.class).setAction(AcaoReceiver.SONECA);
        it.putExtras(ex);
        int idx = ex.getInt("idx", 0);
        PendingIntent pi = PendingIntent.getBroadcast(c, 300000 + idx, it,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long quando = System.currentTimeMillis() + 10L * 60L * 1000L;
        try {
            if (podeExato(c)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi);
            }
        } catch (SecurityException semPermissao) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi);
        }
    }

    /** O aviso adiado chegou: mostra de novo, a menos que o item tenha sido feito nesse meio tempo. */
    static void reavisar(Context c, Bundle ex) {
        if (ex == null) return;
        try {
            JSONObject e = new JSONObject();
            e.put("k", ex.getString("tipo", ""));
            e.put("id", ex.getString("id", ""));
            if (jaFeito(c, e)) return;
            notificar(c, ex.getInt("nid", 100), ex.getString("t", "Rotina"), ex.getString("d", ""),
                    ex.getString("perfil", ""), ex);
        } catch (Exception ignorado) {
            // nada a fazer
        }
    }
}
