package com.qa.api.pojo.invoice;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FreightInvoice {
    @JsonProperty("FreightInvoiceId")
    private String freightInvoiceId;
    @JsonProperty("CreatedSourceTypeId")
    private SourceType createdSourceTypeId;
    @JsonProperty("FreightInvoiceCharge")
    private List<FreightInvoiceCharge> freightInvoiceCharge;
    @JsonProperty("BillingCycleStartDate")
    private String billingCycleStartDate;
    @JsonProperty("CurrencyCode")
    private String currencyCode;
    @JsonProperty("UpdatedBy")
    private String updatedBy;
    @JsonProperty("TerminalId")
    private String terminalId;
    @JsonProperty("RequestedPaymentMethodId")
    private PaymentMethodRef requestedPaymentMethodId;
    @JsonProperty("PayeeId")
    private String payeeId;
    @JsonProperty("ReceivedDate")
    private String receivedDate;
    @JsonProperty("StatusId")
    private FreightInvoiceStatusRef statusId;
    @JsonProperty("CountNotApprovedCharges")
    private Integer countNotApprovedCharges;
    @JsonProperty("PaymentDueReferenceDate")
    private PaymentDueRef paymentDueReferenceDate;
    @JsonProperty("BillingDate")
    private String billingDate;
    @JsonProperty("FreightInvoiceTypeId")
    private FreightInvoiceTypeRef freightInvoiceTypeId;
    @JsonProperty("PaymentDueDate")
    private String paymentDueDate;
    @JsonProperty("FrIvcValidationMessage")
    private List<Object> frIvcValidationMessage;
    @JsonProperty("FrIvcSupPurchaseOrder")
    private List<Object> frIvcSupPurchaseOrder;
    @JsonProperty("AutoCreated")
    private Boolean autoCreated;
    @JsonProperty("AutoRejectInvoice")
    private Boolean autoRejectInvoice;
    @JsonProperty("BlindInvoice")
    private Boolean blindInvoice;
    @JsonProperty("OrgId")
    private String orgId;
    @JsonProperty("CountVoucheredCharges")
    private Integer countVoucheredCharges;
    @JsonProperty("ReasonCodeHistory")
    private List<Object> reasonCodeHistory;
}
