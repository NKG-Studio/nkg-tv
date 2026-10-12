package main

import (
	"encoding/json"
	"path/filepath"
	"strings"
	"sync"
	"testing"
)

func TestNativeBridgeRoundTrip(t *testing.T) {
	store := stateStore{path: filepath.Join(t.TempDir(), "state.json")}
	if raw, e := store.Load(); e != nil || raw != "{}" {
		t.Fatal(raw, e)
	}
	c, e := parseCatalog([]byte("{\"format\":\"nkg-catalog-v1\",\"videos\":[{\"id\":\"1\",\"title\":\"" + strings.Repeat("中", 256) + "\",\"url\":\"https://example.org/a.mp4\"}]}"))
	if e != nil {
		t.Fatal(e)
	}
	raw, _ := json.Marshal(map[string]any{"videos": c.Videos, "favorites": map[string]Video{"1": c.Videos[0]}, "history": map[string]any{"1": map[string]any{"video": c.Videos[0], "position": 5}}})
	if e = store.Save(string(raw)); e != nil {
		t.Fatal(e)
	}
	loaded, e := store.Load()
	if e != nil || loaded != string(raw) {
		t.Fatal(e)
	}
	if e = store.Save("{bad"); e == nil {
		t.Fatal("accepted invalid JSON")
	}
	loaded, e = store.Load()
	if e != nil || loaded != string(raw) {
		t.Fatal("corrupted after failed save")
	}
}
func TestConcurrentSavesRemainComplete(t *testing.T) {
	store := stateStore{path: filepath.Join(t.TempDir(), "state.json")}
	var wg sync.WaitGroup
	for i := 0; i < 20; i++ {
		wg.Add(1)
		go func(i int) {
			defer wg.Done()
			b, _ := json.Marshal(map[string]int{"n": i})
			if e := store.Save(string(b)); e != nil {
				t.Error(e)
			}
		}(i)
	}
	wg.Wait()
	raw, e := store.Load()
	if e != nil || !json.Valid([]byte(raw)) {
		t.Fatal(raw, e)
	}
}
