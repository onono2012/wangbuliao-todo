# -*- coding: utf-8 -*-
"""终极验证: 修正表(zhdate CHINESEYEARCODE == cnlunar重建) + 标准算法
1) solar2lunar 全量 1900-01-31..2099-12-31 vs zhdate, 抽样 vs cnlunar
2) sTerm 公式 1901-2099 vs cnlunar thisYearSolarTermsDic, 抽样 vs ephem 天文计算"""
import datetime, sys
from zhdate import ZhDate, CHINESEYEARCODE, CHINESENEWYEAR
import cnlunar

LUNARINFO = CHINESEYEARCODE  # 201 values, 1900..2100, 17-bit同构编码

def leapMonth(y): return LUNARINFO[y-1900] & 0xf
def leapDays(y):
    if leapMonth(y): return 30 if (LUNARINFO[y-1900] & 0x10000) else 29
    return 0
def monthDays(y, m): return 30 if (LUNARINFO[y-1900] & (0x10000 >> m)) else 29
def lYearDays(y):
    s = 348; i = 0x8000
    while i > 0x8:
        s += 1 if (LUNARINFO[y-1900] & i) else 0
        i >>= 1
    return s + leapDays(y)

def solar2lunar(y, m, d):
    objD = datetime.date(y, m, d)
    offset = (objD - datetime.date(1900, 1, 31)).days
    temp = 0; i = 1900
    while i < 2101 and offset > 0:
        temp = lYearDays(i); offset -= temp; i += 1
    if offset < 0:
        offset += temp; i -= 1
    year = i
    leap = leapMonth(i); isLeap = False
    i = 1
    while i < 13 and offset > 0:
        if leap > 0 and i == leap + 1 and not isLeap:
            i -= 1; isLeap = True; temp = leapDays(year)
        else:
            temp = monthDays(year, i)
        if isLeap and i == leap + 1:
            isLeap = False
        offset -= temp
        i += 1
    if offset == 0 and leap > 0 and i == leap + 1:
        if isLeap: isLeap = False
        else: isLeap = True; i -= 1
    if offset < 0:
        offset += temp; i -= 1
    return year, i, offset + 1, isLeap

sTermInfo = [0,21208,42467,63836,85337,107014,128867,150921,173149,195551,
             218072,240693,263343,285989,308563,331033,353350,375494,397447,
             419210,440795,462224,483532,504758]
def sTerm(y, n):
    base = datetime.datetime(1900, 1, 6, 2, 5, 0)
    ms = 31556925974.7 * (y - 1900) + sTermInfo[n-1] * 60000.0
    t = base + datetime.timedelta(milliseconds=ms) + datetime.timedelta(hours=8)
    return t.date()

# ── 1a. 春节日期自检(表内部一致性) ──
errs = 0
for idx, s in enumerate(CHINESENEWYEAR):
    y = 1900 + idx
    ny = datetime.date(int(s[:4]), int(s[4:6]), int(s[6:]))
    mine = solar2lunar(ny.year, ny.month, ny.day)
    if mine != (y, 1, 1, False):
        errs += 1
        if errs <= 5: print(f"NYE MISMATCH {ny}: mine={mine} want=({y},1,1,False)")
print(f"[春节自检] errs={errs}/201")

# ── 1b. 全量 vs zhdate ──
errs = 0; checked = 0
d = datetime.date(1900, 1, 31); end = datetime.date(2099, 12, 31)
step = datetime.timedelta(days=1)
skips = 0
while d <= end:
    checked += 1
    mine = solar2lunar(d.year, d.month, d.day)
    try:
        z = ZhDate.from_datetime(datetime.datetime(d.year, d.month, d.day, 12))
        truth = (z.lunar_year, z.lunar_month, z.lunar_day, z.leap_month)
    except Exception:
        skips += 1
        a = cnlunar.Lunar(datetime.datetime(d.year, d.month, d.day, 12), godType='8char')
        truth = (a.lunarYear, a.lunarMonth, a.lunarDay, a.isLunarLeapMonth)
    if mine != truth:
        errs += 1
        if errs <= 10:
            print(f"LUNAR {d}: mine={mine} truth={truth}")
    d += step
print(f"zhdate异常日(改用cnlunar校验): {skips}")
print(f"[全量vs zhdate] checked={checked} errs={errs}")

# ── 1c. 抽样 vs cnlunar (每7天一个) ──
errs = 0; checked = 0
d = datetime.date(1901, 1, 1); end = datetime.date(2099, 12, 31)
while d <= end:
    checked += 1
    a = cnlunar.Lunar(datetime.datetime(d.year, d.month, d.day, 12), godType='8char')
    mine = solar2lunar(d.year, d.month, d.day)
    if mine != (a.lunarYear, a.lunarMonth, a.lunarDay, a.isLunarLeapMonth):
        errs += 1
        if errs <= 10:
            print(f"CN {d}: mine={mine} cnlunar={(a.lunarYear,a.lunarMonth,a.lunarDay,a.isLunarLeapMonth)}")
    d += datetime.timedelta(days=7)
print(f"[抽样vs cnlunar] checked={checked} errs={errs}")

# ── 2a. 节气 vs cnlunar ──
names = ['小寒','大寒','立春','雨水','惊蛰','春分','清明','谷雨','立夏','小满','芒种','夏至',
         '小暑','大暑','立秋','处暑','白露','秋分','寒露','霜降','立冬','小雪','大雪','冬至']
jerrs = 0; jchecked = 0; bydecade = {}
for y in range(1901, 2100):
    a = cnlunar.Lunar(datetime.datetime(y, 6, 1, 12), godType='8char')
    dic = a.thisYearSolarTermsDic
    for i in range(24):
        jchecked += 1
        truth = dic[names[i]]
        mine = sTerm(y, i+1)
        if (mine.month, mine.day) != tuple(truth):
            jerrs += 1
            dec = y // 10 * 10
            bydecade.setdefault(dec, []).append((y, names[i], f"{mine.month}-{mine.day}", f"{truth[0]}-{truth[1]}"))
print(f"[节气vs cnlunar] checked={jchecked} errs={jerrs}")
for dec in sorted(bydecade): print(f"  {dec}s: {len(bydecade[dec])} errs, e.g. {bydecade[dec][:2]}")
