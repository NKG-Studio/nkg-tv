//go:build windows

package main

import (
	"crypto/rand"
	"embed"
	"encoding/hex"
	"encoding/json"
	"fmt"
	webview2 "github.com/jchv/go-webview2"
	"io/fs"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"syscall"
	"unsafe"
)

//go:embed ui/* licenses/*
var assets embed.FS

func alert(s string) {
	user32 := syscall.NewLazyDLL("user32.dll")
	msg, _ := syscall.UTF16PtrFromString(s)
	title, _ := syscall.UTF16PtrFromString("NKG TV")
	user32.NewProc("MessageBoxW").Call(0, uintptr(unsafe.Pointer(msg)), uintptr(unsafe.Pointer(title)), 0x10)
}
func main() {
	dir, e := os.UserConfigDir()
	if e != nil {
		alert(e.Error())
		return
	}
	dir = filepath.Join(dir, "NKG-TV")
	if e = os.MkdirAll(dir, 0700); e != nil {
		alert(e.Error())
		return
	}
	w := webview2.NewWithOptions(webview2.WebViewOptions{Debug: false, AutoFocus: true, DataPath: filepath.Join(dir, "webview"), WindowOptions: webview2.WindowOptions{Title: "NKG TV · Preview", Width: 1180, Height: 780, Center: true}})
	if w == nil {
		alert("无法启动 WebView2。需要 Windows 10/11 上已安装的 Microsoft Edge WebView2 Runtime。本程序不会自动下载或接受其许可。\nhttps://developer.microsoft.com/microsoft-edge/webview2/")
		return
	}
	defer w.Destroy()
	path := filepath.Join(dir, "state.json")
	w.Bind("nativeLoad", func() (string, error) {
		b, e := os.ReadFile(path)
		if os.IsNotExist(e) {
			return "{}", nil
		}
		return string(b), e
	})
	w.Bind("nativeSave", func(s string) error {
		if len(s) > 4<<20 || !json.Valid([]byte(s)) {
			return fmt.Errorf("invalid state")
		}
		tmp := path + ".tmp"
		if e := os.WriteFile(tmp, []byte(s), 0600); e != nil {
			return e
		}
		return os.Rename(tmp, path)
	})
	w.Bind("nativeCatalog", func(raw string) (Catalog, error) { return loadCatalog(raw) })
	tokenBytes := make([]byte, 24)
	if _, e = rand.Read(tokenBytes); e != nil {
		alert(e.Error())
		return
	}
	prefix := "/" + hex.EncodeToString(tokenBytes) + "/"
	listener, e := net.Listen("tcp", "127.0.0.1:0")
	if e != nil {
		alert(e.Error())
		return
	}
	defer listener.Close()
	tree, _ := fs.Sub(assets, "ui")
	files := http.FileServer(http.FS(tree))
	mux := http.NewServeMux()
	mux.HandleFunc(prefix, func(rw http.ResponseWriter, r *http.Request) {
		if r.Method != "GET" && r.Method != "HEAD" {
			http.Error(rw, "method", 405)
			return
		}
		rw.Header().Set("Content-Security-Policy", "default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self'; img-src 'self' data:; media-src 'self' https: blob:; connect-src 'self' https:; object-src 'none'; base-uri 'none'; frame-src 'none'")
		r.URL.Path = strings.TrimPrefix(r.URL.Path, strings.TrimSuffix(prefix, "/"))
		files.ServeHTTP(rw, r)
	})
	server := &http.Server{Handler: mux}
	defer server.Close()
	go server.Serve(listener)
	w.Navigate("http://" + listener.Addr().String() + prefix)
	w.Run()
}
