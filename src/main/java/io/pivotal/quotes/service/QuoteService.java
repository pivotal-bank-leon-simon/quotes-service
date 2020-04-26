package io.pivotal.quotes.service;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import io.pivotal.quotes.domain.*;
import io.pivotal.quotes.exception.SymbolNotFoundException;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

/**
 * A service to retrieve Company and Quote information.
 *
 * @author David Ferreira Pinto
 */
@Service
@RefreshScope
@Slf4j
public class QuoteService {

    @Value("${pivotal.quotes.quote_url}")
    protected String quote_url;

    @Value("${pivotal.quotes.quotes_url}")
    protected String quotes_url;

    @Value("${pivotal.quotes.symbols_url}")
    protected String sybmbolsUrl;

    public static final String FMT = "json";

    static final List<CompanyInfo> ALL_COMPANIES = new ArrayList<>();
    /*
     * cannot autowire as don't want ribbon here.
     */
    private RestTemplate restTemplate = new RestTemplate();

    /**
     * Get all the sybmols and populate the ALL_COMPANIES collection
     */
    @PostConstruct
    public void init() {
        log.info("Initilizing symbols into memory");
        ALL_COMPANIES.addAll(
                restTemplate.exchange(sybmbolsUrl, HttpMethod.GET,
                        null, new ParameterizedTypeReference<List<Symbol>>() {
                        }).getBody()
                        .stream()
                        .map(s -> new CompanyInfo(s.getSymbol(), s.getName(), s.getExchange()))
                        .collect(toList())
        );
        log.info("Finished initializing symbols into memory , loaded:" + ALL_COMPANIES.size());
    }


    /**
     * Retrieves an up to date quote for the given symbol.
     *
     * @param symbol The symbol to retrieve the quote for.
     * @return The quote object or null if not found.
     * @throws SymbolNotFoundException
     */
    @HystrixCommand(fallbackMethod = "getQuoteFallback")
    public Quote getQuote(String symbol) throws SymbolNotFoundException {

        log.debug("QuoteService.getQuote: retrieving quote for: " + symbol);

        Map<String, String> params = new HashMap<String, String>();
        params.put("symbol", symbol);

        IexQuote quote = restTemplate.getForObject(quote_url, IexQuote.class, params);

        if (quote == null || quote.getSymbol() == null) {
            throw new SymbolNotFoundException("Symbol not found: " + symbol);
        }

        log.debug("QuoteService.getQuote: retrieved quote: " + quote);


        return QuoteMapper.INSTANCE.mapFromIexQuote(quote);

    }

    @SuppressWarnings("unused")
    private Quote getQuoteFallback(String symbol)
            throws SymbolNotFoundException {
        log.debug("QuoteService.getQuoteFallback: circuit opened for symbol: "
                + symbol);
        Quote quote = new Quote();
        quote.setSymbol(symbol);
        quote.setStatus("FAILED");
        return quote;
    }

    /**
     * Retrieves a list of CompanyInfor objects. Given the name parameters, the
     * return list will contain objects that match the search both on company
     * name as well as symbol.
     *
     * @param name The search parameter for company name or symbol.
     * @return The list of company information.
     */

    public List<CompanyInfo> getCompanyInfo(String name) {
        List<CompanyInfo> matching = ALL_COMPANIES
                .stream()
                .filter(c -> c.getName().toLowerCase().contains(name.toLowerCase()))
                .collect(toList());
        if (matching.size() > 10) {
            matching = matching.subList(0,10);
        }
        return matching;
    }

    /**
     * Retrieve multiple quotes at once.
     *
     * @param symbols comma delimeted list of symbols.
     * @return a list of quotes.
     */
    public List<Quote> getQuotes(String symbols) {
        log.debug("retrieving multiple quotes for: " + symbols);

        IexBatchQuote batchQuotes = restTemplate.getForObject(quotes_url, IexBatchQuote.class, symbols);

        log.debug("Got response: " + batchQuotes);
        final List<Quote> quotes = new ArrayList<>();

        Arrays.asList(symbols.split(",")).forEach(symbol -> {
            if (batchQuotes.containsKey(symbol)) {
                quotes.add(QuoteMapper.INSTANCE.mapFromIexQuote(batchQuotes.get(symbol).get("quote")));
            } else {
                log.warn("Quote could not be found for the following symbol: " + symbol);
                Quote quote = new Quote();
                quote.setSymbol(symbol);
                quote.setStatus("FAILED");
                quotes.add(quote);
            }
        });

        return quotes;
    }


    @SuppressWarnings("unused")
    private List<CompanyInfo> getCompanyInfoFallback(String symbol)
            throws SymbolNotFoundException {
        log.debug("QuoteService.getCompanyInfoFallback: circuit opened for symbol: "
                + symbol);
        List<CompanyInfo> companies = new ArrayList<>();
        return companies;
    }
}
