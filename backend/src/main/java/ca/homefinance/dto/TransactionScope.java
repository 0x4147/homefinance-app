package ca.homefinance.dto;

/** Which transactions a view is about. Personal views (one person's own finances) will be added alongside these. */
public enum TransactionScope {
    /** Everything, including rows left out of the balance, so they can be reviewed. */
    ALL,
    /** The true shared finances: counted in the balance and split between both people. */
    SHARED;

    public static TransactionScope parse(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid scope: " + value);
        }
    }
}
