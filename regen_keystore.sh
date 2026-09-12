#!/usr/bin/env bash
# 原子化重建签名密钥: 新keystore + 新keystore.properties 同脚本生成, 密码不回显
set -euo pipefail
cd /sdcard/projects/wangbuliao

PASS=$(python3 -c "import secrets;print(secrets.token_hex(18))")

rm -rf keystore keystore.properties
mkdir -p keystore

keytool -genkeypair -v \
  -keystore keystore/wbl.keystore \
  -alias wbl \
  -keyalg RSA -keysize 2048 \
  -validity 10950 \
  -storepass "$PASS" -keypass "$PASS" \
  -dname "CN=wangbuliao-todo, OU=dev, O=wbl, L=Beijing, ST=Beijing, C=CN" \
  > /dev/null 2>&1

umask 077
cat > keystore.properties <<EOF
storePassword=$PASS
keyAlias=wbl
keyPassword=$PASS
EOF
unset PASS

echo "--- verify with properties password ---"
P=$(grep '^storePassword=' keystore.properties | cut -d= -f2-)
keytool -list -keystore keystore/wbl.keystore -storepass "$P" 2>/dev/null | grep -E 'wbl|Entry| entries' | sed 's/,.*//' 
echo "KEYTOOL_LIST_EXIT=$?"
ls -la keystore/ keystore.properties
echo REGEN_DONE
