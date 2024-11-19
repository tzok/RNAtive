package pl.poznan.put.api.exception;

public class VisualizationException extends Exception {

    public VisualizationException(String message) {
        super(message);
    }

    public VisualizationException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
