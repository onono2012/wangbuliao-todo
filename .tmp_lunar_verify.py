# -*- coding: utf-8 -*-
"""农历算法验证(将原样移植Kotlin)：lunarInfo表 + sTermInfo节气表
真值: cnlunar / lunarcalendar / zhdate 交叉验证"""
import datetime

lunarInfo = [
0x04bd8,0x04ae0,0x0a570,0x054d5,0x0d260,0x0d950,0x16554,0x056a0,0x09ad0,0x055d2,
0x04ae0,0x0a5b6,0x0a4d0,0x0d250,0x1d255,0x0b540,0x0d6a0,0x0ada2,0x095b0,0x14977,
0x04970,0x0a4b0,0x0b4b5,0x06a50,0x06d40,0x1ab54,0x02b60,0x09570,0x052f2,0x04970,
0x06566,0x0d4a0,0x0ea50,0x06e95,0x05ad0,0x02b60,0x186e3,0x092e0,0x1c8d7,0x0c950,
0x0d4a0,0x1d8a6,0x0b550,0x056a0,0x1a5b4,0x025d0,0x092d0,0x0d2b2,0x0a950,0x0b557,
0x06ca0,0x0b550,0x15355,0x04da0,0x0a5b0,0x14573,0x052b0,0x0a9a8,0x0e950,0x06aa0,
0x0aea6,0x0ab50,0x04b60,0x0aae4,0x0a570,0x05260,0x0f263,0x0d950,0x05b57,0x056a0,
0x096d0,0x04dd5,0x04ad0,0x0a4d0,0x0d4d4,0x0d250,0x0d558,0x0b540,0x0b6a0,0x195a6,
0x095b0,0x049b0,0x0a974,0x0a4b0,0x0b27a,0x06a50,0x06d40,0x0af46,0x0ab60,0x09570,
0x04af5,0x04970,0x064b0,0x074a3,0x0ea50,0x06b58,0x055c0,0x0ab60,0x096d5,0x092e0,
0x0c960,0x0d954,0x0d4a0,0x0da50,0x07552,0x056a0,0x0abb7,0x025d0,0x092d0,0x0cab5,
0x0a950,0x0b4a0,0x0baa4,0x0ad50,0x055d9,0x04ba0,0x0a5b0,0x15176,0x052b0,0x0a930,
0x07954,0x06aa0,0x0ad50,0x05b52,0x04b60,0x0a6e6,0x0a4e0,0x0d260,0x0ea65,0x0d530,
0x05aa0,0x076a3,0x096d0,0x04afb,0x04ad0,0x0a4d0,0x1d0b6,0x0d250,0x0d520,0x0dd45,
0x0b5a0,0x056d0,0x055b2,0x049b0,0x0a577,0x0a4b0,0x0aa50,0x1b255,0x06d20,0x0ada0,
0x14b63,0x09370,0x049f8,0x04970,0x064b0,0x168a6,0x0ea50,0x06b20,0x1a6c4,0x0aae0,
0x0a2e0,0x0d2e3,0x0c960,0x0d557,0x0d4a0,0x0da50,0x05d55,0x056a0,0x0a6d0,0x055d4,
0x052d0,0x0a9b8,0x0a950,0x0b4a0,0x0b6a6,0x0ad50,0x055a0,0x0aba4,0x0a5b0,0x052b0,
0x0b273,0x06930,0x07337,0x06aa0,0x0ad50,0x14b55,0x04b60,0x0a570,0x054e4,0x0d160,
0x0e968,0x0d520,0x0daa0,0x16aa6,0x056d0,0x04ae0,0x0a9d4,0x0a2d0,0x0d150,0x0f252,
0x0d520]

sTermInfo = [0,21208,42467,63836,85337,107014,128867,150921,173149,195551,
             218072,240693,263343,285989,308563,331033,353350,375494,397447,
             419210,440795,462224,483532,504758]

def leapMonth(y): return lunarInfo[y-1900] & 0xf
def leapDays(y):
    if leapMonth(y): return 30 if (lunarInfo[y-1900] & 0x10000) else 29
    return 0
def monthDays(y, m): return 30 if (lunarInfo[y-1900] & (0x10000 >> m)) else 29
def lYearDays(y):
    s = 348; i = 0x8000
    while i > 0x8:
        s += 1 if (lunarInfo[y-1900] & i) else 0
        i >>= 1
    return s + leapDays(y)

def solar2lunar(y, m, d):
    """完全按 calendar.js 标准算法"""
    objD = datetime.date(y, m, d)
    baseD = datetime.date(1900, 1, 31)
    offset = (objD - baseD).days
    temp = 0; i = 1900
    while i < 2101 and offset > 0:
        temp = lYearDays(i); offset -= temp; i += 1
    if offset < 0:
        offset += temp; i -= 1
    year = i
    leap = leapMonth(i)
    isLeap = False
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

def sTerm(y, n):
    base = datetime.datetime(1900, 1, 6, 2, 5, 0)
    ms = 31556925974.7 * (y - 1900) + sTermInfo[n-1] * 60000.0
    t = base + datetime.timedelta(milliseconds=ms) + datetime.timedelta(hours=8)
    return t.date()

# ── 验证1: 农历 vs cnlunar ──
import cnlunar
errs = 0; checked = 0
def check(y, m, d):
    global errs, checked
    checked += 1
    a = cnlunar.Lunar(datetime.datetime(y, m, d, 12), godType='8char')
    truth = (a.lunarYear, a.lunarMonth, a.lunarDay, a.isLunarLeapMonth)
    mine = solar2lunar(y, m, d)
    if mine != truth:
        errs += 1
        if errs <= 15:
            print(f"LUNAR {y}-{m:02d}-{d:02d}: mine={mine} truth={truth}")
for y in range(1970, 2050):
    for m in range(1, 13):
        mdays = [31,29 if (y%4==0 and (y%100!=0 or y%400==0)) else 28,31,30,31,30,31,31,30,31,30,31][m-1]
        for d in range(1, mdays+1):
            check(y, m, d)
for y in list(range(1901,1970))+list(range(2050,2100)):
    for m in range(1,13):
        for d in (1,5,10,15,20,26):
            check(y,m,d)
print(f"[农历] checked={checked} errs={errs}")

# (旧验证2已替换,见文件尾)
# ── 验证2(修正): 节气 vs cnlunar thisYearSolarTermsDic ──
names = ['小寒','大寒','立春','雨水','惊蛰','春分','清明','谷雨','立夏','小满','芒种','夏至',
         '小暑','大暑','立秋','处暑','白露','秋分','寒露','霜降','立冬','小雪','大雪','冬至']
jerrs = 0; jchecked = 0
for y in range(1901, 2100):
    a = cnlunar.Lunar(datetime.datetime(y, 6, 1, 12), godType='8char')
    dic = a.thisYearSolarTermsDic
    for i in range(24):
        jchecked += 1
        truth = dic[names[i]].date() if hasattr(dic[names[i]], 'date') else dic[names[i]]
        mine = sTerm(y, i+1)
        if truth != mine:
            jerrs += 1
            if jerrs <= 15:
                print(f"TERM {y} {names[i]}: mine={mine} truth={truth}")
print(f"[节气] checked={jchecked} errs={jerrs}")
