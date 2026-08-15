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
public class FreightChargeStatusRef {
    @JsonProperty("FreightChargeStatusId")
    private String freightChargeStatusId;
    @JsonProperty("Name")
    private String name;
}
