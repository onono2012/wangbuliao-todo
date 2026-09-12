#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""忘不了 发布工具：GitHub Release + wbl-update.json + 123网盘 WebDAV 三渠道同步

用法：
  # 完整发布（创建 Release + 上传 APK + 提交 wbl-update.json + 上传 123网盘 + 全线路验证）
  python3 publish.py <apk路径> -v 1.2.0 -c 10200 -n notes.md

  # 仅提交/回滚 wbl-update.json（含 jsDelivr purge + 123网盘同步，不动 Release）
  python3 publish.py --commit-json wbl-update.json

  # 删除测试 Release + tag（E2E 清理）
  python3 publish.py --delete-tag v1.2.1

  # 上传任意文件到 123网盘 /app/ 目录
  python3 publish.py --dav-put <本地文件> [网盘文件名]

环境变量（勿写入文件/日志）：
  GITHUB_TOKEN      GitHub PAT（repo scope）
  WBL_WEBDAV_USER   123网盘 WebDAV 账号
  WBL_WEBDAV_PASS   123网盘 WebDAV 密码
"""
import argparse
import base64
import hashlib
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

OWNER = "onono2012"
REPO = "wangbuliao-todo"
API = f"https://api.github.com/repos/{OWNER}/{REPO}"
UPLOADS = f"https://uploads.github.com/repos/{OWNER}/{REPO}/releases"
RAW_JSON = f"https://raw.githubusercontent.com/{OWNER}/{REPO}/main/wbl-update.json"
JSDELIVR = f"https://cdn.jsdelivr.net/gh/{OWNER}/{REPO}@main/wbl-update.json"
PURGE = f"https://purge.jsdelivr.net/gh/{OWNER}/{REPO}@main/wbl-update.json"
DAV = "https://webdav.123pan.cn/webdav"
DAV_DIR = "/app"  # 网盘内存放目录：/webdav/app/
DL_PROXIES = ["https://gh-proxy.com/", "https://ghproxy.net/", "https://ghfast.top/"]

TOK = os.environ.get("GITHUB_TOKEN", "")
DAV_USER = os.environ.get("WBL_WEBDAV_USER", "")
DAV_PASS = os.environ.get("WBL_WEBDAV_PASS", "")


def gh(url, method="GET", data=None, ctype=None, accept="application/vnd.github+json", timeout=180):
    if not TOK:
        sys.exit("缺少环境变量 GITHUB_TOKEN")
    h = {
        "Authorization": f"Bearer {TOK}",
        "Accept": accept,
        "User-Agent": "wbl-publish",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    if ctype:
        h["Content-Type"] = ctype
    req = urllib.request.Request(url, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()


def dav_curl(args, timeout=900):
    if not DAV_USER or not DAV_PASS:
        sys.exit("缺少环境变量 WBL_WEBDAV_USER / WBL_WEBDAV_PASS")
    cmd = ["curl", "-sS", "-m", str(timeout), "-u", f"{DAV_USER}:{DAV_PASS}",
           "-o", "/dev/null", "-w", "%{http_code}"] + args
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(f"curl 失败: {r.stderr.strip()[:200]}")
    return r.stdout.strip()


def sha256_file(path):
    md = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            md.update(chunk)
    return md.hexdigest()


def build_json(apk_name, version, vc, sha, size, desc, asset_id):
    direct = f"https://github.com/{OWNER}/{REPO}/releases/download/v{version}/{apk_name}"
    routes = [f"{API}/releases/assets/{asset_id}"] if asset_id else []
    routes += [p + direct for p in DL_PROXIES] + [direct]
    return {
        "versionCode": vc,
        "versionName": version,
        "force": True,
        "desc": desc,
        "sha256": sha,
        "size": size,
        "fileName": apk_name,
        "routes": routes,
        "publishedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
    }


def commit_json(jtxt):
    """提交 wbl-update.json 到仓库 main（contents API），返回 True/False"""
    st, body = gh(f"{API}/contents/wbl-update.json")
    old_sha = None
    if st == 200:
        old_sha = json.loads(body).get("sha")
    payload = {
        "message": f"update: wbl-update.json {time.strftime('%Y-%m-%d %H:%M:%S')}",
        "content": base64.b64encode(jtxt.encode("utf-8")).decode(),
        "branch": "main",
    }
    if old_sha:
        payload["sha"] = old_sha
    st, body = gh(f"{API}/contents/wbl-update.json", method="PUT",
                  data=json.dumps(payload).encode(), ctype="application/json")
    if st not in (200, 201):
        print(f"  [WARN] 提交 json HTTP {st}: {body[:200]!r}")
        return False
    print("  ✓ wbl-update.json 已提交到 main")
    # jsDelivr 缓存刷新
    try:
        urllib.request.urlopen(PURGE, timeout=20).read()
        print("  ✓ jsDelivr 缓存已 purge")
    except Exception as e:
        print(f"  [WARN] jsDelivr purge 失败（不影响其他线路）: {e}")
    return True


def dav_upload_dir():
    code = dav_curl(["-X", "MKCOL", f"{DAV}{DAV_DIR}/"])
    if code in ("201", "405"):  # 405=已存在
        print(f"  ✓ 网盘目录 {DAV_DIR}/ 就绪 (MKCOL {code})")
    else:
        raise RuntimeError(f"MKCOL 失败 HTTP {code}")


def dav_put(local, remote_name):
    code = dav_curl(["-T", local, f"{DAV}{DAV_DIR}/{remote_name}"])
    if code in ("200", "201", "204"):
        print(f"  ✓ 网盘上传 {remote_name} (HTTP {code}, {os.path.getsize(local)}B)")
    else:
        raise RuntimeError(f"网盘上传 {remote_name} 失败 HTTP {code}")


def verify_routes(jtxt):
    j = json.loads(jtxt)
    print("== 验证：检查线路 ==")
    ok = 0
    for name, url in [("raw", RAW_JSON),
                      ("gh-proxy", "https://gh-proxy.com/" + RAW_JSON),
                      ("ghproxy.net", "https://ghproxy.net/" + RAW_JSON),
                      ("jsDelivr", JSDELIVR),
                      ("api.github", f"{API}/releases/latest")]:
        try:
            r = subprocess.run(["curl", "-sS", "-m", "15", "-o", "/tmp/_v.json",
                                "-w", "%{http_code}", url], capture_output=True, text=True)
            code = r.stdout.strip()
            if name == "api.github":
                got = json.load(open("/tmp/_v.json")).get("tag_name", "?")
                expect = f"v{j['versionName']}"
            else:
                got = json.load(open("/tmp/_v.json")).get("versionCode", "?")
                expect = j["versionCode"]
            mark = "✓" if str(got) == str(expect) and code == "200" else "✗"
            ok += mark == "✓"
            print(f"  {mark} {name}: HTTP {code}, {'tag='+str(got) if name=='api.github' else 'vc='+str(got)}")
        except Exception as e:
            print(f"  ✗ {name}: {e}")
    print("== 验证：下载线路（HEAD，跟随重定向） ==")
    for u in j["routes"]:
        try:
            # Authorization 头经 stdin 传入（-H @-），避免 token 出现在进程 argv
            stdin_hdr = "Accept: application/octet-stream\n"
            if "/releases/assets/" in u:
                stdin_hdr += f"Authorization: Bearer {TOK}\n"
            r = subprocess.run(["curl", "-sSI", "-L", "-m", "25", "-o", "/dev/null",
                                "-w", "%{http_code}|%{size_download}|%{url_effective}",
                                "-H", "@-", u],
                               input=stdin_hdr, capture_output=True, text=True)
            print(f"  {'✓' if r.stdout.startswith(('200','302','206')) else '✗'} {u[:80]} => {r.stdout[:120]}")
        except Exception as e:
            print(f"  ✗ {u[:80]}: {e}")
    print(f"检查线路通过 {ok}/5")


def do_publish(apk, version, vc, notes_file):
    apk_name = f"wbl-v{version}-vc{vc}.apk"
    desc = open(notes_file, encoding="utf-8").read().strip()
    size = os.path.getsize(apk)
    sha = sha256_file(apk)
    print(f"== 发布 v{version} (vc{vc}) ==\nAPK: {apk}\nname: {apk_name}\nsize: {size}\nsha256: {sha}")

    # 1. 创建 Release
    st, body = gh(f"{API}/releases", method="POST",
                  data=json.dumps({
                      "tag_name": f"v{version}", "name": f"忘不了 v{version}",
                      "body": desc, "draft": False, "prerelease": False,
                      "target_commitish": "main",
                  }).encode(), ctype="application/json")
    if st == 422 and "already_exists" in body.decode(errors="ignore"):
        st, body = gh(f"{API}/releases/tags/v{version}")
        print(f"  Release v{version} 已存在，复用 (id={json.loads(body).get('id')})")
    elif st not in (200, 201):
        sys.exit(f"创建 Release 失败 HTTP {st}: {body[:300]!r}")
    else:
        print("  ✓ Release 已创建")
    rel = json.loads(body)
    rel_id = rel["id"]

    # 2. 上传资产（同名旧资产先删）
    for a in rel.get("assets", []):
        if a["name"] == apk_name:
            st, _ = gh(f"{API}/releases/assets/{a['id']}", method="DELETE")
            print(f"  旧资产删除 HTTP {st}")
    with open(apk, "rb") as f:
        data = f.read()
    st, body = gh(f"{UPLOADS}/{rel_id}/assets?name={apk_name}", method="POST",
                  data=data, ctype="application/vnd.android.package-archive", timeout=900)
    if st not in (200, 201):
        sys.exit(f"上传资产失败 HTTP {st}: {body[:300]!r}")
    asset = json.loads(body)
    print(f"  ✓ 资产已上传 id={asset['id']} digest={asset.get('digest')}")

    # 3. 生成并提交 wbl-update.json
    j = build_json(apk_name, version, vc, sha, size, desc, asset["id"])
    if j["size"] != asset.get("size", size):
        print(f"  [WARN] 资产大小不一致 local={j['size']} remote={asset.get('size')}")
    jtxt = json.dumps(j, ensure_ascii=False, indent=2)
    open("wbl-update.json", "w", encoding="utf-8").write(jtxt)
    if not commit_json(jtxt):
        sys.exit("提交 wbl-update.json 失败")

    # 4. 123网盘 WebDAV 同步
    print("== 123网盘 WebDAV 同步 ==")
    dav_upload_dir()
    dav_put(apk, apk_name)
    open("/tmp/_wj.json", "w", encoding="utf-8").write(jtxt)
    dav_put("/tmp/_wj.json", "wbl-update.json")
    sums = f"{sha}  {apk_name}\n"
    open("/tmp/_sums.txt", "w", encoding="utf-8").write(sums)
    dav_put("/tmp/_sums.txt", "SHA256SUMS.txt")

    # 5. 验证
    verify_routes(jtxt)
    print("== 发布完成 ==")


def do_commit_json(path):
    jtxt = open(path, encoding="utf-8").read()
    json.loads(jtxt)  # 校验合法
    if not commit_json(jtxt):
        sys.exit("提交失败")
    print("== 123网盘同步 json ==")
    dav_upload_dir()
    open("/tmp/_wj.json", "w", encoding="utf-8").write(jtxt)
    dav_put("/tmp/_wj.json", "wbl-update.json")
    verify_routes(jtxt)


def do_delete_tag(tag):
    st, body = gh(f"{API}/releases/tags/{tag}")
    if st == 200:
        rid = json.loads(body)["id"]
        st2, _ = gh(f"{API}/releases/{rid}", method="DELETE")
        print(f"删除 Release {tag}: HTTP {st2}")
    else:
        print(f"Release {tag} 不存在 (HTTP {st})")
    time.sleep(2)
    st3, _ = gh(f"{API}/git/refs/tags/{tag}", method="DELETE")
    print(f"删除 tag {tag}: HTTP {st3}")


def do_dav_put(local, remote=None):
    dav_upload_dir()
    dav_put(local, remote or os.path.basename(local))


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("apk", nargs="?")
    ap.add_argument("-v", "--version")
    ap.add_argument("-c", "--vc", type=int)
    ap.add_argument("-n", "--notes")
    ap.add_argument("--commit-json")
    ap.add_argument("--delete-tag")
    ap.add_argument("--dav-put", nargs="+")
    a = ap.parse_args()
    if a.delete_tag:
        do_delete_tag(a.delete_tag)
    elif a.commit_json:
        do_commit_json(a.commit_json)
    elif a.dav_put:
        do_dav_put(*a.dav_put)
    elif a.apk and a.version and a.vc and a.notes:
        do_publish(a.apk, a.version, a.vc, a.notes)
    else:
        ap.print_help()
        sys.exit(1)
