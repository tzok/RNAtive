package pl.poznan.put.pdb.analysis;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

public class PdbModelDeserializer extends JsonDeserializer<PdbModel> {
  @Override
  public PdbModel deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    // For now, return null as we're not actually deserializing from JSON
    // The PdbModel is created from PDB file content via PdbParser
    return null;
  }
}
