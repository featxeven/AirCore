package com.ftxeven.aircore.module.variables;

import com.ftxeven.aircore.core.condition.ExprEvaluator;
import com.ftxeven.aircore.module.variables.VariableCatalog.Spec;
import com.ftxeven.aircore.module.variables.VariablesConfig.Constraints;
import org.jetbrains.annotations.Nullable;

// Turns a (variable, write) pair into a Plan. Whether a write is acceptable never depends on the current
// value, so the plan is decided up front (synchronously)
final class VariableMutator {

    // value == null clears the stored value so reads fall back to the config default
    record Change(@Nullable String value, @Nullable Double delta) {}

    @FunctionalInterface
    interface Operation {
        Change apply(String current);
    }

    record Plan(@Nullable Operation operation, @Nullable String rejection) {
        boolean accepted() {
            return operation != null;
        }
    }

    private static final Plan RESET = new Plan(current -> new Change(null, null), null);

    private VariableMutator() {
    }

    static Plan plan(Spec spec, VariableWrite write) {
        return switch (spec.type()) {
            case BOOLEAN -> planBoolean(write);
            case STRING -> planString(spec, write);
            case INTEGER -> planNumber(spec, write, true);
            case DOUBLE -> planNumber(spec, write, false);
        };
    }

    private static Plan planBoolean(VariableWrite write) {
        return switch (write) {
            case VariableWrite.Set set -> {
                String value = String.valueOf(Boolean.parseBoolean(set.value()));
                yield accept(current -> new Change(value, null));
            }
            case VariableWrite.Toggle ignored -> accept(current -> new Change(String.valueOf(!Boolean.parseBoolean(current)), null));
            case VariableWrite.Reset ignored -> RESET;
            case VariableWrite.Add ignored -> reject("'add' is not valid on a boolean variable");
            case VariableWrite.Subtract ignored -> reject("'subtract' is not valid on a boolean variable");
        };
    }

    private static Plan planString(Spec spec, VariableWrite write) {
        return switch (write) {
            case VariableWrite.Set set -> {
                Constraints c = spec.constraints();
                String value = set.value();
                if (!c.allowedValues().isEmpty() && !c.allowedValues().contains(value)) {
                    yield reject("'" + value + "' is not one of the allowed values");
                }
                String clamped = c.maxLength() >= 0 && value.length() > c.maxLength() ? value.substring(0, c.maxLength()) : value;
                yield accept(current -> new Change(clamped, null));
            }
            case VariableWrite.Reset ignored -> RESET;
            case VariableWrite.Add ignored -> reject("'add' is not valid on a string variable");
            case VariableWrite.Subtract ignored -> reject("'subtract' is not valid on a string variable");
            case VariableWrite.Toggle ignored -> reject("'toggle' is not valid on a string variable");
        };
    }

    private static Plan planNumber(Spec spec, VariableWrite write, boolean integer) {
        Constraints c = spec.constraints();
        return switch (write) {
            case VariableWrite.Add add -> !Double.isFinite(add.amount())
                    ? reject("'add' amount must be a finite number")
                    : accept(current -> new Change(format(clamp(base(current, spec) + add.amount(), c), integer), add.amount()));
            case VariableWrite.Subtract subtract -> !Double.isFinite(subtract.amount())
                    ? reject("'subtract' amount must be a finite number")
                    : accept(current -> new Change(format(clamp(base(current, spec) - subtract.amount(), c), integer), -subtract.amount()));
            case VariableWrite.Set set -> {
                double requested = ExprEvaluator.parseNumber(set.value());
                if (!Double.isFinite(requested)) {
                    yield reject("'" + set.value() + "' is not a valid number");
                }
                String value = format(clamp(requested, c), integer);
                yield accept(current -> new Change(value, null));
            }
            case VariableWrite.Reset ignored -> RESET;
            case VariableWrite.Toggle ignored -> reject("'toggle' is not valid on a " + (integer ? "integer" : "double") + " variable");
        };
    }

    private static double base(String current, Spec spec) {
        double value = ExprEvaluator.parseNumber(current);
        return Double.isNaN(value) ? spec.defaultNumber() : value;
    }

    private static double clamp(double value, Constraints c) {
        double result = value;
        if (c.min() != null) result = Math.max(result, c.min());
        if (c.max() != null) result = Math.min(result, c.max());
        return result;
    }

    private static String format(double value, boolean integer) {
        return integer ? Long.toString((long) value) : ExprEvaluator.formatNumber(value);
    }

    private static Plan accept(Operation operation) {
        return new Plan(operation, null);
    }

    private static Plan reject(String reason) {
        return new Plan(null, reason);
    }
}