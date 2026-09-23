package com.ftxeven.aircore.module.variables;

public sealed interface VariableWrite permits VariableWrite.Add, VariableWrite.Subtract, VariableWrite.Set, VariableWrite.Reset, VariableWrite.Toggle {

    record Add(double amount) implements VariableWrite {}

    record Subtract(double amount) implements VariableWrite {}

    record Set(String value) implements VariableWrite {}

    record Reset() implements VariableWrite {}

    record Toggle() implements VariableWrite {}
}