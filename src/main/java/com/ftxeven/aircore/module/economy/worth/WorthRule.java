package com.ftxeven.aircore.module.economy.worth;

import java.util.Set;

public record WorthRule(MatchRules match, double worth, Set<String> categories) {

    public WorthRule {
        categories = Set.copyOf(categories);
    }
}