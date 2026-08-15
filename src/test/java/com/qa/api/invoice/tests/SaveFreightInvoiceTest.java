package com.qa.api.invoice.tests;

import com.aventstack.chaintest.plugins.ChainTestListener;
import com.qa.api.base.BaseTest;
import com.qa.api.constants.AuthType;
import com.qa.api.manager.ConfigManager;
import com.qa.api.pojo.invoice.*;
import io.qameta.allure.*;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Epic("Freight Invoice")
@Story("Save Freight Invoice — Dedicated Fleet Invoice with two charges (COD + ECOD)")
public class SaveFreightInvoiceTest extends BaseTest {

    private static final String BASE_URL_INVOICE = "https://scnxtgen01.cp.manh.cloud/invoice/api";
    private static final String SAVE_ENDPOINT     = "/invoice/freightInvoice/save";

    @BeforeClass
    public void setUpInvoiceToken() {
        ConfigManager.setProp("bearerToken", "REPLACE_WITH_VALID_TOKEN");
    }

    @Description("Saves a Dedicated Fleet Invoice with COD and ECOD charges and validates that the invoice reaches Payment Processing status with all amounts correctly approved and vouchered")
    @Severity(SeverityLevel.CRITICAL)
    @Owner("Raghuveer")
    @Test
    public void saveFreightInvoiceTest() {
        ChainTestListener.log("Save Freight Invoice Test — COD + ECOD charges");

        FreightInvoice invoice = buildInvoiceRequest();

        Response response = restClient.postCall(
                BASE_URL_INVOICE, SAVE_ENDPOINT,
                invoice, null, null,
                AuthType.BEARER_TOKEN, ContentType.JSON);

        // ── Top-level response envelope ──────────────────────────────────────
        Assert.assertEquals(response.jsonPath().getBoolean("success"), true,
                "Response envelope success flag must be true");
        Assert.assertEquals(response.jsonPath().getString("statusCode"), "OK",
                "HTTP status code in response body must be OK");

        // ── Invoice identity ─────────────────────────────────────────────────
        Assert.assertEquals(response.jsonPath().getString("data.FreightInvoiceId"), "INV_AUF14_004",
                "FreightInvoiceId must match the submitted invoice ID");
        Assert.assertEquals(response.jsonPath().getString("data.OrgId"), "3270",
                "OrgId must be preserved from request");
        Assert.assertEquals(response.jsonPath().getString("data.PayeeId"), "CUM_DIST_E2E_CAR1_2625",
                "PayeeId must be preserved from request");

        // ── Invoice status must advance to Payment Processing ────────────────
        Assert.assertEquals(response.jsonPath().getString("data.StatusId.FreightInvoiceStatusId"), "5400",
                "Invoice status must advance to 5400 after save");
        Assert.assertEquals(response.jsonPath().getString("data.StatusId.Name"), "Payment Processing",
                "Invoice status name must be 'Payment Processing'");

        // ── Invoice type and payment method ──────────────────────────────────
        Assert.assertEquals(response.jsonPath().getString("data.FreightInvoiceTypeId.FreightInvoiceTypeId"), "DED-FLT",
                "Invoice type must remain Dedicated Fleet Invoice");
        Assert.assertEquals(response.jsonPath().getString("data.RequestedPaymentMethodId.PaymentMethodId"), "CHK",
                "Payment method must remain Paper Check (CHK)");

        // ── Financial totals (core invoice validation) ───────────────────────
        Assert.assertEquals(response.jsonPath().getDouble("data.TotalInvoicedAmount"), 1850.0,
                "TotalInvoicedAmount must equal sum of COD (650) + ECOD (1200)");
        Assert.assertEquals(response.jsonPath().getDouble("data.TotalApprovedAmount"), 1850.0,
                "TotalApprovedAmount must equal TotalInvoicedAmount — full approval expected");
        Assert.assertEquals(response.jsonPath().getDouble("data.RequestedTotalOnInvoice"), 1850.0,
                "RequestedTotalOnInvoice must reflect the combined charge total");
        Assert.assertEquals(response.jsonPath().getInt("data.InvoiceApprovalTotal"), 1850,
                "InvoiceApprovalTotal must equal 1850");

        // ── Header summary ───────────────────────────────────────────────────
        Assert.assertEquals(response.jsonPath().getInt("data.HeaderSummary.NumberOfCharges"), 2,
                "HeaderSummary must report exactly 2 charges");
        Assert.assertEquals(response.jsonPath().getInt("data.HeaderSummary.BilledTotal"), 1850,
                "HeaderSummary.BilledTotal must equal 1850");
        Assert.assertEquals(response.jsonPath().getInt("data.HeaderSummary.ApprovedTotal"), 1850,
                "HeaderSummary.ApprovedTotal must equal 1850");
        Assert.assertEquals(response.jsonPath().getInt("data.HeaderSummary.RejectedTotal"), 0,
                "HeaderSummary.RejectedTotal must be 0 — no charges rejected");
        Assert.assertEquals(response.jsonPath().getInt("data.HeaderSummary.PaidTotal"), 0,
                "HeaderSummary.PaidTotal must be 0 at save — payment not yet disbursed");

        // ── Charge status summary — both charges must be Ready To Be Vouchered
        Assert.assertEquals(
                response.jsonPath().getString("data.ChargeStatusSummary.ChargeSummaryByStatus[0].StatusCode"), "6000",
                "All charges must reach status 6000 (Ready To Be Vouchered)");
        Assert.assertEquals(
                response.jsonPath().getInt("data.ChargeStatusSummary.ChargeSummaryByStatus[0].Count"), 2,
                "Both COD and ECOD charges must be in Ready To Be Vouchered status");
        Assert.assertEquals(
                response.jsonPath().getInt("data.ChargeStatusSummary.ChargeSummaryByStatus[0].Amount"), 1850,
                "Total amount in Ready To Be Vouchered bucket must be 1850");

        // ── Per-charge validation ─────────────────────────────────────────────
        List<Map<String, Object>> charges = response.jsonPath().getList("data.FreightInvoiceCharge");
        Assert.assertEquals(charges.size(), 2, "Response must contain exactly 2 charges");

        validateCharge(charges, "COD", 650.0);
        validateCharge(charges, "ECOD", 1200.0);
    }

    private void validateCharge(List<Map<String, Object>> charges, String chargeCode, double expectedAmount) {
        Map<String, Object> charge = charges.stream()
                .filter(c -> chargeCode.equals(c.get("ChargeDefinitionId")))
                .findFirst()
                .orElse(null);

        Assert.assertNotNull(charge, "Charge with ChargeDefinitionId=" + chargeCode + " must be present in response");

        Assert.assertEquals(((Number) charge.get("InvoicedAmount")).doubleValue(), expectedAmount,
                chargeCode + " InvoicedAmount must be " + expectedAmount);
        Assert.assertEquals(((Number) charge.get("ApprovedAmount")).doubleValue(), expectedAmount,
                chargeCode + " ApprovedAmount must equal InvoicedAmount — charge fully approved");
        Assert.assertEquals(((Number) charge.get("AmountToBeApproved")).doubleValue(), expectedAmount,
                chargeCode + " AmountToBeApproved must match InvoicedAmount");

        Map<String, Object> status = (Map<String, Object>) charge.get("StatusId");
        Assert.assertNotNull(status, chargeCode + " StatusId must not be null");
        Assert.assertEquals(status.get("FreightChargeStatusId"), "6000",
                chargeCode + " charge must reach status 6000 (Ready To Be Vouchered)");

        Assert.assertEquals(charge.get("CurrencyCode"), "USD",
                chargeCode + " CurrencyCode must be USD");
        Assert.assertEquals(charge.get("OrgId"), "3270",
                chargeCode + " OrgId must be 3270");
    }

    private FreightInvoice buildInvoiceRequest() {
        FreightInvoiceCharge codCharge = FreightInvoiceCharge.builder()
                .freightInvoiceId("INV_AUF14_004")
                .currencyCode("USD")
                .isBookingAccessorial(false)
                .isDedicatedFleetAssetCharge(true)
                .orgId("3270")
                .chargeDefinitionId("COD")
                .addToInvoice(true)
                .statusId(FreightChargeStatusRef.builder()
                        .freightChargeStatusId("1000").name("Not Attempted").build())
                .invoicedAmount(650.0)
                .amountToBeApproved(650.0)
                .build();

        FreightInvoiceCharge ecodCharge = FreightInvoiceCharge.builder()
                .freightInvoiceId("INV_AUF14_004")
                .currencyCode("USD")
                .isBookingAccessorial(false)
                .isDedicatedFleetAssetCharge(true)
                .orgId("3270")
                .chargeDefinitionId("ECOD")
                .addToInvoice(true)
                .statusId(FreightChargeStatusRef.builder()
                        .freightChargeStatusId("1000").name("Not Attempted").build())
                .invoicedAmount(1200.0)
                .amountToBeApproved(1200.0)
                .build();

        return FreightInvoice.builder()
                .freightInvoiceId("INV_AUF14_004")
                .createdSourceTypeId(SourceType.builder()
                        .sourceTypeId("ManuallyShipperPortal").name("Manual").build())
                .freightInvoiceCharge(Arrays.asList(codCharge, ecodCharge))
                .billingCycleStartDate("2026-08-06T00:00:00")
                .currencyCode("USD")
                .updatedBy("gsuser")
                .terminalId("CUM_DIST_E2E_Terminal1_2625")
                .requestedPaymentMethodId(PaymentMethodRef.builder()
                        .paymentMethodId("CHK").name("Paper Check").build())
                .payeeId("CUM_DIST_E2E_CAR1_2625")
                .receivedDate("2026-08-14T00:00:00")
                .statusId(FreightInvoiceStatusRef.builder()
                        .freightInvoiceStatusId("1000").name("Draft").build())
                .countNotApprovedCharges(0)
                .paymentDueReferenceDate(PaymentDueRef.builder()
                        .referenceDateId("BILLING").name("Invoice Date").build())
                .billingDate("2026-08-14T00:00:00")
                .freightInvoiceTypeId(FreightInvoiceTypeRef.builder()
                        .freightInvoiceTypeId("DED-FLT").name("Dedicated Fleet Invoice").build())
                .paymentDueDate("2026-08-14T00:00:00")
                .frIvcValidationMessage(Collections.emptyList())
                .frIvcSupPurchaseOrder(Collections.emptyList())
                .autoCreated(false)
                .autoRejectInvoice(false)
                .blindInvoice(false)
                .orgId("3270")
                .countVoucheredCharges(0)
                .reasonCodeHistory(Collections.emptyList())
                .build();
    }
}
