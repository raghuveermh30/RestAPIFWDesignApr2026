package com.qa.api.pojo.invoice;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FreightInvoiceCharge {
    @JsonProperty("FreightInvoiceId")
    private String freightInvoiceId;
    @JsonProperty("CurrencyCode")
    private String currencyCode;
    @JsonProperty("IsBookingAccessorial")
    private Boolean isBookingAccessorial;
    @JsonProperty("IsDedicatedFleetAssetCharge")
    private Boolean isDedicatedFleetAssetCharge;
    @JsonProperty("OrgId")
    private String orgId;
    @JsonProperty("ChargeDefinitionId")
    private String chargeDefinitionId;
    @JsonProperty("AddToInvoice")
    private Boolean addToInvoice;
    @JsonProperty("StatusId")
    private FreightChargeStatusRef statusId;
    @JsonProperty("InvoicedAmount")
    private Double invoicedAmount;
    @JsonProperty("AmountToBeApproved")
    private Double amountToBeApproved;
}
