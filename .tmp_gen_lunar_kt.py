# -*- coding: utf-8 -*-
lunarinfo = open('.tmp_lunarinfo.kt.txt').read().rstrip() + '\n'
solarterm = open('.tmp_solarterm.kt.txt').read().rstrip() + '\n'
head = open('.tmp_lunar_head.txt').read()
mid1 = '    )\n\n    private val TERM_ROWS = arrayOf(\n'
tail = open('.tmp_lunar_tail.txt').read()
out = head + lunarinfo + mid1 + solarterm + tail
path = 'app/src/main/java/com/wangbuliao/todo/util/LunarUtil.kt'
open(path, 'w').write(out)
print("written", path, len(out), "bytes")
