import pathlib, secrets, subprocess, shutil, sys

KEYTOOL = shutil.which('keytool') or '/usr/lib/jvm/java-17-openjdk/bin/keytool'
keys = pathlib.Path('/root/keys'); keys.mkdir(parents=True, exist_ok=True)
ks = keys / 'wbl.keystore'
pw = secrets.token_hex(16)   # 仅写入文件，绝不打印

if ks.exists():
    ks.unlink()

r = subprocess.run([KEYTOOL, '-genkeypair', '-keystore', str(ks), '-alias', 'wbl',
                    '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000',
                    '-storepass', pw, '-keypass', pw,
                    '-dname', 'CN=WangBuLiao, OU=Mobile, O=WBL, C=CN'],
                   capture_output=True, text=True)
print('GEN_RC=', r.returncode)
if r.returncode != 0:
    print('GEN_ERR=', r.stderr[-400:]); sys.exit(1)

v = subprocess.run([KEYTOOL, '-list', '-keystore', str(ks), '-storepass', pw],
                   capture_output=True, text=True)
print('LIST_RC=', v.returncode)
for line in v.stdout.splitlines():
    if 'wbl' in line.lower():
        print('ENTRY:', line.strip()[:80]); break
if v.returncode != 0:
    print('LIST_ERR=', v.stderr[-300:]); sys.exit(1)

props = f"storeFile={ks}\nstorePassword={pw}\nkeyAlias=wbl\nkeyPassword={pw}\n"
p1 = pathlib.Path('/sdcard/projects/wangbuliao/keystore.properties')
p1.write_text(props)
shutil.copy2(ks, '/sdcard/projects/wangbuliao/keystore/wbl.keystore')

print('PROPS_MATCH=', p1.read_text() == props)
print('KS_SIZE=', ks.stat().st_size)
print('BACKUP_SIZE=', pathlib.Path('/sdcard/projects/wangbuliao/keystore/wbl.keystore').stat().st_size)
print('ALL_OK')
