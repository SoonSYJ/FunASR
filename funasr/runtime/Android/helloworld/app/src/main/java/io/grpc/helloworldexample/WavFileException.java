package io.grpc.helloworldexample;

public class WavFileException extends Exception {
    private static final long serialVersionUID = 1L;

    public WavFileException(String message) {
        super(message);
    }
}
