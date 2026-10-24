package os.cintyez.launcher;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/* WebView 直连模型接口会被 CORS 挡；这里用原生 HTTP 代发，JS 通过 CintyezNative.chat 调用。 */
public class KemonBridge {
    private final WebView web;
    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    KemonBridge(WebView web) {
        this.web = web;
    }

    @JavascriptInterface
    public void chat(int id, String url, String key, String body) {
        pool.execute(() -> {
            String payload;
            try {
                payload = request(url, key, body);
            } catch (Exception e) {
                String msg = e.getMessage();
                payload = error(msg == null ? e.getClass().getSimpleName() : msg);
            }
            String js = "window.kemonChatReply&&window.kemonChatReply("
                + id + "," + JSONObject.quote(payload) + ")";
            web.post(() -> web.evaluateJavascript(js, null));
        });
    }

    @JavascriptInterface
    public String clipboard() {
        try {
            ClipboardManager cm = (ClipboardManager)
                web.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return "";
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return "";
            CharSequence text = clip.getItemAt(0).coerceToText(web.getContext());
            return text == null ? "" : text.toString();
        } catch (Exception e) {
            return "";
        }
    }

    void shutdown() {
        pool.shutdownNow();
    }

    private String request(String url, String key, String body) throws Exception {
        URL target = new URL(url);
        String scheme = target.getProtocol();
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            throw new IllegalArgumentException("只支持 http/https 接口地址");
        }

        HttpURLConnection conn = (HttpURLConnection) target.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        if (key != null && !key.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + key);
        }
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String text = read(in);
        if (code < 200 || code >= 300) {
            return error("HTTP " + code + " · " + cut(text, 240));
        }

        JSONObject root = new JSONObject(text);
        JSONArray choices = root.optJSONArray("choices");
        String content = "";
        if (choices != null && choices.length() > 0) {
            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            if (message != null) content = message.optString("content", "");
        }
        return new JSONObject()
            .put("ok", true)
            .put("content", content)
            .put("model", root.optString("model", ""))
            .toString();
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader =
                 new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static String error(String message) {
        try {
            return new JSONObject()
                .put("ok", false)
                .put("error", message == null ? "未知错误" : message)
                .toString();
        } catch (Exception e) {
            return "{\"ok\":false,\"error\":\"未知错误\"}";
        }
    }

    private static String cut(String s, int max) {
        if (s == null) return "";
        s = s.trim();
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
