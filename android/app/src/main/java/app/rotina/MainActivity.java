package app.rotina;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

/**
 * Casca do app Rotina Fitness: mostra o index.html guardado dentro do APK
 * (assets) num WebView, sem precisar de internet. Os dados ficam no aparelho.
 *
 * O WebView não sabe salvar downloads gerados pela página (backup, relatórios,
 * lembretes). Por isso a página chama RotinaAndroid.salvar(...), que grava o
 * arquivo na pasta Downloads e, para lembretes (.ics), abre o Calendário.
 */
public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String INICIO = "https://" + HOST + "/assets/index.html";
    private static final int PEDIDO_ARQUIVO = 1;
    private static final int PEDIDO_CAMERA = 2;

    private WebView web;
    private ValueCallback<Uri[]> escolha;
    private Uri fotoUri;

    @Override
    protected void onCreate(Bundle estado) {
        super.onCreate(estado);

        web = new WebView(this);
        setContentView(web);

        WebSettings cfg = web.getSettings();
        cfg.setJavaScriptEnabled(true);
        cfg.setDomStorageEnabled(true);
        cfg.setAllowFileAccess(false);
        cfg.setMediaPlaybackRequiresUserGesture(true);

        web.addJavascriptInterface(new Ponte(), "RotinaAndroid");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return servir(r.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost())) {
                    return false;
                }
                abrirFora(u);
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> retorno,
                                             FileChooserParams params) {
                if (escolha != null) {
                    escolha.onReceiveValue(null);
                }
                escolha = retorno;
                if (params.isCaptureEnabled() && pedeImagem(params.getAcceptTypes()) && abrirCamera()) {
                    return true;
                }
                try {
                    startActivityForResult(params.createIntent(), PEDIDO_ARQUIVO);
                } catch (Exception e) {
                    escolha = null;
                    Toast.makeText(MainActivity.this,
                            "Não consegui abrir o seletor de arquivos.", Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }
        });

        web.loadUrl(INICIO);
    }

    private static boolean pedeImagem(String[] tipos) {
        if (tipos == null) return false;
        for (String t : tipos) {
            if (t != null && t.startsWith("image")) return true;
        }
        return false;
    }

    /** Abre o app de câmera para a foto do perfil. Se algo falhar, volta ao seletor de arquivos. */
    private boolean abrirCamera() {
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, "rotina_" + System.currentTimeMillis() + ".jpg");
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Rotina Fitness");
            fotoUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (fotoUri == null) return false;
            Intent it = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            it.putExtra(MediaStore.EXTRA_OUTPUT, fotoUri);
            it.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(it, PEDIDO_CAMERA);
            return true;
        } catch (Exception e) {
            descartarFoto();
            return false;
        }
    }

    private void descartarFoto() {
        if (fotoUri != null) {
            try {
                getContentResolver().delete(fotoUri, null, null);
            } catch (Exception ignorado) {
                // sem problema: a foto vazia some sozinha
            }
            fotoUri = null;
        }
    }

    @Override
    protected void onActivityResult(int pedido, int resultado, Intent dados) {
        if (pedido == PEDIDO_CAMERA) {
            if (escolha != null) {
                if (resultado == RESULT_OK && fotoUri != null) {
                    escolha.onReceiveValue(new Uri[]{fotoUri});
                } else {
                    descartarFoto();
                    escolha.onReceiveValue(null);
                }
                escolha = null;
            }
            fotoUri = null;
            return;
        }
        if (pedido == PEDIDO_ARQUIVO) {
            if (escolha != null) {
                escolha.onReceiveValue(
                        WebChromeClient.FileChooserParams.parseResult(resultado, dados));
                escolha = null;
            }
            return;
        }
        super.onActivityResult(pedido, resultado, dados);
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
        }
        super.onDestroy();
    }

    /** Entrega os arquivos de assets/ como se viessem de um site https. */
    private WebResourceResponse servir(Uri u) {
        if (!HOST.equals(u.getHost())) {
            return null; // fontes e outros endereços externos seguem pela internet
        }
        String caminho = u.getPath();
        String prefixo = "/assets/";
        if (caminho == null || !caminho.startsWith(prefixo)) {
            return naoEncontrado();
        }
        String nome = caminho.substring(prefixo.length());
        if (nome.length() == 0) {
            nome = "index.html";
        }
        try {
            InputStream in = getAssets().open(nome);
            String tipo = tipoDe(nome);
            String codificacao = tipo.startsWith("text/") || tipo.endsWith("json") ? "utf-8" : null;
            return new WebResourceResponse(tipo, codificacao, in);
        } catch (Exception e) {
            return naoEncontrado();
        }
    }

    private WebResourceResponse naoEncontrado() {
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found",
                new HashMap<String, String>(), new ByteArrayInputStream(new byte[0]));
    }

    private static String tipoDe(String nome) {
        String n = nome.toLowerCase();
        if (n.endsWith(".html")) return "text/html";
        if (n.endsWith(".js")) return "text/javascript";
        if (n.endsWith(".css")) return "text/css";
        if (n.endsWith(".json") || n.endsWith(".webmanifest")) return "application/json";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    /** Links externos (agenda do Google, etc.) abrem no navegador do aparelho. */
    private void abrirFora(Uri u) {
        String esquema = u.getScheme();
        if (esquema == null) return;
        if (!(esquema.equals("http") || esquema.equals("https")
                || esquema.equals("mailto") || esquema.equals("tel"))) {
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Nenhum app para abrir este link.", Toast.LENGTH_LONG).show();
        }
    }

    private void abrirArquivo(Uri uri, String tipo) {
        Intent it = new Intent(Intent.ACTION_VIEW);
        it.setDataAndType(uri, tipo);
        it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(it);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Arquivo salvo em Downloads.", Toast.LENGTH_LONG).show();
        }
    }

    /** Ponte chamada pela página: window.RotinaAndroid.salvar(nome, tipo, base64). */
    public class Ponte {
        @JavascriptInterface
        public String salvar(String nome, final String tipo, String base64) {
            try {
                byte[] dados = Base64.decode(base64, Base64.DEFAULT);
                String limpo = nome.replaceAll("[\\\\/:*?\"<>|]", "_");

                ContentValues valores = new ContentValues();
                valores.put(MediaStore.Downloads.DISPLAY_NAME, limpo);
                valores.put(MediaStore.Downloads.MIME_TYPE, tipo);
                valores.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                valores.put(MediaStore.Downloads.IS_PENDING, 1);

                ContentResolver cr = getContentResolver();
                final Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores);
                if (uri == null) {
                    return "sem acesso à pasta Downloads";
                }
                OutputStream saida = cr.openOutputStream(uri);
                if (saida == null) {
                    return "não consegui gravar o arquivo";
                }
                try {
                    saida.write(dados);
                } finally {
                    saida.close();
                }
                ContentValues pronto = new ContentValues();
                pronto.put(MediaStore.Downloads.IS_PENDING, 0);
                cr.update(uri, pronto, null, null);

                final String aviso = "Salvo em Downloads: " + limpo;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, aviso, Toast.LENGTH_LONG).show();
                        if ("text/calendar".equals(tipo)) {
                            abrirArquivo(uri, tipo);
                        }
                    }
                });
                return "ok";
            } catch (Exception e) {
                return String.valueOf(e.getMessage());
            }
        }
    }
}
