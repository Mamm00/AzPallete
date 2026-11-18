package com.alazhartex.shadecart.domain.export

import com.alazhartex.shadecart.domain.model.RollStockStatus
import com.alazhartex.shadecart.domain.model.ShadeCartModel
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale

/**
 * Pure Kotlin single-file offline HTML lookbook exporter (B2 Advanced).
 *
 * Embeds pinked-scissors swatch graphics directly as base64 SVG data URIs with an inline
 * English / Arabic RTL switcher and zero external network dependencies.
 */
object LookbookHtmlExporter {

    fun generateHtml(
        carts: List<ShadeCartModel>,
        companyName: String = "Al AzharTex",
        calibrated: Boolean = false,
    ): String {
        val cardsHtml = carts.joinToString("\n") { cart ->
            val swatchesHtml = cart.swatches.sortedBy { it.positionOrder }.joinToString("\n") { sw ->
                val svg = buildPinkedSwatchSvg(sw.colorHex)
                val b64 = Base64.getEncoder().encodeToString(svg.toByteArray(StandardCharsets.UTF_8))
                val rollInfo = sw.linkedRoll?.let { roll ->
                    val statusBadge = when (roll.status) {
                        RollStockStatus.IN_STOCK -> "<span class='badge ok'>${String.format(Locale.US, "%.0f", roll.availableMeters)}m · ${escapeHtml(roll.warehouseShed)}</span>"
                        RollStockStatus.LOW_STOCK -> "<span class='badge warn'>⚠ Low ${String.format(Locale.US, "%.0f", roll.availableMeters)}m</span>"
                        RollStockStatus.RESERVED -> "<span class='badge res'>Reserved</span>"
                        RollStockStatus.OUT_OF_STOCK -> "<span class='badge out'>Out of stock</span>"
                    }
                    statusBadge
                } ?: "<span class='badge muted'>Sample Only</span>"

                """
                <div class="swatch-card">
                  <img class="swatch-img" src="data:image/svg+xml;base64,$b64" alt="#${escapeHtml(sw.swatchNumber)}"/>
                  <div class="swatch-meta">
                    <div class="swatch-num">#${escapeHtml(sw.swatchNumber)} · ${escapeHtml(sw.colorName)}</div>
                    <div class="swatch-hex" dir="ltr">${escapeHtml(sw.colorHex.uppercase(Locale.US))}</div>
                    $rollInfo
                  </div>
                </div>
                """.trimIndent()
            }

            """
            <section class="cart-section">
              <header class="cart-header">
                <div>
                  <h2>${escapeHtml(cart.fabricName)}</h2>
                  <p class="specs" dir="ltr">${escapeHtml(cart.fabricCode)} · ${cart.weightGsm} GSM (g/m²) · ${cart.widthCm} cm</p>
                </div>
                ${if (calibrated) "<span class='cal-chip'>Calibrated ✓</span>" else ""}
              </header>
              <div class="swatch-grid">
                $swatchesHtml
              </div>
            </section>
            """.trimIndent()
        }

        return """
        <!DOCTYPE html>
        <html lang="en" dir="ltr">
        <head>
          <meta charset="UTF-8"/>
          <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
          <title>${escapeHtml(companyName)} — Digital Shade Cart Lookbook</title>
          <style>
            :root { --navy:#102A71; --crimson:#D90A0A; --bg:#FAF9F5; --card:#FFFFFF; --ink:#171B26; }
            body { margin:0; font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif; background:var(--bg); color:var(--ink); padding:24px; }
            .topbar { max-width:960px; margin:0 auto 20px; display:flex; justify-content:space-between; align-items:center; gap:12px; border-bottom:2px solid var(--navy); padding-bottom:12px; }
            .brand { font-weight:800; font-size:20px; color:var(--navy); }
            .lang-btn { background:var(--navy); color:#fff; border:none; border-radius:999px; padding:8px 14px; cursor:pointer; font-weight:600; }
            .cart-section { max-width:960px; margin:0 auto 28px; background:var(--card); border-radius:16px; padding:20px; box-shadow:0 2px 10px rgba(16,42,113,0.08); }
            .cart-header { display:flex; justify-content:space-between; align-items:center; border-bottom:1px solid #ECEAE2; padding-bottom:12px; margin-bottom:16px; }
            .cart-header h2 { margin:0; color:var(--navy); }
            .specs { margin:4px 0 0; color:#475569; font-size:14px; }
            .cal-chip { background:#DCFCE7; color:#166534; padding:4px 10px; border-radius:999px; font-size:12px; font-weight:700; }
            .swatch-grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(220px,1fr)); gap:16px; }
            .swatch-card { border:1px solid #ECEAE2; border-radius:12px; padding:12px; background:#fff; }
            .swatch-img { width:100%; height:auto; display:block; }
            .swatch-meta { margin-top:8px; }
            .swatch-num { font-weight:700; font-size:14px; }
            .swatch-hex { font-family:monospace; color:#475569; font-size:12px; margin:2px 0 6px; }
            .badge { display:inline-block; padding:2px 8px; border-radius:999px; font-size:11px; font-weight:600; }
            .ok { background:#DCFCE7; color:#166534; }
            .warn { background:#FEF3C7; color:#92400E; }
            .res { background:#DBEAFE; color:#1E40AF; }
            .out { background:#FEE2E2; color:#991B1B; }
            .muted { background:#F1F5F9; color:#475569; }
            footer { max-width:960px; margin:12px auto 0; text-align:center; font-size:12px; color:#64748B; }
          </style>
        </head>
        <body>
          <div class="topbar">
            <div>
              <div class="brand" id="title-text">${escapeHtml(companyName)} · Digital Shade Cart Lookbook</div>
              <small id="sub-text">Offline Textile Catalog · Metric GSM / cm / m</small>
            </div>
            <button class="lang-btn" onclick="toggleLang()" id="lang-btn">العربية</button>
          </div>
          $cardsHtml
          <footer id="footer-text">Generated offline by AzPallete · Al AzharTex</footer>
          <script>
            var isAr = false;
            function toggleLang() {
              isAr = !isAr;
              document.documentElement.dir = isAr ? 'rtl' : 'ltr';
              document.documentElement.lang = isAr ? 'ar' : 'en';
              document.getElementById('lang-btn').innerText = isAr ? 'English' : 'العربية';
              document.getElementById('title-text').innerText = isAr ? 'الأزهر تكس · كتالوج كروت الألوان الرقمي' : '${escapeJs(companyName)} · Digital Shade Cart Lookbook';
              document.getElementById('sub-text').innerText = isAr ? 'كتالوج أقمشة يعمل بدون إنترنت · جم/م² · سم · متر' : 'Offline Textile Catalog · Metric GSM / cm / m';
              document.getElementById('footer-text').innerText = isAr ? 'تم الإنشاء بواسطة تطبيق أز باليت · الأزهر تكس' : 'Generated offline by AzPallete · Al AzharTex';
            }
          </script>
        </body>
        </html>
        """.trimIndent()
    }

    private fun buildPinkedSwatchSvg(colorHex: String): String {
        val pts = ShadeCartFormatters.buildPinkedPerimeterCoords(200f, 140f)
        val poly = pts.joinToString(" ") { "${String.format(Locale.US, "%.1f", it.first)},${String.format(Locale.US, "%.1f", it.second)}" }
        val safeHex = if (colorHex.matches(Regex("^#[0-9A-Fa-f]{6}$"))) colorHex else "#334155"
        return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 140"><polygon points="$poly" fill="$safeHex" stroke="rgba(0,0,0,0.22)" stroke-width="1.5"/></svg>"""
    }

    private fun escapeHtml(raw: String): String = raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun escapeJs(raw: String): String = raw
        .replace("\\", "\\\\")
        .replace("'", "\\'")
}
