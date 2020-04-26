package io.pivotal.quotes.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Symbol {

    private String symbol;
    private String name;
    private String exchange;

}
