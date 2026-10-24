package os.cintyez.launcher;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.SystemClock;
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

/* WebView 直连模型接口会被 CORS 挡；这里用原生 HTTP 代发，JS 通过 CintyezNative 调用。 */
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
                payload = error(messageOf(e));
            }
            emit(id, "kemonChatReply", payload);
        });
    }

    @JavascriptInterface
    public void chatStream(int id, String url, String key, String body) {
        pool.execute(() -> stream(id, url, key, body));
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

    private void stream(int id, String url, String key, String body) {
        HttpURLConnection conn = null;
        try {
            conn = open(url, key, body, true);
            int code = conn.getResponseCode();
            InputStream in = code >= 200 && code < 300
                ? conn.getInputStream() : conn.getErrorStream();

            if (code < 200 || code >= 300) {
                emit(id, "kemonChatError", describeError(code, read(in)));
                return;
            }

            String type = conn.getContentType();
            if (type == null || !type.contains("text/event-stream")) {
                String content = contentOf(new JSONObject(read(in)));
                if (!content.isEmpty()) emit(id, "kemonChatChunk", content);
                emit(id, "kemonChatDone", "");
                return;
            }

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder pending = new StringBuilder();
            long last = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if ("[DONE]".equals(data)) break;
                String piece = deltaOf(data);
                if (piece.isEmpty()) continue;
                pending.append(piece);
                long now = SystemClock.uptimeMillis();
                if (now - last >= 40) {
                    emit(id, "kemonChatChunk", pending.toString());
                    pending.setLength(0);
                    last = now;
                }
            }
            if (pending.length() > 0) emit(id, "kemonChatChunk", pending.toString());
            emit(id, "kemonChatDone", "");
        } catch (Exception e) {
            emit(id, "kemonChatError", messageOf(e));
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String request(String url, String key, String body) throws Exception {
        HttpURLConnection conn = open(url, key, body, false);
        try {
            int code = conn.getResponseCode();
            InputStream in = code >= 200 && code < 300
                ? conn.getInputStream() : conn.getErrorStream();
            String text = read(in);
            if (code < 200 || code >= 300) return error(describeError(code, text));

            JSONObject root = new JSONObject(text);
            return new JSONObject()
                .put("ok", true)
                .put("content", contentOf(root))
                .put("model", root.optString("model", ""))
                .toString();
        } finally {
            conn.disconnect();
        }
    }

    private HttpURLConnection open(String url, String key, String body, boolean stream)
        throws Exception {
        URL target = new URL(url);
        String scheme = target.getProtocol();
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            throw new IllegalArgumentException("只支持 http/https 接口地址");
        }

        HttpURLConnection conn = (HttpURLConnection) target.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(90000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        if (stream) conn.setRequestProperty("Accept", "text/event-stream");
        if (key != null && !key.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + key);
        }
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return conn;
    }

    private static String deltaOf(String data) {
        try {
            JSONObject root = new JSONObject(data);
            JSONArray choices = root.optJSONArray("choices");
            if (choices == null || choices.length() == 0) return "";
            JSONObject choice = choices.getJSONObject(0);
            String content = textOf(choice.optJSONObject("delta"), "content");
            return content.isEmpty() ? textOf(choice.optJSONObject("message"), "content") : content;
        } catch (Exception e) {
            return "";
        }
    }

    private static String contentOf(JSONObject root) {
        JSONArray choices = root.optJSONArray("choices");
        if (choices == null || choices.length() == 0) return "";
        JSONObject choice = choices.optJSONObject(0);
        return choice == null ? "" : textOf(choice.optJSONObject("message"), "content");
    }

    /* optString 会把 JSON null 变成字符串 "null"（Android org.json 的实现），
       所以显式 null 的 delta 会污染流式正文，这里只接受真正的字符串。 */
    private static String textOf(JSONObject obj, String key) {
        if (obj == null) return "";
        Object value = obj.opt(key);
        return value instanceof String ? (String) value : "";
    }

    private void emit(int id, String fn, String text) {
        String js = "window." + fn + "&&window." + fn + "("
            + id + "," + JSONObject.quote(text) + ")";
        web.post(() -> {
            try {
                web.evaluateJavascript(js, null);
            } catch (Exception ignored) {
                // WebView 已销毁，回调直接丢弃
            }
        });
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

    private static String describeError(int code, String text) {
        String body = text == null ? "" : text.trim();
        if (body.isEmpty()) return "HTTP " + code;
        if (body.startsWith("<")) {
            return "HTTP " + code + " · 返回了 HTML，接口地址可能不是 API 端点（填到 /v1 即可）";
        }
        try {
            JSONObject root = new JSONObject(body);
            JSONObject err = root.optJSONObject("error");
            String message = err == null ? "" : err.optString("message", "");
            if (message.isEmpty()) message = root.optString("message", "");
            if (!message.isEmpty()) return "HTTP " + code + " · " + cut(message, 160);
        } catch (Exception ignored) {
            // 不是 JSON，退回原文
        }
        return "HTTP " + code + " · " + cut(body, 160);
    }

    private static String messageOf(Exception e) {
        String message = e.getMessage();
        return message == null || message.isEmpty() ? e.getClass().getSimpleName() : message;
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
