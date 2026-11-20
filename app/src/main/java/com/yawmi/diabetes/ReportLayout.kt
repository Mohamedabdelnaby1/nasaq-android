package com.yawmi.diabetes

internal const val reportStyles = """
@page{size:A4;margin:13mm 13mm 15mm}
*{box-sizing:border-box}
html{color:#162f28;background:#fff}
body{margin:0;font-family:Tajawal,Arial,sans-serif;font-size:10.5pt;line-height:1.45}
h1,h2,h3,p,figure{margin:0}
h1{font-size:35pt;line-height:1.13;font-weight:700}
h2{font-size:18pt;line-height:1.24}
h3{font-size:12pt;line-height:1.25}
bdi{unicode-bidi:isolate}
.hero{position:relative;isolation:isolate;border-radius:22px 9px 22px 9px;background:#10283f;color:#f4f8fc;padding:18px 23px 15px;overflow:hidden;break-inside:avoid}
.hero-art{position:absolute;width:48%;height:100%;inset-block-start:0;inset-inline-end:0;z-index:-1;opacity:.38}
.brand{display:flex;align-items:center;gap:8px;font-size:13pt;font-weight:700;color:#dce7ff;margin-bottom:7px}
.logo{width:30px;height:30px;display:block}
.hero-meta{display:flex;gap:9px 25px;flex-wrap:wrap;align-items:start;border-top:1px solid #7691b8;padding-top:8px;margin-top:11px}
.hero-meta>div{flex:1 1 42%;min-width:0;overflow-wrap:anywhere}
.hero-meta span{display:block;color:#dce7ff;font-size:9pt}
.hero-meta strong{font-size:11pt;font-weight:700}
.generated{font-size:8.5pt;color:#dce7ff;margin-top:7px}
.section-title{display:flex;align-items:center;gap:8px;margin:15px 0 7px;break-after:avoid;page-break-after:avoid}
.mark{width:27px;height:27px;flex:none;display:grid;place-items:center;border-radius:8px 4px 8px 4px;background:#dbeab7;color:#355021}
.mark svg{width:16px;height:16px;display:block}
.mark.violet{background:#e7dcfc;color:#3b2770}
.mark.peach{background:#ffdecb;color:#572b1e}
.tile-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:9px;break-inside:avoid}
.tile{min-width:0;padding:10px 12px;border-radius:16px 6px 16px 6px;overflow-wrap:anywhere;border:1px solid transparent}
.tile.violet{background:#eee6ff;color:#342365;border-color:#dfd1f9}
.tile.sky{background:#d9f1fc;color:#103f54;border-color:#c6e7f6}
.tile.peach{background:#ffe4d5;color:#592d21;border-color:#f9d6c3}
.tile-label{font-weight:700;font-size:10pt}
.tile-value{font-size:24pt;font-weight:700;line-height:1.05;margin:5px 0 3px}
.tile-value span{font-size:10pt;white-space:nowrap;font-weight:400}
.tile-detail{font-size:8.75pt}
.summary-strip{display:flex;gap:8px 18px;flex-wrap:wrap;margin:8px 0 3px;padding:6px 10px;background:#f2f6e6;border-radius:8px;break-inside:avoid}
.summary-strip b{font-size:10.5pt;margin-inline-start:5px}
.hints{font-size:8.3pt;color:#4c5d55;margin:5px 0;overflow-wrap:anywhere}
.hints p+p{margin-top:3px}
.chart-box{padding:7px 10px 6px;border:1px solid #ddd5f2;border-radius:12px 5px 12px 5px;background:#fbf8ff;break-inside:avoid}
.chart{display:block;width:100%;height:auto}
.chart text{font-family:Tajawal,Arial,sans-serif}
.legend{display:flex;gap:6px 15px;flex-wrap:wrap;align-items:center;color:#4a3d64;font-size:8.7pt}
.legend span{white-space:nowrap}
.legend i{display:inline-block;width:9px;height:9px;background:#704bb2;border-radius:50%;margin-inline-end:4px}
.legend i.outside{background:#fff;border:2px solid #ae4639}
.legend i.range{background:#e6f0ce;border-radius:2px}
.subheading{margin-top:8px;break-after:avoid}
table{width:100%;border-collapse:collapse;table-layout:fixed;margin-top:6px;font-size:10pt}
thead{display:table-header-group}
thead tr{break-inside:avoid;break-after:avoid}
th{text-align:start;background:#eaf2e8;color:#1d4537;font-weight:700;border-block-end:2px solid #78a687}
th,td{padding:6px 9px}
td{vertical-align:top;border-block-end:1px solid #e5eae4;overflow-wrap:anywhere}
tbody tr:nth-child(even){background:#f8faf6}
tr{break-inside:avoid;page-break-inside:avoid}
.when,.subtext{font-size:8.75pt;color:#4b6054}
.note{font-size:9.3pt;white-space:pre-wrap}
.chip{display:inline-block;border-radius:5px;padding:2px 6px;background:#eaf2e6;color:#29483e;font-size:9pt;line-height:1.3}
.chip.glucose{background:#eee6ff;color:#3d2b6b}
.chip.water{background:#dcf2fb;color:#15455b}
.chip.dose{background:#ffeadc;color:#623426}
.chip.meal{background:#fff2d1;color:#5b4516}
.chip.symptom{background:#f9e5ef;color:#63334f}
.plain-box,.user-note{overflow-wrap:anywhere;background:#f6f7ef;border:1px solid #e3eadd;padding:9px 12px;border-radius:10px 5px 10px 5px}
.user-note{white-space:pre-wrap}
.report-footer{margin-top:15px;border-top:2px solid #c9d8bf;padding-top:7px;color:#4b6054;font-size:8pt;break-inside:avoid}
.report-footer strong{color:#285142}
.report-footer p+p{margin-top:3px}
@media screen{body{padding:20px;max-width:850px;margin:auto}}
@media print{
  html,body{-webkit-print-color-adjust:exact;print-color-adjust:exact}
  .visit-overview{break-inside:avoid}
  .report-detail{break-before:page;page-break-before:always}
}
"""
