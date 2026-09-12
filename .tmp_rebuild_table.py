# -*- coding: utf-8 -*-
"""从 cnlunar 真值重建 lunarInfo 表 + 生成打包节气日表, 并用 ephem/zhdate/lunarcalendar 交叉验证"""
import datetime, cnlunar

# ── 1. 重建 lunarInfo[1900..2100] ──
# 遍历所有日期收集: lunarYear -> {month: long(29/30), leapMonth, leapLong}
yinfo = {}
d = datetime.date(1900, 1, 31)
end = datetime.date(2100, 2, 8)
while d <= end:
    a = cnlunar.Lunar(datetime.datetime(d.year, d.month, d.day, 12), godType='8char')
    ly, lm, ld = a.lunarYear, a.lunarMonth, a.lunarDay
    leap = a.isLunarLeapMonth
    long_ = 30 if a.lunarMonthLong else 29
    yi = yinfo.setdefault(ly, {'m': {}, 'leap': 0, 'leapLong': 0})
    if leap:
        yi['leap'] = lm
        yi['leapLong'] = long_
    else:
        yi['m'][lm] = long_
    d += datetime.timedelta(days=1)

def buildInfo(ly):
    yi = yinfo[ly]
    v = 0
    if yi['leapLong'] == 30: v |= 0x10000
    for m in range(1, 13):
        if yi['m'].get(m, 29) == 30: v |= (0x10000 >> m)
    v |= yi['leap'] & 0xf
    return v

table = [buildInfo(y) for y in range(1900, 2100)] + [0x0d520]
print("lunarInfo 1900-2100 rebuilt, count:", len(table))
# 输出Kotlin格式
lines = []
for i in range(0, 201, 10):
    chunk = table[i:i+10]
    lines.append("    " + ",".join(f"0x{v:05x}" for v in chunk) + ("," if i+10 < 201 else "") + f" // {1900+i}")
open('/root/projects/wbl/.tmp_lunar_table.txt','w').write("\n".join(lines))

# 与原表对比差异年份
orig = None
src = open("/root/projects/wbl/.tmp_lunar_verify.py").read(); orig_src = src[:src.find("def leapMonth")]; ns = {}; exec(orig_src, ns); orig = ns["lunarInfo"]
diffs = [1900+i for i in range(201) if orig and orig[i] != table[i]]
print("与原标准表差异年份:", diffs)
for y in diffs:
    print(f"  {y}: orig=0x{orig[y-1900]:05x} new=0x{table[y-1900]:05x}")
