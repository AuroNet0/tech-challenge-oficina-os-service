package com.oficina.os.application.port.out;

public class MessagePublishException extends RuntimeException {

	public MessagePublishException(String message) {
		super(message);
	}

	public MessagePublishException(String message, Throwable cause) {
		super(message, cause);
	}
}
