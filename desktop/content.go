package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
	"unicode/utf16"
)

const defaultFeed = "https://tv.lfzxb.top/sources/feed.json"

type Video struct {
	ID          string `json:"id"`
	Title       string `json:"title"`
	Description string `json:"description"`
	Source      string `json:"source"`
	URL         string `json:"url"`
}
type Catalog struct {
	Format string  `json:"format"`
	Videos []Video `json:"videos"`
}

func validURL(raw string) error {
	u, e := url.Parse(strings.TrimSpace(raw))
	if e != nil || u.Scheme != "https" || u.Hostname() == "" || u.User != nil || u.Fragment != "" {
		return errors.New("请使用不含账号与片段的 HTTPS 地址")
	}
	return nil
}
func parseCatalog(data []byte) (Catalog, error) {
	var c Catalog
	if len(data) > 2<<20 {
		return c, errors.New("目录超过 2 MiB")
	}
	if e := json.Unmarshal(data, &c); e != nil {
		return c, errors.New("响应不是有效的目录 JSON")
	}
	if c.Format != "nkg-catalog-v1" {
		return c, errors.New("未知订阅格式：目前仅支持 nkg-catalog-v1；指定 feed.json 格式尚待实际样本适配")
	}
	if c.Videos == nil {
		return c, errors.New("videos 必须是数组")
	}
	if len(c.Videos) > 2000 {
		return c, errors.New("目录超过 2000 部影片")
	}
	ids := map[string]bool{}
	for _, v := range c.Videos {
		if strings.TrimSpace(v.ID) == "" || len(utf16.Encode([]rune(v.ID))) > 128 || strings.TrimSpace(v.Title) == "" || len(utf16.Encode([]rune(v.Title))) > 256 || len(utf16.Encode([]rune(v.URL))) > 4096 {
			return c, errors.New("影片字段缺失或过长")
		}
		if ids[v.ID] {
			return c, errors.New("影片 ID 重复")
		}
		ids[v.ID] = true
		if e := validURL(v.URL); e != nil {
			return c, e
		}
	}
	return c, nil
}
func loadCatalog(raw string) (Catalog, error) {
	var empty Catalog
	if e := validURL(raw); e != nil {
		return empty, e
	}
	client := http.Client{Timeout: 18 * time.Second, CheckRedirect: func(r *http.Request, via []*http.Request) error {
		if len(via) > 3 {
			return errors.New("重定向次数过多")
		}
		return validURL(r.URL.String())
	}}
	req, e := http.NewRequest("GET", raw, nil)
	if e != nil {
		return empty, e
	}
	req.Header.Set("Accept", "application/json")
	req.Header.Set("User-Agent", "NKG-TV/0.1")
	resp, e := client.Do(req)
	if e != nil {
		return empty, fmt.Errorf("连接失败或超时：%w", e)
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return empty, fmt.Errorf("订阅返回 HTTP %d", resp.StatusCode)
	}
	b, e := io.ReadAll(io.LimitReader(resp.Body, (2<<20)+1))
	if e != nil {
		return empty, e
	}
	return parseCatalog(b)
}
