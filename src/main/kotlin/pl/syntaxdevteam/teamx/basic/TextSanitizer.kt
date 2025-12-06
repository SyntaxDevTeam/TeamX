package pl.syntaxdevteam.teamx.basic

object TextSanitizer {
    private val legacyColorPattern = "§[0-9a-fk-orx]".toRegex(RegexOption.IGNORE_CASE)
    private val legacyRgbPattern = "§x(§[0-9a-fA-F]){6}".toRegex()
    private val vaultRgbPattern = "&#[0-9a-fA-F]{6}".toRegex()
    private val miniMessagePattern = "<(/?\\w+)(:[^>]*)?>".toRegex()
    private val ampersandPattern = "&[0-9a-fklmor]".toRegex(RegexOption.IGNORE_CASE)  // Obsługa `&X`
    private val ampersandDoublePattern = "&[0-9a-fklmor]&[0-9a-fklmor]".toRegex(RegexOption.IGNORE_CASE) // Obsługa `&X&Y`

    fun stripFormatting(text: String): String {
        return text.replace(legacyRgbPattern, "")
            .replace(legacyColorPattern, "")
            .replace(vaultRgbPattern, "")
            .replace(miniMessagePattern, "")
            .replace(ampersandDoublePattern, "")
            .replace(ampersandPattern, "")
    }
}

