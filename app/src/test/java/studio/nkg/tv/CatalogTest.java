package studio.nkg.tv;
import org.junit.Test;
import org.json.JSONException;
import static org.junit.Assert.*;
import java.util.*;
public class CatalogTest {
    private final String valid="{\"format\":\"nkg-catalog-v1\",\"videos\":[{\"id\":\"one\",\"title\":\"测试 Movie\",\"url\":\"https://example.org/video.mp4\"}]}";
    @Test public void parseAndSearch() throws Exception { List<Catalog.Video> v=Catalog.parse(valid);assertEquals(1,v.size());assertEquals(1,Catalog.search(v,"movie").size());assertEquals(1,Catalog.search(v,"测试").size());assertTrue(Catalog.search(v,"missing").isEmpty()); }
    @Test public void emptyIsValid() throws Exception { assertTrue(Catalog.parse("{\"format\":\"nkg-catalog-v1\",\"videos\":[]}").isEmpty()); }
    @Test(expected=JSONException.class) public void unknownFormatFails() throws Exception { Catalog.parse("{\"sites\":[]}"); }
    @Test(expected=JSONException.class) public void nonHttpsFails() throws Exception { Catalog.parse(valid.replace("https://","http://")); }
    @Test(expected=JSONException.class) public void missingTitleFails() throws Exception { Catalog.parse(valid.replace("\"title\":\"测试 Movie\",","")); }
    @Test(expected=JSONException.class) public void duplicateIdsFail() throws Exception { String item="{\"id\":\"x\",\"title\":\"x\",\"url\":\"https://example.org/a\"}";Catalog.parse("{\"format\":\"nkg-catalog-v1\",\"videos\":["+item+","+item+"]}"); }
    @Test(expected=IllegalArgumentException.class) public void credentialsRejected() { Net.checked("https://user:password@example.org/a"); }
    @Test(expected=IllegalArgumentException.class) public void scriptRejected() { Net.checked("javascript:alert(1)"); }
    @Test public void validUrl() { assertEquals("example.org",Net.checked(" https://example.org/a?q=1 ").getHost()); }
}
