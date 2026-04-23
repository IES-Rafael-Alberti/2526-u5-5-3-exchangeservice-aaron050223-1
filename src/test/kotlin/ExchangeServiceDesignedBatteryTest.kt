package com.example.exchange

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifySequence
import org.iesra.revilofe.ExchangeRateProvider
import org.iesra.revilofe.ExchangeService
import org.iesra.revilofe.InMemoryExchangeRateProvider
import org.iesra.revilofe.Money

class ExchangeServiceDesignedBatteryTest : DescribeSpec({

    afterTest {
        clearAllMocks()
    }

    describe("battery designed from equivalence classes for ExchangeService") {

        describe("input validation") {
            val provider = mockk<ExchangeRateProvider>()
            val service = ExchangeService(provider)

            it("throws an exception when the amount is zero") {
                shouldThrow<IllegalArgumentException> {
                    service.exchange(Money(0, "USD"), "EUR")
                }
            }

            it("throws an exception when the amount is negative") {
                shouldThrow<IllegalArgumentException> {
                    service.exchange(Money(-1, "USD"), "EUR")
                }
            }

            it("throws an exception when the source currency code is invalid") {
                shouldThrow<IllegalArgumentException> {
                    service.exchange(Money(42, "US"), "EUR")
                }
            }

            it("throws an exception when the target currency code is invalid") {
                shouldThrow<IllegalArgumentException> {
                    service.exchange(Money(42, "USD"), "EU")
                }
            }
        }

        describe("misma moneda y spy") {
            it("debe devolver la misma cantidad si origen y destino son iguales.") {
                val spyProvider = spyk<ExchangeRateProvider>()
                val service = ExchangeService(spyProvider)

                service.exchange(Money(100, "EUR"), "EUR") shouldBe 100

                verify(exactly = 0) { spyProvider.rate(any()) }
            }

            it("debe convertir correctamente usando una tasa directa con stub") {
                val stubProvider = mockk<ExchangeRateProvider>()
                every { stubProvider.rate("GBPUSD") } returns 1.25

                val service = ExchangeService(stubProvider)

                service.exchange(Money(10, "GBP"), "USD") shouldBe 12 // (10 * 1.25)
            }

            it("debe usar spy sobre InMemoryExchangeRateProvider para verificar una llamada real correcta") {
                val realProvider = InMemoryExchangeRateProvider(mapOf("USDEUR" to 0.92))
                val spyProvider = spyk(realProvider)
                val service = ExchangeService(spyProvider)

                service.exchange(Money(100, "USD"), "EUR") shouldBe 92

                verify(exactly = 1) { spyProvider.rate("USDEUR") }
            }
        }

        describe("conversiones con mock") {
            val mockProvider = mockk<ExchangeRateProvider>()
            val service = ExchangeService(mockProvider, supportedCurrencies = setOf("USD", "EUR", "GBP"))

            it("debe resolver una conversión cruzada cuando la tasa directa no exista usando mock") {
                every { mockProvider.rate("USDGBP") } throws IllegalArgumentException()
                every { mockProvider.rate("USDEUR") } returns 0.90
                every { mockProvider.rate("EURGBP") } returns 0.85

                service.exchange(Money(100, "USD"), "GBP") shouldBe 76 // (100 * 0.9 * 0.85)
            }

            it("debe lanzar excepción si no existe ninguna ruta válida") {
                every { mockProvider.rate(any()) } throws IllegalArgumentException()

                shouldThrow<IllegalArgumentException> {
                    service.exchange(Money(100, "USD"), "GBP")
                }
            }

            it("debe verificar el orden exacto de las llamadas al proveedor en una conversión cruzada") {
                every { mockProvider.rate("USDGBP") } throws IllegalArgumentException()
                every { mockProvider.rate("USDEUR") } returns 0.9
                every { mockProvider.rate("EURGBP") } returns 0.8

                service.exchange(Money(10, "USD"), "GBP")

                verifySequence {
                    mockProvider.rate("USDGBP")
                    mockProvider.rate("USDEUR")
                    mockProvider.rate("EURGBP")
                }
            }
        }
    }})