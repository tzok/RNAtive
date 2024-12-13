package pl.poznan.put.api.util;

public class InvalidSequenceException extends RuntimeException {
  public InvalidSequenceException(String expectedSequence, String actualSequence) {
    super(
        String.format(
            "Sequence mismatch: reference sequence is '%s', but the 3D model has '%s'",
            actualSequence, expectedSequence));
  }
}
