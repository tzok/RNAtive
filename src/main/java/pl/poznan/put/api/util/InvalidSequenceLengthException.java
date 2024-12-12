package pl.poznan.put.api.util;

public class InvalidSequenceLengthException extends RuntimeException {
  public InvalidSequenceLengthException(int expectedLength, int actualLength) {
    super(
        String.format(
            "Sequence length mismatch: expected %d residues, but got %d",
            expectedLength, actualLength));
  }
}
