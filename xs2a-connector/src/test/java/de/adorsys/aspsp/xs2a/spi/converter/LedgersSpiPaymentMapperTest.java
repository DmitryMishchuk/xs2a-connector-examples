package de.adorsys.aspsp.xs2a.spi.converter;

import de.adorsys.ledgers.domain.payment.AmountTO;
import de.adorsys.ledgers.domain.payment.BulkPaymentTO;
import de.adorsys.ledgers.domain.payment.PeriodicPaymentTO;
import de.adorsys.ledgers.domain.payment.SinglePaymentTO;
import de.adorsys.psd2.xs2a.spi.domain.account.SpiAccountReference;
import de.adorsys.psd2.xs2a.spi.domain.code.SpiFrequencyCode;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiAmount;
import de.adorsys.psd2.xs2a.spi.domain.common.SpiTransactionStatus;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiAddress;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiBulkPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiPeriodicPayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.SpiSinglePayment;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiBulkPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiPeriodicPaymentInitiationResponse;
import de.adorsys.psd2.xs2a.spi.domain.payment.response.SpiSinglePaymentInitiationResponse;
import org.junit.Test;
import org.mapstruct.factory.Mappers;
import pro.javatar.commons.reader.YamlReader;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

public class LedgersSpiPaymentMapperTest {
    private final LedgersSpiPaymentMapper mapper = Mappers.getMapper(LedgersSpiPaymentMapper.class);


    @Test
    public void toSinglePaymentTO() {
        //Given
        SpiSinglePayment spiPayment = getSpiSingle();
        SinglePaymentTO expected = readYml(SinglePaymentTO.class, "PaymentSingleTO.yml");

        //When
        SinglePaymentTO payment = mapper.toSinglePaymentTO(spiPayment);
        assertThat(payment).isNotNull();
        assertThat(payment).isEqualToComparingFieldByFieldRecursively(expected);
    }

    @Test
    public void toPeriodicPaymentTO() {
        //Given
        SpiPeriodicPayment spiPayment = getPeriodic();
        PeriodicPaymentTO expected = readYml(PeriodicPaymentTO.class, "PaymentPeriodicTO.yml");

        //When
        PeriodicPaymentTO payment = mapper.toPeriodicPaymentTO(spiPayment);
        assertThat(payment).isNotNull();
        assertThat(payment).isEqualToComparingFieldByFieldRecursively(expected);
    }

    @Test
    public void toBulkPaymentTO() {
        //Given
        SpiBulkPayment spiPayment = getBulk();
        BulkPaymentTO expected = readYml(BulkPaymentTO.class, "PaymentBulkTO.yml");

        //When
        BulkPaymentTO payment = mapper.toBulkPaymentTO(spiPayment);
        assertThat(payment).isNotNull();
        assertThat(payment).isEqualToComparingFieldByFieldRecursively(expected);
    }

    @Test
    public void toSpiSingleResponse() {
        //Given
        SinglePaymentTO initial = readYml(SinglePaymentTO.class, "PaymentSingleTO.yml");
        SpiSinglePaymentInitiationResponse expected = readYml(SpiSinglePaymentInitiationResponse.class, "SingleSpiResponse.yml");

        //When
        SpiSinglePaymentInitiationResponse response = mapper.toSpiSingleResponse(initial);
        assertThat(response).isNotNull();
        assertThat(response).isEqualToComparingFieldByFieldRecursively(expected);
    }

    @Test
    public void toSpiPeriodicResponse() {
        //Given
        PeriodicPaymentTO initial = readYml(PeriodicPaymentTO.class, "PaymentPeriodicTO.yml");
        SpiPeriodicPaymentInitiationResponse expected = readYml(SpiPeriodicPaymentInitiationResponse.class, "SingleSpiResponse.yml");

        //When
        SpiPeriodicPaymentInitiationResponse response = mapper.toSpiPeriodicResponse(initial);
        assertThat(response).isNotNull();
        assertThat(response).isEqualToComparingFieldByFieldRecursively(expected);
    }

    @Test
    public void toSpiBulkResponse() {
        //Given
        BulkPaymentTO initial = readYml(BulkPaymentTO.class, "PaymentBulkTO.yml");
        SpiBulkPaymentInitiationResponse expected = getBulkResponse();

        //When
        SpiBulkPaymentInitiationResponse response = mapper.toSpiBulkResponse(initial);
        assertThat(response).isNotNull();
        assertThat(response).isEqualToComparingFieldByFieldRecursively(expected);
    }

    @Test
    public void mapToSpiBulkPayment() {
        //Given
        BulkPaymentTO initial = readYml(BulkPaymentTO.class, "PaymentBulkTO.yml");
        SpiBulkPayment expected = getBulk();

        //When
        SpiBulkPayment response = mapper.mapToSpiBulkPayment(initial);
        assertThat(response).isNotNull();
        assertThat(response).isEqualToComparingFieldByFieldRecursively(expected);
    }

    private SpiSinglePayment getSpiSingle() {
        SpiSinglePayment spiPayment = new SpiSinglePayment("SEPA");
        spiPayment.setPaymentId("myPaymentId");
        spiPayment.setEndToEndIdentification("123456789");
        spiPayment.setDebtorAccount(getDebtorAcc());
        spiPayment.setInstructedAmount(new SpiAmount(Currency.getInstance("EUR"), BigDecimal.valueOf(100)));
        spiPayment.setCreditorAccount(getDebtorAcc());
        spiPayment.setCreditorAgent("agent");
        spiPayment.setCreditorName("Rozetka.ua");
        spiPayment.setCreditorAddress(new SpiAddress("SomeStreet", "666", "Kiev", "04210", "Ukraine"));
        spiPayment.setRemittanceInformationUnstructured("remittance");
        spiPayment.setPaymentStatus(SpiTransactionStatus.RCVD);
        spiPayment.setRequestedExecutionDate(LocalDate.of(2018, 12, 12));
        spiPayment.setRequestedExecutionTime(OffsetDateTime.of(LocalDate.of(2018, 12, 12), LocalTime.of(12, 0), ZoneOffset.UTC));
        return spiPayment;
    }

    private SpiPeriodicPayment getPeriodic() {
        SpiPeriodicPayment spiPayment = new SpiPeriodicPayment("SEPA");
        spiPayment.setPaymentId("myPaymentId");
        spiPayment.setEndToEndIdentification("123456789");
        spiPayment.setDebtorAccount(getDebtorAcc());
        spiPayment.setInstructedAmount(new SpiAmount(Currency.getInstance("EUR"), BigDecimal.valueOf(100)));
        spiPayment.setCreditorAccount(getDebtorAcc());
        spiPayment.setCreditorAgent("agent");
        spiPayment.setCreditorName("Rozetka.ua");
        spiPayment.setCreditorAddress(new SpiAddress("SomeStreet", "666", "Kiev", "04210", "Ukraine"));
        spiPayment.setRemittanceInformationUnstructured("remittance");
        spiPayment.setPaymentStatus(SpiTransactionStatus.RCVD);
        spiPayment.setRequestedExecutionDate(LocalDate.of(2018, 12, 12));
        spiPayment.setRequestedExecutionTime(OffsetDateTime.of(LocalDate.of(2018, 12, 12), LocalTime.of(12, 0), ZoneOffset.UTC));

        spiPayment.setStartDate(LocalDate.of(2018, 12, 12));
        spiPayment.setEndDate(LocalDate.of(2018, 12, 28));
        spiPayment.setExecutionRule("everyday");
        spiPayment.setFrequency(SpiFrequencyCode.DAILY);
        spiPayment.setDayOfExecution(1);
        return spiPayment;
    }

    private SpiBulkPayment getBulk() {
        SpiBulkPayment payment = new SpiBulkPayment();
        payment.setPaymentId("myPaymentId");
        payment.setBatchBookingPreferred(false);
        payment.setDebtorAccount(getDebtorAcc());
        payment.setRequestedExecutionDate(LocalDate.of(2018, 12, 12));
        payment.setPaymentStatus(SpiTransactionStatus.RCVD);
        SpiSinglePayment one = getSpiSingle();
        one.setPaymentId("myPaymentId1");
        one.setEndToEndIdentification("123456788");
        one.setCreditorAccount(getCreditorAcc());
        one.setInstructedAmount(new SpiAmount(Currency.getInstance("EUR"), BigDecimal.valueOf(200)));
        one.setCreditorAccount(getCreditorAcc());

        SpiSinglePayment two = getSpiSingle();
        two.setPaymentId("myPaymentId2");
        two.setCreditorAccount(getCreditorAcc2());
        two.setCreditorName("Sokol.ua");
        two.setPaymentProduct("CROSS_BORDER");

        payment.setPayments(Arrays.asList(one, two));
        payment.setPaymentProduct("SEPA");
        return payment;
    }

    private SpiAccountReference getDebtorAcc() {
        return new SpiAccountReference(null, "DE91100000000123456789", "bban", "pan", "maskedPan", "msisdn", Currency.getInstance("EUR"));
    }

    private SpiAccountReference getCreditorAcc() {
        return new SpiAccountReference(null, "DE91100000000123456787", "bban", "pan", "maskedPan", "msisdn", Currency.getInstance("EUR"));
    }

    private SpiAccountReference getCreditorAcc2() {
        return new SpiAccountReference(null, "DE91100000000123456788", "bban", "pan", "maskedPan", "msisdn", Currency.getInstance("EUR"));
    }

    private SpiBulkPaymentInitiationResponse getBulkResponse() {
        SpiBulkPaymentInitiationResponse resp = new SpiBulkPaymentInitiationResponse();
        resp.setPaymentId("myPaymentId");
        resp.setTransactionStatus(SpiTransactionStatus.RCVD);
        resp.setPayments(getBulk().getPayments());
        return resp;
    }

    private AmountTO getAmountTO(long amount) {
        AmountTO to = new AmountTO();
        to.setCurrency(Currency.getInstance("EUR"));
        to.setAmount(BigDecimal.valueOf(amount));
        return to;
    }

    private static <T> T readYml(Class<T> aClass, String file) {
        try {
            return YamlReader.getInstance().getObjectFromResource(LedgersSpiPaymentMapper.class, file, aClass);
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalStateException("Resource file not found", e);
        }
    }
}