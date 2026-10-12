package studio.nkg.tv;

import org.json.*;
import java.util.*;

final class Catalog {
    static final String DEFAULT_FEED = "https://tv.lfzxb.top/sources/feed.json";
    static final class Video {
        final String id, title, description, url, source;
        Video(String id, String title, String description, String url, String source) { this.id=id; this.title=title; this.description=description; this.url=url; this.source=source; }
        JSONObject json() throws JSONException { return new JSONObject().put("id",id).put("title",title).put("description",description).put("url",url).put("source",source); }
    }
    static List<Video> parse(String text) throws JSONException {
        JSONObject root = new JSONObject(text);
        if (!"nkg-catalog-v1".equals(root.optString("format"))) throw new JSONException("尚不支持此订阅格式。当前仅支持明确标记的 nkg-catalog-v1；指定订阅的实际格式尚待核实。");
        JSONArray list = root.getJSONArray("videos");
        if (list.length() > 2000) throw new JSONException("最多允许 2000 条视频");
        List<Video> items = new ArrayList<>(); Set<String> ids = new HashSet<>();
        for (int i=0;i<list.length();i++) {
            JSONObject o=list.getJSONObject(i);
            String id=field(o,"id",128), title=field(o,"title",256), url=field(o,"url",4096);
            try { Net.checked(url); } catch (IllegalArgumentException e) { throw new JSONException("第 " + (i+1) + " 条视频：" + e.getMessage()); }
            if (!ids.add(id)) throw new JSONException("视频 ID 重复：" + id);
            items.add(new Video(id,title,o.optString("description",""),url,o.optString("source","自定义来源")));
        }
        return items;
    }
    static String field(JSONObject o,String key,int max) throws JSONException {
        Object v=o.get(key); if (!(v instanceof String)) throw new JSONException(key+" 必须是文本");
        String s=((String)v).trim(); if(s.isEmpty() || s.length()>max) throw new JSONException(key+" 长度无效"); return s;
    }
    static List<Video> search(List<Video> all,String query) {
        String q=query.trim().toLowerCase(Locale.ROOT); List<Video> result=new ArrayList<>();
        for(Video v:all) if ((v.title+" "+v.description+" "+v.source).toLowerCase(Locale.ROOT).contains(q)) result.add(v);
        return result;
    }
}
