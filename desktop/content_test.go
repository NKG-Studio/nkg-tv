package main

import (
	"encoding/json"
	"strings"
	"testing"
)

func TestCatalog(t *testing.T) {
	good := []byte(`{"format":"nkg-catalog-v1","videos":[{"id":"1","title":"片名","url":"https://example.org/a.mp4"}]}`)
	if c, e := parseCatalog(good); e != nil || len(c.Videos) != 1 {
		t.Fatal(c, e)
	}
	for _, s := range []string{`{}`, `{"format":"nkg-catalog-v1","videos":[{"id":"1","title":"x","url":"http://example.org"}]}`, `{"format":"nkg-catalog-v1","videos":[{"id":"1","title":"x","url":"https://x"},{"id":"1","title":"x","url":"https://y"}]}`} {
		if _, e := parseCatalog([]byte(s)); e == nil {
			t.Fatal("accepted", s)
		}
	}
}
func TestURLs(t *testing.T) {
	for _, s := range []string{"http://example.org", "javascript:x", "https://user:pw@example.org", "https://example.org/#a"} {
		if validURL(s) == nil {
			t.Fatal(s)
		}
	}
	if validURL("https://example.org/a") != nil {
		t.Fatal("https rejected")
	}
}

func TestTitlePersistenceLimits(t *testing.T) {
	for _, s := range []struct {
		title string
		ok    bool
	}{{strings.Repeat("中", 256), true}, {strings.Repeat("a", 257), false}, {strings.Repeat("😀", 129), false}} {
		data, _ := json.Marshal(Catalog{Format: "nkg-catalog-v1", Videos: []Video{{ID: "x", Title: s.title, URL: "https://example.org/a.mp4"}}})
		_, e := parseCatalog(data)
		if (e == nil) != s.ok {
			t.Fatalf("UTF16 title consistency: %v", e)
		}
	}
}
