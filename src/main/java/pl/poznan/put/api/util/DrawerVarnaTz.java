package pl.poznan.put.api.util;

import fr.orsay.lri.varna.exceptions.ExceptionFileFormatOrSyntax;
import fr.orsay.lri.varna.exceptions.ExceptionNAViewAlgorithm;
import fr.orsay.lri.varna.exceptions.ExceptionUnmatchedClosingParentheses;
import fr.orsay.lri.varna.exceptions.ExceptionWritingForbidden;
import fr.orsay.lri.varna.models.VARNAConfig;
import fr.orsay.lri.varna.models.rna.ModeleBP;
import fr.orsay.lri.varna.models.rna.RNA;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.svg.SVGDocument;
import pl.poznan.put.api.exception.VisualizationException;
import pl.poznan.put.notation.LeontisWesthof;
import pl.poznan.put.notation.NucleobaseEdge;
import pl.poznan.put.notation.Stericity;
import pl.poznan.put.pdb.PdbResidueIdentifier;
import pl.poznan.put.pdb.analysis.PdbModel;
import pl.poznan.put.structure.BasePair;
import pl.poznan.put.structure.ClassifiedBasePair;
import pl.poznan.put.structure.DotBracketSymbol;
import pl.poznan.put.structure.formats.DotBracket;
import pl.poznan.put.structure.formats.DotBracketFromPdb;
import pl.poznan.put.structure.formats.Strand;
import pl.poznan.put.utility.svg.SVGHelper;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of SecondaryStructureDrawer using VARNA-based algorithm.
 * <p>
 * The implementation has been taken from RNApdbee 2.0.
 */
@Component
public class DrawerVarnaTz {

    private static final Logger LOGGER = LoggerFactory.getLogger(DrawerVarnaTz.class);

    private final Color missingOutlineColor = new Color(222, 45, 38);

    public final SVGDocument drawSecondaryStructure(final DotBracket dotBracket)
            throws VisualizationException {
        final List<SVGDocument> svgs = new ArrayList<>();
        for (final DotBracket combinedStrand : dotBracket.combineStrands()) {
            try {
                svgs.add(drawStructure(combinedStrand));
            } catch (IOException e) {
                throw new VisualizationException("Internal error has occurred", e);
            }
        }
        return SVGHelper.merge(svgs);
    }

    public final SVGDocument drawSecondaryStructure(
            final DotBracketFromPdb dotBracket,
            final PdbModel structureModel,
            final List<? extends ClassifiedBasePair> nonCanonicalBasePairs)
            throws VisualizationException {
        final List<ClassifiedBasePair> availableNonCanonical =
                nonCanonicalBasePairs.stream()
                        .filter(ClassifiedBasePair::isPairing)
                        .collect(Collectors.toList());
        final List<DotBracketFromPdb> combinedStrands =
                dotBracket.combineStrands(availableNonCanonical);

        // prepare maps to distinguish non-canonical base pairs in different strands
        final Map<PdbResidueIdentifier, DotBracket> residueToStrand = new HashMap<>();
        final Map<PdbResidueIdentifier, Integer> residueToIndex = new HashMap<>();
        for (final DotBracketFromPdb combinedStrand : combinedStrands) {
            int i = 0;
            for (final DotBracketSymbol symbol : combinedStrand.symbols()) {
                final PdbResidueIdentifier identifier =
                        PdbResidueIdentifier.from(combinedStrand.identifier(symbol));
                residueToStrand.put(identifier, combinedStrand);
                residueToIndex.put(identifier, i);
                i += 1;
            }
        }

        final List<SVGDocument> svgs = new ArrayList<>();
        for (final DotBracket combinedStrand : combinedStrands) {
            try {
                svgs.add(drawStructure(combinedStrand, availableNonCanonical, residueToStrand, residueToIndex));
            } catch (IOException e) {
                throw new VisualizationException("Internal error has occurred", e);
            }
        }
        return SVGHelper.merge(svgs);
    }

    private void addNonCanonical(
            final DotBracket combinedStrand,
            final Iterable<? extends ClassifiedBasePair> nonCanonicalBasePairs,
            final Map<PdbResidueIdentifier, DotBracket> residueToStrand,
            final Map<PdbResidueIdentifier, Integer> residueToIndex,
            final RNA rna) {
        // add non-canonical base pairs if any
        for (final ClassifiedBasePair nonCanonicalBasePair : nonCanonicalBasePairs) {
            final BasePair basePair = nonCanonicalBasePair.basePair();
            final PdbResidueIdentifier left = PdbResidueIdentifier.from(basePair.left());
            final PdbResidueIdentifier right = PdbResidueIdentifier.from(basePair.right());

            if (!residueToStrand.containsKey(left)) {
                LOGGER.error(String.format("Mapping of residues and dot-bracket symbols failed: %s", left));
                continue;
            }
            if (!residueToStrand.containsKey(right)) {
                LOGGER.error(String.format("Mapping of residues and dot-bracket symbols failed: %s", right));
                continue;
            }
            if (!residueToStrand.get(left).equals(combinedStrand)
                    || !residueToStrand.get(right).equals(combinedStrand)) {
                continue;
            }

            final int i = residueToIndex.get(left);
            final int j = residueToIndex.get(right);
            final LeontisWesthof leontisWesthof = nonCanonicalBasePair.leontisWesthof();

            if (leontisWesthof == LeontisWesthof.UNKNOWN) {
                rna.addBPAux(i, j, 0);
            } else {
                final ModeleBP.Edge edge5 = translateEdgeEnum(leontisWesthof.edge5());
                final ModeleBP.Edge edge3 = translateEdgeEnum(leontisWesthof.edge3());
                final ModeleBP.Stericity ster = translateStericityEnum(leontisWesthof.stericity());
                rna.addBPAux(i, j, edge5, edge3, ster);
            }
        }
    }

    private String replaceMissingWithDash(
            final DotBracket combinedStrand, final CharSequence structure) {
        // change sequence characters to dash '-' for missing residues
        final char[] sequence = combinedStrand.sequence(true).toCharArray();
        for (int i = 0; i < structure.length(); i++) {
            if (structure.charAt(i) == '-') {
                sequence[i] = '-';
            }
        }
        return new String(sequence);
    }

    private void renumberBases(final DotBracket combinedStrand, final RNA rna) {
        // renumber bases if there are multiple strands involved
        int i = 0;
        for (final Strand strand : combinedStrand.strands()) {
            for (int j = 0; j < strand.length(); i++, j++) {
                rna.getBaseAt(i).setBaseNumber(j + 1);
            }
        }
    }

    private ModeleBP.Stericity translateStericityEnum(final Stericity stericity) {
        switch (stericity) {
            case CIS:
                return ModeleBP.Stericity.CIS;
            case TRANS:
                return ModeleBP.Stericity.TRANS;
            case UNKNOWN:
        }
        throw new IllegalArgumentException("Invalid argument: " + stericity);
    }

    private ModeleBP.Edge translateEdgeEnum(final NucleobaseEdge edge) {
        switch (edge) {
            case WATSON_CRICK:
                return ModeleBP.Edge.WC;
            case HOOGSTEEN:
                return ModeleBP.Edge.HOOGSTEEN;
            case SUGAR:
                return ModeleBP.Edge.SUGAR;
            case UNKNOWN:
        }
        throw new IllegalArgumentException("Invalid argument: " + edge);
    }

    private SVGDocument drawStructure(final DotBracket combinedStrand)
            throws IOException, VisualizationException {
        return drawStructure(
                combinedStrand, Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
    }

    private SVGDocument drawStructure(
            final DotBracket combinedStrand,
            final Iterable<? extends ClassifiedBasePair> nonCanonicalBasePairs,
            final Map<PdbResidueIdentifier, DotBracket> residueToStrand,
            final Map<PdbResidueIdentifier, Integer> residueToIndex)
            throws IOException, VisualizationException {
        final File tempFile = File.createTempFile("varna", ".svg");

        try {
            final VARNAConfig config = new VARNAConfig();
            config._bondColor = Color.DARK_GRAY.brighter();
            config._colorDashBases = true;
            config._dashBasesColor = missingOutlineColor;
            config._drawAlternativeLW = true;

            final String structure = combinedStrand.structure(true);
            final String sequence = replaceMissingWithDash(combinedStrand, structure);

            final RNA rna = new RNA(true);
            rna.setRNA(sequence, structure);

            renumberBases(combinedStrand, rna);
            addNonCanonical(combinedStrand, nonCanonicalBasePairs, residueToStrand, residueToIndex, rna);

            rna.drawRNANAView(config);
            rna.saveRNASVG(tempFile.getAbsolutePath(), config);
            return SVGHelper.fromFile(tempFile);
        } catch (final ExceptionUnmatchedClosingParentheses
                       | ExceptionWritingForbidden
                       | ExceptionNAViewAlgorithm
                       | ExceptionFileFormatOrSyntax e) {
            throw new VisualizationException("Failed to draw secondary structure with VARNA", e);
        } finally {
            FileUtils.forceDelete(tempFile);
        }
    }
}