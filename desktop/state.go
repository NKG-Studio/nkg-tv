package main

import (
	"encoding/json"
	"errors"
	"io"
	"os"
	"path/filepath"
	"sync"
)

type stateStore struct {
	path string
	mu   sync.Mutex
}

func (s *stateStore) Load() (string, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	f, e := os.Open(s.path)
	if os.IsNotExist(e) {
		return "{}", nil
	}
	if e != nil {
		return "", e
	}
	defer f.Close()
	b, e := io.ReadAll(io.LimitReader(f, (4<<20)+1))
	if e != nil {
		return "", e
	}
	if len(b) > 4<<20 || !json.Valid(b) {
		return "", errors.New("invalid stored state")
	}
	return string(b), nil
}
func (s *stateStore) Save(raw string) error {
	if len(raw) > 4<<20 || !json.Valid([]byte(raw)) {
		return errors.New("invalid state")
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	f, e := os.CreateTemp(filepath.Dir(s.path), "state-*.tmp")
	if e != nil {
		return e
	}
	name := f.Name()
	defer os.Remove(name)
	if _, e = f.WriteString(raw); e != nil {
		f.Close()
		return e
	}
	if e = f.Sync(); e != nil {
		f.Close()
		return e
	}
	if e = f.Close(); e != nil {
		return e
	}
	return os.Rename(name, s.path)
}
