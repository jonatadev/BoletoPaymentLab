package org.example.samplemicroservice.boleto.application;

public enum BoletoFailure {
    INVALID_REQUEST,
    BOLETO_NOT_FOUND,
    REVISION_NOT_FOUND,
    IDEMPOTENCY_CONFLICT,
    OPERATION_IN_PROGRESS,
    INVALID_STATE_TRANSITION,
    PDF_RENDER_FAILED;

    public FailureException exception() {
        return new FailureException(this);
    }

    public static final class FailureException extends RuntimeException {
        private final BoletoFailure failure;

        private FailureException(BoletoFailure failure) {
            super(failure.name());
            this.failure = failure;
        }

        public BoletoFailure failure() { return failure; }
    }
}
