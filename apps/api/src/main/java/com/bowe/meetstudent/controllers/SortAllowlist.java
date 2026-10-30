package com.bowe.meetstudent.controllers;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.data.util.TypeInformation;

import java.util.List;
import java.util.Set;

/**
 * The {@code ?sort=} properties a public list accepts. Spring Data sorts by any path it can resolve, including one
 * through an association ({@code schoolRates.userEntity.email}, also spelled {@code schoolRatesUserEntityEmail} or
 * with underscores): the response never shows that column, but the order of the rows reveals it, one comparison at a
 * time. A property off the list answers exactly like an unknown one: 400 "Unknown sort property".
 */
record SortAllowlist(Class<?> domainType, Set<String> properties) {

    static SortAllowlist of(Class<?> domainType, String... properties) {
        return new SortAllowlist(domainType, Set.of(properties));
    }

    void check(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!properties.contains(order.getProperty())) {
                throw new PropertyReferenceException(order.getProperty(), TypeInformation.of(domainType), List.of());
            }
        }
    }
}
