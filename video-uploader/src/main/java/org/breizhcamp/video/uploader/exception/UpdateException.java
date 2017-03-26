package org.breizhcamp.video.uploader.exception;

/**
 * Exception thrown when we can't notify user of a video update
 */
public class UpdateException extends Exception {

	public UpdateException() {
	}

	public UpdateException(String message) {
		super(message);
	}

	public UpdateException(String message, Throwable cause) {
		super(message, cause);
	}

	public UpdateException(Throwable cause) {
		super(cause);
	}
}
