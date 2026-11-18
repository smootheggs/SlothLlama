package app.localmind.assistant.domain.tools

import java.util.Locale

/**
 * Measure conversion for length, weight, volume (US kitchen measures), temperature, speed, area,
 * data and time: "5 miles in km", "350 F to C", "how many cups in a litre". No currencies: the
 * app is offline, so it has no exchange rates.
 */
object Units {

    class Result(val amount: Double, val from: Measure, val to: Measure, val value: Double) {
        val label: String get() = "${Calculator.format(amount)} ${from.symbol} = ${round(value)} ${to.symbol}"
    }

    enum class Kind { LENGTH, MASS, VOLUME, TEMPERATURE, SPEED, AREA, DATA, TIME }

    /** [factor] converts to the kind's base unit; temperatures use [toBase]/[fromBase] instead. */
    class Measure(val symbol: String, val kind: Kind, val factor: Double, vararg val names: String)

    private val UNITS = listOf(
        Measure("mm", Kind.LENGTH, 0.001, "mm", "millimeter", "millimeters", "millimetre", "millimetres"),
        Measure("cm", Kind.LENGTH, 0.01, "cm", "centimeter", "centimeters", "centimetre", "centimetres"),
        Measure("m", Kind.LENGTH, 1.0, "m", "meter", "meters", "metre", "metres"),
        Measure("km", Kind.LENGTH, 1000.0, "km", "kilometer", "kilometers", "kilometre", "kilometres", "kms"),
        Measure("in", Kind.LENGTH, 0.0254, "in", "inch", "inches", "\""),
        Measure("ft", Kind.LENGTH, 0.3048, "ft", "foot", "feet", "'"),
        Measure("yd", Kind.LENGTH, 0.9144, "yd", "yard", "yards"),
        Measure("mi", Kind.LENGTH, 1609.344, "mi", "mile", "miles"),
        Measure("nmi", Kind.LENGTH, 1852.0, "nmi", "nautical mile", "nautical miles"),
        Measure("mg", Kind.MASS, 1e-6, "mg", "milligram", "milligrams"),
        Measure("g", Kind.MASS, 0.001, "g", "gram", "grams", "gramme", "grammes"),
        Measure("kg", Kind.MASS, 1.0, "kg", "kilo", "kilos", "kilogram", "kilograms", "kgs"),
        Measure("t", Kind.MASS, 1000.0, "tonne", "tonnes", "metric ton", "metric tons"),
        Measure("oz", Kind.MASS, 0.028349523125, "oz", "ounce", "ounces"),
        Measure("lb", Kind.MASS, 0.45359237, "lb", "lbs", "pound", "pounds"),
        Measure("st", Kind.MASS, 6.35029318, "st", "stone", "stones"),
        Measure("ml", Kind.VOLUME, 0.001, "ml", "milliliter", "milliliters", "millilitre", "millilitres"),
        Measure("cl", Kind.VOLUME, 0.01, "cl", "centiliter", "centiliters", "centilitre", "centilitres"),
        Measure("dl", Kind.VOLUME, 0.1, "dl", "deciliter", "deciliters", "decilitre", "decilitres"),
        Measure("L", Kind.VOLUME, 1.0, "l", "liter", "liters", "litre", "litres"),
        Measure("tsp", Kind.VOLUME, 0.00492892159375, "tsp", "teaspoon", "teaspoons"),
        Measure("tbsp", Kind.VOLUME, 0.01478676478125, "tbsp", "tablespoon", "tablespoons"),
        Measure("cups", Kind.VOLUME, 0.2365882365, "cup", "cups"),
        Measure("fl oz", Kind.VOLUME, 0.0295735295625, "fl oz", "fluid ounce", "fluid ounces"),
        Measure("pints", Kind.VOLUME, 0.473176473, "pt", "pint", "pints"),
        Measure("quarts", Kind.VOLUME, 0.946352946, "qt", "quart", "quarts"),
        Measure("gal", Kind.VOLUME, 3.785411784, "gal", "gallon", "gallons"),
        Measure("°C", Kind.TEMPERATURE, 1.0, "c", "°c", "celsius", "centigrade", "degrees c", "degrees celsius"),
        Measure("°F", Kind.TEMPERATURE, 1.0, "f", "°f", "fahrenheit", "degrees f", "degrees fahrenheit"),
        Measure("K", Kind.TEMPERATURE, 1.0, "k", "kelvin"),
        Measure("m/s", Kind.SPEED, 1.0, "m/s", "meters per second", "metres per second"),
        Measure("km/h", Kind.SPEED, 1 / 3.6, "km/h", "kph", "kmh", "kilometers per hour", "kilometres per hour"),
        Measure("mph", Kind.SPEED, 0.44704, "mph", "miles per hour"),
        Measure("knots", Kind.SPEED, 1852.0 / 3600, "knot", "knots", "kn", "kt"),
        Measure("m²", Kind.AREA, 1.0, "m2", "m²", "sq m", "square meter", "square meters", "square metre", "square metres"),
        Measure("km²", Kind.AREA, 1e6, "km2", "km²", "sq km", "square kilometer", "square kilometers", "square kilometre", "square kilometres"),
        Measure("ft²", Kind.AREA, 0.09290304, "ft2", "ft²", "sq ft", "square foot", "square feet"),
        Measure("mi²", Kind.AREA, 2589988.110336, "mi2", "mi²", "sq mi", "square mile", "square miles"),
        Measure("acres", Kind.AREA, 4046.8564224, "acre", "acres"),
        Measure("ha", Kind.AREA, 10000.0, "ha", "hectare", "hectares"),
        Measure("B", Kind.DATA, 1.0, "byte", "bytes"),
        Measure("KB", Kind.DATA, 1e3, "kb", "kilobyte", "kilobytes"),
        Measure("MB", Kind.DATA, 1e6, "mb", "megabyte", "megabytes"),
        Measure("GB", Kind.DATA, 1e9, "gb", "gigabyte", "gigabytes"),
        Measure("TB", Kind.DATA, 1e12, "tb", "terabyte", "terabytes"),
        Measure("KiB", Kind.DATA, 1024.0, "kib"),
        Measure("MiB", Kind.DATA, 1048576.0, "mib"),
        Measure("GiB", Kind.DATA, 1073741824.0, "gib"),
        Measure("s", Kind.TIME, 1.0, "s", "sec", "secs", "second", "seconds"),
        Measure("min", Kind.TIME, 60.0, "min", "mins", "minute", "minutes"),
        Measure("h", Kind.TIME, 3600.0, "h", "hr", "hrs", "hour", "hours"),
        Measure("days", Kind.TIME, 86400.0, "day", "days"),
        Measure("weeks", Kind.TIME, 604800.0, "week", "weeks"),
        Measure("years", Kind.TIME, 31557600.0, "year", "years")
    )

    /** Every name, longest first, so "fl oz" wins over "oz" and "square feet" over "feet". */
    private val NAMES: List<Pair<String, Measure>> =
        UNITS.flatMap { u -> u.names.map { it to u } }.sortedByDescending { it.first.length }

    private val NUMBER = Regex("(-?\\d+(?:[.,]\\d+)?)")
    private val CONNECTOR = Regex("^\\s*(?:to|in|into|as|=|->|→|en|nach)\\s+", RegexOption.IGNORE_CASE)
    private val HOW_MANY = Regex(
        "how many\\s+(.+?)\\s+(?:are\\s+)?(?:in|per|make)\\s+(?:a|an|one|(\\d+(?:[.,]\\d+)?))\\s+(.+?)(?:\\?|$|\\s+(?:are there|is that))",
        RegexOption.IGNORE_CASE
    )

    /** The conversion asked for in [message], or null. */
    fun find(message: String): Result? {
        val text = message.lowercase(Locale.ROOT).replace('’', '\'')
        HOW_MANY.find(text)?.let { m ->
            val to = unitAt(m.groupValues[1], 0)?.first
            val amount = m.groupValues[2].ifEmpty { "1" }.replace(',', '.').toDoubleOrNull() ?: 1.0
            val from = unitAt(m.groupValues[3], 0)?.first
            if (to != null && from != null && to.kind == from.kind && to !== from) return Result(amount, from, to, convert(amount, from, to))
        }
        for (n in NUMBER.findAll(text)) {
            val amount = n.groupValues[1].replace(',', '.').toDoubleOrNull() ?: continue
            val (from, afterFrom) = unitAt(text, n.range.last + 1) ?: continue
            val connector = CONNECTOR.find(text.substring(afterFrom)) ?: continue
            val (to, _) = unitAt(text, afterFrom + connector.range.last + 1) ?: continue
            if (from.kind != to.kind || from === to) continue
            return Result(amount, from, to, convert(amount, from, to))
        }
        return null
    }

    /** The unit named at [start] in [text] (after spaces) and where its name ends. */
    private fun unitAt(text: String, start: Int): Pair<Measure, Int>? {
        var i = start
        while (i < text.length && text[i] == ' ') i++
        // "degrees" on its own before a scale: "100 degrees f".
        for ((name, unit) in NAMES) {
            if (!text.startsWith(name, i)) continue
            val end = i + name.length
            // Whole words only ("m" mustn't match "march"), except symbols like ' and ".
            if (end < text.length && text[end].isLetterOrDigit() && name.last().isLetterOrDigit()) continue
            return unit to end
        }
        return null
    }

    fun convert(amount: Double, from: Measure, to: Measure): Double {
        if (from.kind != Kind.TEMPERATURE) return amount * from.factor / to.factor
        val celsius = when (from.symbol) {
            "°F" -> (amount - 32) * 5 / 9
            "K" -> amount - 273.15
            else -> amount
        }
        return when (to.symbol) {
            "°F" -> celsius * 9 / 5 + 32
            "K" -> celsius + 273.15
            else -> celsius
        }
    }

    /** Four significant figures for everyday answers, whole numbers as they are. */
    private fun round(v: Double): String {
        if (v == 0.0) return "0"
        val abs = kotlin.math.abs(v)
        val digits = (3 - kotlin.math.floor(kotlin.math.log10(abs)).toInt()).coerceIn(0, 10)
        val rounded = java.math.BigDecimal(v).setScale(digits, java.math.RoundingMode.HALF_UP).toDouble()
        return Calculator.format(rounded)
    }
}
