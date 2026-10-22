package ua.x44to.kopiyka.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.IOException

class RatesTest {
    @Test
    fun monobank() {
        val m = Rates.parseMono(
            """[{"currencyCodeA":840,"currencyCodeB":980,"date":1792616473,"rateBuy":44.83,"rateSell":45.2345},
               {"currencyCodeA":978,"currencyCodeB":980,"date":1792653373,"rateBuy":50.38,"rateSell":51.01},
               {"currencyCodeA":978,"currencyCodeB":840,"date":1792648873,"rateBuy":1.119,"rateSell":1.129},
               {"currencyCodeA":826,"currencyCodeB":980,"date":1792660694,"rateCross":60.6595},
               {"currencyCodeA":392,"currencyCodeB":980,"date":1792660631,"rateCross":0.2866}]""",
        )
        assertEquals((44.83 + 45.2345) / 2, m.getValue("USD"), 1e-9)
        assertEquals((50.38 + 51.01) / 2, m.getValue("EUR"), 1e-9) // EUR→USD пропускаємо
        assertEquals(60.6595, m.getValue("GBP"), 1e-9)
        assertEquals(1.0, m.getValue("UAH"), 0.0)
        assertTrue("єни Копійка не знає", "JPY" !in m)
    }

    @Test
    fun nbu() {
        val m = Rates.parseNbu(
            """[{"r030":840,"txt":"Долар США","rate":41.7713,"cc":"USD","exchangedate":"22.10.2026"},
               {"r030":985,"txt":"Злотий","rate":11.4321,"cc":"PLN","exchangedate":"22.10.2026"},
               {"r030":392,"txt":"Єна","rate":0.27,"cc":"JPY","exchangedate":"22.10.2026"}]""",
        )
        assertEquals(41.7713, m.getValue("USD"), 1e-9)
        assertEquals(11.4321, m.getValue("PLN"), 1e-9)
        assertTrue("JPY" !in m)
    }

    @Test
    fun conversionUsesRates() {
        val s = Settings(base = "UAH", rates = Money.DEFAULT_RATES + ("USD" to 42.0))
        assertEquals(4_200_00L, Money.convert(100_00, "USD", "UAH", s)) // 100 $ = 4200 ₴
        val usdBase = s.copy(base = "USD")
        assertEquals(100_00L, Money.convert(4_200_00, "UAH", "USD", usdBase))
    }

    /** Справжній запит (лише в CI). Monobank обмежує частоту, НБУ буває закритий з-за кордону — тоді пропускаємо. */
    @Test
    fun live() {
        assumeTrue(System.getenv("KP_LIVE_TESTS") == "1")
        val got = try {
            Rates.fetchAny(Rates.Source.MONO)
        } catch (e: IOException) {
            assumeTrue("rates sources unreachable from CI: $e", false)
            return
        }
        val usd = got.first.getValue("USD")
        println("live rates from ${got.second}: USD=$usd EUR=${got.first["EUR"]}")
        assertTrue("курс долара схожий на правду: $usd", usd in 20.0..200.0)
    }
}
