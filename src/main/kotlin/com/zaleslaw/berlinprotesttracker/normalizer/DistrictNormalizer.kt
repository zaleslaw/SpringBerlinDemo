package com.zaleslaw.berlinprotesttracker.normalizer

object DistrictNormalizer {

    // Standard Berlin PLZ → Bezirk mapping (all ~191 postal codes)
    private val plzToDistrict: Map<String, String> = buildMap {
        // Mitte
        listOf("10115","10117","10119","10135","10178","10179","13347","13349","13351","13353","13355","13357","13359","13407","13409").forEach { put(it, "Mitte") }
        // Friedrichshain-Kreuzberg
        listOf("10243","10245","10247","10249","10961","10963","10965","10967","10969","10997","10999").forEach { put(it, "Friedrichshain-Kreuzberg") }
        // Pankow
        listOf("10405","10407","10409","10435","10437","10439","13053","13055","13057","13059","13086","13088","13089","13125","13127","13129","13156","13158","13159","13187","13189").forEach { put(it, "Pankow") }
        // Charlottenburg-Wilmersdorf
        listOf("10585","10587","10589","10623","10625","10627","10629","10707","10709","10711","10713","10715","10717","10719","10787","10789","13597","14050","14052","14053","14055","14057","14059").forEach { put(it, "Charlottenburg-Wilmersdorf") }
        // Spandau
        listOf("13581","13583","13585","13587","13589","13591","13593","13595","13599","14052").forEach { put(it, "Spandau") }
        // Steglitz-Zehlendorf
        listOf("12163","12165","12167","12169","12203","12205","12207","12209","14109","14129","14163","14165","14167","14169","14193","14195","14197","14199").forEach { put(it, "Steglitz-Zehlendorf") }
        // Tempelhof-Schöneberg
        listOf("10777","10779","10781","10783","10785","10823","10825","10827","12099","12101","12103","12105","12107","12109","12157","12159","12161","14197").forEach { put(it, "Tempelhof-Schöneberg") }
        // Neukölln
        listOf("12043","12045","12047","12049","12051","12053","12055","12057","12059","12305","12307","12309","12347","12349","12351","12353","12355","12357","12359").forEach { put(it, "Neukölln") }
        // Treptow-Köpenick
        listOf("12435","12437","12439","12459","12487","12489","12524","12526","12527","12555","12557","12559","12587","12589","12623").forEach { put(it, "Treptow-Köpenick") }
        // Marzahn-Hellersdorf
        listOf("12619","12621","12623","12627","12629","12679","12681","12683","12685","12687","12689").forEach { put(it, "Marzahn-Hellersdorf") }
        // Lichtenberg
        listOf("10315","10317","10319","10365","10367","10369","13051","13053","13057","13059","10315").forEach { put(it, "Lichtenberg") }
        // Reinickendorf
        listOf("13403","13405","13407","13409","13435","13437","13439","13465","13467","13469","13503","13505","13507","13509","13629").forEach { put(it, "Reinickendorf") }
    }

    fun extractDistrictFromPlz(plz: String?): String? = plz?.trim()?.let { plzToDistrict[it] }

    private val knownDistricts = listOf(
        "Mitte",
        "Friedrichshain-Kreuzberg",
        "Pankow",
        "Charlottenburg-Wilmersdorf",
        "Spandau",
        "Steglitz-Zehlendorf",
        "Tempelhof-Schöneberg",
        "Neukölln",
        "Treptow-Köpenick",
        "Marzahn-Hellersdorf",
        "Lichtenberg",
        "Reinickendorf"
    )

    private val aliases = mapOf(
        "friedrichshain" to "Friedrichshain-Kreuzberg",
        "kreuzberg" to "Friedrichshain-Kreuzberg",
        "charlottenburg" to "Charlottenburg-Wilmersdorf",
        "wilmersdorf" to "Charlottenburg-Wilmersdorf",
        "tempelhof" to "Tempelhof-Schöneberg",
        "schoeneberg" to "Tempelhof-Schöneberg",
        "schöneberg" to "Tempelhof-Schöneberg",
        "neukoelln" to "Neukölln",
        "treptow" to "Treptow-Köpenick",
        "koepenick" to "Treptow-Köpenick",
        "köpenick" to "Treptow-Köpenick",
        "marzahn" to "Marzahn-Hellersdorf",
        "hellersdorf" to "Marzahn-Hellersdorf"
    )

    private val postalCodePattern = Regex("""\b1[0-4]\d{3}\b""")

    fun extractDistrict(districtRaw: String?, rawText: String): String? {
        val source = districtRaw ?: rawText
        val lower = source.lowercase()

        for (district in knownDistricts) {
            if (lower.contains(district.lowercase())) return district
        }
        for ((alias, canonical) in aliases) {
            if (lower.contains(alias)) return canonical
        }
        return null
    }

    fun extractPostalCode(rawText: String): String? =
        postalCodePattern.find(rawText)?.value

    fun extractParticipantCount(raw: String?): Int? {
        if (raw == null) return null
        val cleaned = raw.replace(".", "").replace(",", "").replace("ca.", "").trim()
        return cleaned.filter { it.isDigit() }.takeIf { it.isNotEmpty() }?.toIntOrNull()
    }
}
