package com.ftxeven.aircore.module.chat.filter;

public sealed interface FilterVerdict {

    record Allow(String body) implements FilterVerdict {}

    record Block(String langKey) implements FilterVerdict {}

    static FilterVerdict allow(String body) {
        return new Allow(body);
    }

    static FilterVerdict block(String langKey) {
        return new Block(langKey);
    }
}