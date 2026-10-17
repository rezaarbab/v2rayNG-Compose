# v2rayNG-Compose

بازسازی UI/UX سبک v2rayNG با **Jetpack Compose** و کاراکترهای استیکری ارگانیکِ انیمیشنی (Blob + چشم‌های پلک‌زن، نفس‌کشیدن و پرش) که با `Canvas` در Compose رندر می‌شوند — هیچ فایل GIF یا تصویری در کار نیست، همه‌چیز وکتور-مثلثاتیو و ۶۰fps است.

## ویژگی‌ها
- دکمه اتصال = استیکر زنده: قطع (آرام) / در حال اتصال (تکان‌تکان) / متصل (پرش و شادی) / خراب (خواب)
- لیست سرورها با آواتار استیکری برای هر پروفایل + چیپ تاخیر (ping)
- مسیریابی با سوییچ‌های استیکری
- گزارش زنده رنگی
- تم تیره، پشتیبانی کامل RTL فارسی

## ساخت
```bash
gradle :app:assembleDebug
```
یا فقط Actions را اجرا کنید: `.github/workflows/build.yml` — هر push یا اجرای دستی، APK دیباگ به‌صورت Artifact تحویل می‌دهد.

[![Build APK](https://github.com/rezaarbab/v2rayNG-Compose/actions/workflows/build.yml/badge.svg)](https://github.com/rezaarbab/v2rayNG-Compose/actions/workflows/build.yml)

## نقشه راه (ادامه کار)
- اتصال `VpnService` واقعی و هسته Xray (JNI) — این نسخه فقط لایه UI است
- اشتراک‌ها/Subscription + QR
- تست سرعت واقعی
