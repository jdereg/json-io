package tools.jackson.databind;

/**
 * Test-only stand-in for Jackson 3.x's {@code tools.jackson.databind.PropertyNamingStrategies}.
 * <p>
 * json-io maps a Jackson naming strategy to its own {@code IoNaming.Strategy} by
 * {@code Class.getSimpleName()}, so the nested strategy classes only need to carry the same simple
 * names Jackson uses ({@code SnakeCaseStrategy}, {@code KebabCaseStrategy}, etc.). The package matches
 * Jackson 3.x so json-io's 3.x detection path resolves it. Only present on the test classpath.
 */
public final class PropertyNamingStrategies {
    private PropertyNamingStrategies() {
    }

    public static class SnakeCaseStrategy {
    }

    public static class UpperSnakeCaseStrategy {
    }

    public static class KebabCaseStrategy {
    }

    public static class UpperCamelCaseStrategy {
    }

    public static class LowerDotCaseStrategy {
    }

    public static class LowerCaseStrategy {
    }
}
