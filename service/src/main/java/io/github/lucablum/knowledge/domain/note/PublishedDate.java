package io.github.lucablum.knowledge.domain.note;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jmolecules.ddd.annotation.ValueObject;

/** When the work behind a Source Note was published: a year, a year and month, or a full date, in ISO form. */
@ValueObject
public record PublishedDate(String value) {

    private static final Pattern YEAR = Pattern.compile("\\d{4}");
    private static final Pattern YEAR_MONTH = Pattern.compile("\\d{4}-\\d{2}");
    private static final Pattern FULL_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    public PublishedDate {
        Objects.requireNonNull(value);
        if (!isValid(value)) {
            throw new IllegalArgumentException("Not a year, year-month or date: " + value);
        }
    }

    /** Empty unless {@code text} is {@code 2021}, {@code 2021-03} or {@code 2021-03-14}. */
    public static Optional<PublishedDate> parse(String text) {
        return isValid(text) ? Optional.of(new PublishedDate(text)) : Optional.empty();
    }

    private static boolean isValid(String text) {
        try {
            if (YEAR.matcher(text).matches()) {
                return true;
            }
            if (YEAR_MONTH.matcher(text).matches()) {
                YearMonth.parse(text);
                return true;
            }
            if (FULL_DATE.matcher(text).matches()) {
                LocalDate.parse(text);
                return true;
            }
            return false;
        } catch (DateTimeException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
