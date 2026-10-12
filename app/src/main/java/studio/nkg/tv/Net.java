package studio.nkg.tv;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

final class Net {
    static final int MAX_BYTES = 2 * 1024 * 1024;
    static URI checked(String raw) {
        URI u;
        try { u = URI.create(raw.trim()); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("地址格式无效"); }
        if (!"https".equalsIgnoreCase(u.getScheme()) || u.getHost() == null || u.getUserInfo() != null || u.getFragment() != null)
            throw new IllegalArgumentException("请使用不含账号或片段的 HTTPS 地址");
        return u;
    }
    static String get(String raw) throws IOException {
        String next = checked(raw).toString();
        for (int redirect = 0; redirect <= 3; redirect++) {
            HttpURLConnection c = (HttpURLConnection) new URL(next).openConnection();
            c.setConnectTimeout(8000); c.setReadTimeout(10000); c.setInstanceFollowRedirects(false);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "NKG-TV/0.1");
            try {
                int code = c.getResponseCode();
                if (code >= 300 && code < 400) {
                    String location = c.getHeaderField("Location");
                    if (location == null) throw new IOException("重定向缺少目标地址");
                    next = checked(URI.create(next).resolve(location).toString()).toString(); continue;
                }
                if (code != 200) throw new IOException("服务器返回 HTTP " + code);
                if (c.getContentLengthLong() > MAX_BYTES) throw new IOException("订阅超过 2 MiB 限制");
                try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[8192]; int n;
                    while ((n = in.read(buf)) != -1) { if (out.size() + n > MAX_BYTES) throw new IOException("响应超过 2 MiB 限制"); out.write(buf, 0, n); }
                    return out.toString(StandardCharsets.UTF_8.name());
                }
            } finally { c.disconnect(); }
        }
        throw new IOException("重定向次数过多");
    }
}
