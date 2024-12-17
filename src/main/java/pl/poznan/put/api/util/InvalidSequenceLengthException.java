package pl.poznan.put.api.util;

public class InvalidSequenceLengthException extends RuntimeException {
  public InvalidSequenceLengthException(int expectedLength, int actualLength) {
    super(
        String.format(
            "Sequence length mismatch: reference sequence has %d residues, but the 3D model has %d",
            expectedLength, actualLength));
  }
}
