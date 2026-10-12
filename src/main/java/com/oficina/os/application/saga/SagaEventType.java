package com.oficina.os.application.saga;

public final class SagaEventType {

	public static final String DIAGNOSTIC_STARTED = "diagnostic.started.v1";
	public static final String DIAGNOSTIC_COMPLETED = "diagnostic.completed.v1";
	public static final String DIAGNOSTIC_FAILED = "diagnostic.failed.v1";
	public static final String QUOTE_CREATED = "quote.created.v1";
	public static final String QUOTE_CREATION_FAILED = "quote.creation-failed.v1";
	public static final String QUOTE_APPROVED = "quote.approved.v1";
	public static final String QUOTE_REJECTED = "quote.rejected.v1";
	public static final String REPAIR_STARTED = "repair.started.v1";
	public static final String REPAIR_COMPLETED = "repair.completed.v1";
	public static final String REPAIR_FAILED = "repair.failed.v1";
	public static final String REPAIR_CANCELLED = "repair.cancelled.v1";
	public static final String REPAIR_CANCELLATION_FAILED = "repair.cancellation-failed.v1";
	public static final String PAYMENT_CONFIRMED = "payment.confirmed.v1";
	public static final String PAYMENT_FAILED = "payment.failed.v1";

	public static final String DIAGNOSTIC_START_COMMAND = "diagnostic.start.v1";
	public static final String QUOTE_CREATE_COMMAND = "quote.create.v1";
	public static final String REPAIR_START_COMMAND = "repair.start.v1";
	public static final String REPAIR_CANCEL_COMMAND = "repair.cancel.v1";
	public static final String PAYMENT_REQUEST_COMMAND = "payment.request.v1";

	private SagaEventType() {
	}
}
