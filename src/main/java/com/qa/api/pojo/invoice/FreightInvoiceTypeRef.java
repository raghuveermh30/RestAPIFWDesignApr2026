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
public class FreightInvoiceTypeRef {
    @JsonProperty("FreightInvoiceTypeId")
    private String freightInvoiceTypeId;
    @JsonProperty("Name")
    private String name;
}
