package studio.nkg.tv;

import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

final class Store {
    private final SharedPreferences prefs;
    Store(SharedPreferences prefs) { this.prefs=prefs; }
    String feed() { return prefs.getString("feed",Catalog.DEFAULT_FEED); }
    void feed(String url) { prefs.edit().putString("feed",url).apply(); }
    String cached() { return prefs.getString("catalog",""); }
    void cache(String json) { prefs.edit().putString("catalog",json).apply(); }
    boolean favorite(Catalog.Video v) { return prefs.getBoolean("fav:"+v.id,false); }
    void toggle(Catalog.Video v) { remember(v); prefs.edit().putBoolean("fav:"+v.id,!favorite(v)).apply(); }
    void remember(Catalog.Video v) { try { prefs.edit().putString("item:"+v.id,v.json().toString()).apply(); } catch(JSONException ignored) { } }
    void progress(Catalog.Video v,int position) { remember(v); prefs.edit().putInt("pos:"+v.id,Math.max(0,position)).putLong("time:"+v.id,System.currentTimeMillis()).apply(); }
    int position(Catalog.Video v) { return prefs.getInt("pos:"+v.id,0); }
    List<Catalog.Video> saved(boolean favorites) {
        List<Catalog.Video> out=new ArrayList<>();
        for(String key:prefs.getAll().keySet()) if(key.startsWith("item:")) {
            try { JSONObject o=new JSONObject(prefs.getString(key,"{}"));
                Catalog.Video v=new Catalog.Video(o.getString("id"),o.getString("title"),o.optString("description"),o.getString("url"),o.optString("source"));
                if(favorites ? favorite(v) : prefs.contains("time:"+v.id)) out.add(v);
            } catch(JSONException ignored) { }
        }
        out.sort((a,b)->Long.compare(prefs.getLong("time:"+b.id,0),prefs.getLong("time:"+a.id,0))); return out;
    }
    void clearHistory() { SharedPreferences.Editor e=prefs.edit(); for(String key:prefs.getAll().keySet()) if(key.startsWith("time:")||key.startsWith("pos:")) e.remove(key); e.apply(); }
}
