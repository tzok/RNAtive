import { Anchor, Col, Row, Typography } from "antd";
import { useEffect, useState, useRef } from "react";
import flowDiff from "./assets/rnative-flow-diff.png";
import uploadScreen from "./assets/upload.png";
import fuzzyMode from "./assets/fuzzy-mode.png";
import results1 from "./assets/results1.png";
import results2 from "./assets/results2a.png";
import results2b from "./assets/results2b.png";
import puzzles1 from "./assets/F1large.jpg";

import A1 from "./assets/a1.png";
import A2 from "./assets/a2.png";
import A3 from "./assets/a3.png";
import A4 from "./assets/a4.png";
import B1 from "./assets/B1.png";
import B3 from "./assets/B3.png";
import B4 from "./assets/B4.png";
const { Title, Paragraph, Text, Link } = Typography;

const Help = () => {
  const [headings, setHeadings] = useState([]);
  const [imgWidth, setImgWidth] = useState(0);
  const containerRef = useRef(null);
  useEffect(() => {
    // Get all Title elements
    const elements = document.querySelectorAll("h1, h2, h3, h4, h5");
    const items = Array.from(elements).map((element) => ({
      id: element.id,
      text: element.textContent,
      level: parseInt(element.tagName.charAt(1)),
    }));
    setHeadings(items);
    const updateWidth = () => {
      if (containerRef.current) {
        const containerRect = containerRef.current.getBoundingClientRect();
        const availableWidth = window.innerWidth - containerRect.right - 10; // 10px margin
        setImgWidth(Math.max(0, availableWidth)); // Ensure non-negative width
      }
    };

    updateWidth();
    window.addEventListener("resize", updateWidth);
    return () => window.removeEventListener("resize", updateWidth);
  }, []);

  const generateId = (text) => {
    return text
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/(^-|-$)+/g, "");
  };

  const CustomTitle = ({ children, level, ...props }) => (
    <Title id={generateId(children)} level={level} {...props}>
      {children}
    </Title>
  );

  return (
    <Row justify={"center"}>
      <Col span={20}>
        <div
          style={{
            display: "flex",
          }}
        >
          <div
            style={{
              marginRight: "20px",
            }}
          >
            <Anchor
              items={headings.map((heading) => ({
                key: heading.id,
                href: `#${heading.id}`,
                title: heading.text,
                style: { paddingLeft: `${(heading.level - 1) * 15}px` },
              }))}
            />
          </div>

          <Typography>
            <CustomTitle level={2}>What is RNAtive</CustomTitle>
            {/* <Paragraph>RNAtive is a system that allows its users to rank a set of different models of the same RNA structure to determine which ones are the most realistic. RNAtive will return both the evaluation of individual pdb files provided, and its proposition of the most realistic actual looks of the molecule.</Paragraph> */}
            <Paragraph>
              The discovery of RNA 3D structures contributes to the explanation
              of their biological functions, which is crucial in designing new
              drugs and therapeutic solutions. Unfortunately, 3D structures of
              the most biologically important RNAs are currently unknown. In
              recent years, many in silico methods devoted to RNA 3D structure
              prediction have been developed. Their accuracy usually depends on
              reliable scoring of 3D models/motifs in various stages of
              prediction, e.g., to assembly or to select the promising RNA 3D
              folds. Nowadays, wet-lab researchers are unable to reliably select
              the native-like RNA 3D structure among promising RNA 3D
              predictions. Even experts are not usually able to rank their 3D
              models ensuring high correlation with the ranking computed within
              the context of the reference RNA 3D structure.
            </Paragraph>
            <Paragraph>
              Therefore, there is a great need for a fully-automated method able
              to reliably rank RNA 3D predictions in terms of their practical
              usefulness through identification and comparison of conservative
              RNA 3D motifs covering various types of base pairing.{" "}
            </Paragraph>
            <Paragraph>
              In the protein field, a consensus-based quality assessment methods
              are often characterized by good performance as stated in CASP
              results. They are able to rank 3D predictions of a given protein
              assuming that conservative 3D motifs shared among the majority of
              analyzed 3D structures could be native-like.{" "}
            </Paragraph>
            <Paragraph>
              In contrast to that, there is no web-server tool providing a
              consensus-based ranking of RNA 3D models for a given RNA. RNAtive
              is designed to fill this gap. Our approach, first, identifies
              interaction networks for all considered RNA 3D models uploaded in
              PDB or PDBx/mmCIF format. Next, a consensus-driven RNA secondary
              structure that includes base pairs whose confidence score exceeds
              a threshold defined by the user, is constructed. Optionally you
              can upload reference RNA secondary structure (e.g., provided by
              Rfam) which base pairs will be treated as fixed in the resultant
              consensus. The base pair confidence score represents a percentage
              of RNA 3D predictions in which the particular base pair was
              observed. Finally, it constructs the Interaction Network
              Fidelity-based ranking of all considered RNA 3D models within the
              context of the consensus-driven RNA secondary structure. Moreover,
              the consensus and base-pair confidence values are presented. The
              former one in a textual and graphical way.{" "}
            </Paragraph>
            <Paragraph>
              The general idea of RNAtive is presented on the following diagram:
            </Paragraph>
            <img
              src={flowDiff}
              alt="rnative flow"
              style={{
                width: imgWidth > 0 ? `${imgWidth}px` : "auto",
                maxWidth: "100%", // Prevents overflow
                height: "auto",
                display: "inline-block",
              }}
            />
            <CustomTitle level={2}>How to use RNAtive</CustomTitle>
            <CustomTitle level={3}>Example mode</CustomTitle>
            <Paragraph>
              To test out the system without using any pdb files of your own,
              you can pick one of the options visible next to the "Load Example"
              section on the top of the form at the home page. This will
              automatically load example pdb files to be send to the server.
              Loaded files will be listed on screen, and each of them can be
              individually downloaded by pressing "Download" after hovering over
              it. To send it for evaluation, press the "Submit" button at the
              bottom of the form.{" "}
            </Paragraph>
            <CustomTitle level={3}>
              What files are used in the example mode
            </CustomTitle>
            <Paragraph>
              The <b>RNA-Puzzles 1</b> will use 13 pdb files provided by the
              competitors of RNA-Puzzles <sup>[1] </sup>
              for their Puzzle 1, that being a dimer molecule of the PDB ID{" "}
              <i>"3mei"</i>. The task provided to the participants, according to
              the RNA-Puzzles paper<sup>[a]</sup> was:
            </Paragraph>
            <Paragraph>
              <i>
                Predict the structure of the following sequence:
                <br></br>
                5'CCGCCGCGCCAUGCCUGUGGCGG-3',<br></br>
                knowing that the crystal structure shows a homodimer that
                contains two strands of the sequence that hybridize with blunt
                ends (C-G closing base pairs).
              </i>
            </Paragraph>
            <Paragraph>
              It is worth noting that 14 files were provided to RNA-Puzzles 1;
              however, the prediction provided by the Santalucia lab had a
              different sequence than the rest of the files. RNAtive requires
              all files to contain identical sequence, therefore this file had
              to be discarded.{" "}
            </Paragraph>
            <Paragraph>
              Structure visualization from the authors of the paper:
              <sup>[b]</sup>
              <img
                src={puzzles1}
                alt="RNA Puzzles structure"
                style={{
                  width: imgWidth > 0 ? `${imgWidth}px` : "auto",
                  maxWidth: "100%", // Prevents overflow
                  height: "auto",
                  display: "inline-block",
                }}
              />
            </Paragraph>
            <Paragraph>
              The <b>Decoys</b> will use nine pdb files from the <i>1a9nR</i>{" "}
              target in the Decoys<sup>[2] </sup>
              dataset.
            </Paragraph>
            <Paragraph>
              The <b>miRNA mir-663</b> will provide 10 models of miRNA mir-663
              from Rfam: RF00957<sup>[d] </sup>, predicted by RNAComposer
              <sup>[c] </sup> from a secondary structure predicted by
              centroidfold.<br></br>
              The following sequence was being predicted:
              <br></br>
              <i>
                CCUUCCGGCGUCCCAGGCGGGGCGCCGCGGGACCGCCCUCGUGUCUGUGGCGGUGGGAUCCCGCGGCCGUGUUUUCCUGGUGGCCCGGCC
              </i>{" "}
              <br></br>
            </Paragraph>
            <Paragraph>
              Both sets of files contain, therefore, a subset of molecules being
              attempts at generating a 3D model from sequence/secondary
              structure. Running those examples before using one's own data can
              be beneficial, for it may help with accustoming oneself to modes
              and parameters available, and with results generated that way.
            </Paragraph>
            <CustomTitle level={3}>Files provided by user</CustomTitle>
            <Paragraph>
              RNAtive accepts both <b>.pdb </b>and <b>.mmCIF</b> files. For a
              file to be evaluated, it must contain at least one RNA chain. It
              is important to note, that from a single .pdb/.mmCIF file
              containing multiple RNA chains, only the first chain shall be
              processed. Proteins, ligands, ions and water will be completely
              ignored during the analysis.<br></br>
              The minimum number of uploaded files for the evaluation to be
              performed is two. The maximum combined size of all provided files
              is 100MB.
            </Paragraph>
            <Paragraph>
              To evaluate your own files, please drop them in "Upload" button or
              click on the "Upload" button and select the .pdb/.mmCIF files from
              your computer.
            </Paragraph>
            <img
              src={uploadScreen}
              alt="Uploading example"
              style={{
                width: imgWidth > 0 ? `${imgWidth}px` : "auto",
                maxWidth: "300px", // Prevents overflow
                height: "auto",
                display: "inline-block",
              }}
            />
            <CustomTitle level={2}>Parameters</CustomTitle>
            <CustomTitle level={3}>Model quality filter</CustomTitle>
            <Paragraph>
              When enabled, individual models undergo MolProbity evaluation,
              receiving ratings of 'good', 'caution', or 'warning' across four
              key metrics: clashscore, backbone conformation, bonds, and angles.
              Based on the selected filter, models failing to meet quality
              standards will be excluded from subsequent evaluation.
            </Paragraph>
            <Paragraph>
              If the option "Clashscore filter" is selected, models that
              received a clashscore of "good" will be uploaded, but issues with
              angles and bonds shall be ignored. <br></br> Option "Strict
              filter" will only accept files that got a score of "good" in
              clashscore, bonds, and angles criteria.{" "}
            </Paragraph>
            <CustomTitle level={3}>Expected 2D structure</CustomTitle>
            <Paragraph>
              If desired, a dot-bracket form of the molecule can be given to the
              RNAtive to be treated as a reference for evaluation. The specified
              base pairs will be considered essential, and their absence in
              models will result in lower rankings. Accepted format looks like
              this:
            </Paragraph>
            <pre>
              ACGCCGCGCCAUGCCUGUGGCGG
              <br />
              (((((((((((((.(.(((((((
              <br />
              CCGCCGCGCCAUGCCUGUGGCGG
              <br />
              )))))))))))))..))))))))
            </pre>
            Individual strands can be also represented with {">"}strand_name for
            example: <br></br>
            <pre>
              {">"}strand_A
              <br />
              ACGCCGCGCCAUGCCUGUGGCGG
              <br />
              (((((((((((((.(.(((((((
              <br />
              {">"}strand_B
              <br />
              CCGCCGCGCCAUGCCUGUGGCGG
              <br />
              )))))))))))))..))))))))
            </pre>
            <Paragraph>
              The following syntax is used to describe the interactions within
              the expected structure:<br></br>
              <i>( )</i> - represents a pair of nucleotides.<br></br>
              <i>x</i> - represents a situation in which the nucleotide is left
              unpaired.<br></br>
              <i>.</i> - represents a lack of any restrictions for the given
              nucleotide.<br></br>
            </Paragraph>
            <CustomTitle level={3}>Base pair analyzer</CustomTitle>
            <Paragraph>
              There are six built-in annotators for extracting nucleotide
              interactions available: MC-Annotate, BARNABA, RNAview, FR3D,
              BPnet, RNApolis.
            </Paragraph>
            <CustomTitle level={3}>Consensus structure based on</CustomTitle>
            <Paragraph>
              "Consensus structure based on" specifies which categories of
              nucleotide interactions should be included when building the
              consensus secondary structure and comparing models. There are four
              consensus modes available: canonical base pairs, non-canonical
              base pairs, stacking interactions, and all interactions.
            </Paragraph>
            <CustomTitle level={3}>
              Conditionally weighted consensus and Confidence level
            </CustomTitle>
            {/* <Paragraph>
              In fuzzy mode, every nucleotide interaction within the specified
              consensus mode contributes to model ranking calculations based on
              how frequently it appears across models. When fuzzy mode is
              disabled, interaction frequency acts as a filtering mechanism,
              with INF computations only considering interactions that surpass
              minimum confidence thresholds. Disabling Fuzzy mode allows setting
              the prefered confidence level.
            </Paragraph> */}
            <Paragraph>
              With conditionally weighted consensus, every nucleotide
              interaction within the specified consensus mode contributes to
              model ranking calculations based on how frequently it appears
              across models. When that mode is disabled, interaction frequency
              acts as a filtering mechanism, with INF computations only
              considering interactions that surpass minimum confidence
              thresholds. Disabling conditionally weighted consensus allows
              setting the prefered confidence level.
            </Paragraph>
            <img
              src={fuzzyMode}
              alt="Fuzzy Mode and Confidence level"
              style={{
                width: imgWidth > 0 ? `${imgWidth}px` : "auto",
                maxWidth: "800px", // Prevents overflow
                height: "auto",
                display: "inline-block",
              }}
            />
            <CustomTitle level={3}>2D structure viewer</CustomTitle>
            <Paragraph>
              RNAtive will return a 2D visualization of the consensus structure.
              There are four visualizers available: VARNA, RNApuzzler,
              PseudoViewer and R-Chie.
            </Paragraph>
            <CustomTitle level={2}>
              Getting already submitted results
            </CustomTitle>
            <Paragraph>
              Once the data has been submitted, url of the site changes. The
              request id is added to it. To access answers from a different
              device, or to return to them later, one can simply copy that url
              and paste it later/elsewhere.
            </Paragraph>
            <CustomTitle level={2}>Results</CustomTitle>
            <Paragraph>RNAtive returns the following:</Paragraph>
            <img
              src={results1}
              alt="Results"
              style={{
                width: imgWidth > 0 ? `${imgWidth}px` : "auto",
                maxWidth: "800px", // Prevents overflow
                height: "auto",
                display: "inline-block",
              }}
            />
            <Paragraph>
              Ranking of sent files (as seen in an image above. Note a
              "download" button in the left bottom corner (marked in red in the
              image), that allows to download the entirety of the table as a txt
              file, as well as, visible in the bottom right of the image, a set
              of buttons allowing to scroll through the table, if it is longer)
            </Paragraph>
            <img
              src={results2}
              alt="Results"
              style={{
                width: imgWidth > 0 ? `${imgWidth}px` : "auto",
                maxWidth: "800px", // Prevents overflow
                height: "auto",
                display: "inline-block",
              }}
            />
            <Paragraph>
              Consensus structure: (section A in the image above)
            </Paragraph>
            <ol>
              <li>Visualization by the selected visualizer (A1)</li>
              <li>Dot bracket of consensus molecule (A1)</li>
              <li>Canonical base pairs (A2)</li>
              <li>Non-canonical base pairs (A3)</li>
              <li>Stackings for the consensus molecule(A4)</li>
            </ol>
            <img
              src={results2b}
              alt="Results"
              style={{
                width: imgWidth > 0 ? `${imgWidth}px` : "auto",
                maxWidth: "800px", // Prevents overflow
                height: "auto",
                display: "inline-block",
              }}
            />
            <Paragraph>
              Results for each sent file: (section B in the image above)
            </Paragraph>
            <ol>
              <li>Dot bracket (B1)</li>
              <li>Canonical pairs (B2)</li>
              <li>Non-canonical pairs (B3)</li>
              <li>Stackings (B4)</li>
            </ol>
            Note that file displayed can be switched using the list on the left
            (section C in the image above).
            <Paragraph>
              Sections A1-A4 and B1-B4 can be opened by clicking on the given
              tile, to reveal their contents, as shown in the images below.
              Note, that the content of the tables can be downloaded using the
              download button, in the same way as it was for the result table.
              To download the visualization, click on it with the left mouse
              button, and an image viewer will appear, then, clicking on the
              image with right mouse button shall open a menu with an option
              "save image as". Picking this option will allow the user to
              download the .svg file with the visualization. The dot-bracket
              structure is displayed in selectable format, therefore it can be
              simply selected with mouse and copied.
            </Paragraph>
            <img
              src={A1}
              alt="Results"
              style={{
                width: imgWidth > 0 ? `${imgWidth}px` : "auto",
                maxWidth: "800px", // Prevents overflow
                height: "auto",
                display: "inline-block",
              }}
            />
            <img
              src={A2}
              alt="Results"
              style={{
                width: imgWidth > 0 ? `${imgWidth}px` : "auto",
                maxWidth: "800px", // Prevents overflow
                height: "auto",
                display: "inline-block",
              }}
            />
            <img
              src={A3}
              alt="Results"
              style={{
                width: imgWidth > 0 ? `${imgWidth}px` : "auto",
                maxWidth: "800px", // Prevents overflow
                height: "auto",
                display: "inline-block",
              }}
            />
            <img
              src={A4}
              alt="Results"
              style={{
                width: imgWidth > 0 ? `${imgWidth}px` : "auto",
                maxWidth: "800px", // Prevents overflow
                height: "auto",
                display: "inline-block",
              }}
            />
            <img
              src={B1}
              alt="Results"
              style={{
                width: imgWidth > 0 ? `${imgWidth}px` : "auto",
                maxWidth: "800px", // Prevents overflow
                height: "auto",
                display: "inline-block",
              }}
            />
            <img
              src={B3}
              alt="Results"
              style={{
                width: imgWidth > 0 ? `${imgWidth}px` : "auto",
                maxWidth: "800px", // Prevents overflow
                height: "auto",
                display: "inline-block",
              }}
            />
            <img
              src={B4}
              alt="Results"
              style={{
                width: imgWidth > 0 ? `${imgWidth}px` : "auto",
                maxWidth: "800px", // Prevents overflow
                height: "auto",
                display: "inline-block",
              }}
            />
            <CustomTitle level={2}>References</CustomTitle>
            <Paragraph>
              RNAtive's functioning would not be possible without the
              integration of state-of-the-art methods and tools. References (in
              order of apperance on the main page):
              <ol>
                {/* <li>
                  <b>RNA-Puzzles: </b>M. Magnus, M. Antczak, T. Zok, J.
                  Wiedemann, P. Lukasiak, Y. Cao, J. M. Bujnicki, E. Westhof, M.
                  Szachniuk, Z. Miao (2020) RNA-Puzzles toolkit: A computational
                  resource of RNA 3D structure benchmark datasets, structure
                  manipulation, and evaluation tools.{" "}
                  <i>Nucleic Acids Research</i>, 48(2): 576-588
                  (doi:10.1093/nar/gkz1108).
                </li> */}
                <li>
                  <b>RNA-Puzzles: </b>M. Magnus et al.,{" "}
                  <i>Nucleic Acids Research</i>, 48(2): 576-588 (2020).
                </li>
                {/* <li>
                  <b>Decoys: </b>Emidio Capriotti, Tomas Norambuena, Marc A.
                  Marti-Renom, Francisco Melo, All-atom knowledge-based
                  potential for RNA structure prediction and assessment,
                  <i>Bioinformatics</i>, Volume 27, Issue 8, April 2011, Pages
                  1086–1093, https://doi.org/10.1093/bioinformatics/btr093.
                </li> */}
                <li>
                  <b>Decoys: </b>Emidio Capriotti et al., <i>Bioinformatics</i>,
                  Volume 27, Issue 8, Pages 1086–1093 (April 2011).
                </li>

                {/* <li>
                  <b>MolProbity:</b> MolProbity: all-atom contacts and structure
                  validation for proteins and nucleic acids I. W. Davis, A.
                  Leaver-Fay, V. B. Chen, J. N. Block, G. J. Kapral, X. Wang, L.
                  W. Murray, W. B. Arendall, III, J. Snoeyink, J. S. Richardson,
                  and D. C. Richardson. <i>Nucl. Acids Res.</i> 35: W375-W383
                  (2007).
                </li> */}
                <li>
                  <b>MolProbity:</b> I. W. Davis et al., <i>Nucl. Acids Res.</i>{" "}
                  35: W375-W383 (2007).
                </li>
                {/* <li>
                  <b>RNAPolis Annotator:</b> Szachniuk, Marta. "RNApolis:
                  Computational Platform for RNA Structure Analysis"{" "}
                  <i>Foundations of Computing and Decision Sciences</i>, vol.
                  44, no. 2, <i>Sciendo</i>, 2019, pp. 241-257.
                  https://doi.org/10.2478/fcds-2019-0012.
                </li> */}
                <li>
                  <b>RNAPolis Annotator:</b> Szachniuk, Marta.{" "}
                  <i>Foundations of Computing and Decision Sciences</i>, vol.
                  44, no. 2, <i>Sciendo</i>, pp. 241-257 (2019).
                </li>
                {/* <li>
                  <b>BPNet:</b> Roy, P., Bhattacharyya, D. Contact networks in
                  RNA: a structural bioinformatics study with a new tool.{" "}
                  <i>J Comput Aided Mol Des 36</i>, 131–140 (2022).
                  https://doi.org/10.1007/s10822-021-00438-x.
                </li> */}
                <li>
                  <b>BPNet:</b> Roy, P. et al., <i>J Comput Aided Mol Des 36</i>
                  , 131–140 (2022).
                </li>
                {/* <li>
                  <b>FR3D:</b> FR3D: Finding Local and Composite Recurrent
                  Structural Motifs in RNA 3D Structures, Michael Sarver; Craig
                  L. Zirbel; Jesse Stombaugh; Ali Mokdad; Neocles B. Leontis.
                  Journal of Mathematical Biology (2008) 56:215–252. <br></br>
                  WebFR3D – a server for finding, aligning and analyzing
                  recurrent RNA 3D motifs, Anton I. Petrov; Craig L. Zirbel;
                  Neocles B. Leontis. <i>Nucleic Acids Research</i>, 2011.
                </li> */}
                {/* https://www.bgsu.edu/research/rna/software/fr3d.html */}
                <li>
                  <b>FR3D:</b> Michael Sarver et al.,{" "}
                  <i>Journal of Mathematical Biology</i> 56:215–252 (2008).{" "}
                  <br></br>
                  Anton I. Petrov et al., WebFR3D – a server for finding,
                  aligning and analyzing recurrent RNA 3D motifs,{" "}
                  <i>Nucleic Acids Research</i>, (2011).
                </li>
                {/* <li>
                  <b>MC-Annotate:</b> Patrick Gendron, Sébastien Lemieux,
                  François Major, Quantitative analysis of nucleic acid
                  three-dimensional structures11Edited by I. Tinoco,{" "}
                  <i>Journal of Molecular Biology</i>, Volume 308, Issue 5,
                  2001, Pages 919-936, ISSN 0022-2836,
                  https://doi.org/10.1006/jmbi.2001.4626.
                  (https://www.sciencedirect.com/science/article/pii/S0022283601946261).
                </li> */}
                <li>
                  <b>MC-Annotate:</b> Patrick Gendron et al.,{" "}
                  <i>Journal of Molecular Biology</i>, Volume 308, Issue 5,
                  Pages 919-936, ISSN 0022-2836 (2001).
                </li>
                {/* <li>
                  <b>RNAView:</b> Yang, H., Jossinet, F., Leontis, N., Chen, L.,
                  Westbrook, J., Berman, H.M., Westhof, E. (2003). Tools for the
                  automatic identification and classification of RNA base pairs.
                  <i>Nucleic Acids Research</i> 31.13: 3450-3460.
                </li> */}
                <li>
                  <b>RNAView:</b> Yang H., et al.,
                  <i>Nucleic Acids Research</i> 31.13: 3450-3460 (2003).
                </li>
                {/* <li>
                  <b>barnaba:</b> Bottaro S, Bussi G, Pinamonti G, Reißer S,
                  Boomsma W, Lindorff-Larsen K. Barnaba: software for analysis
                  of nucleic acid structures and trajectories. <i>RNA</i>. 2019
                  Feb;25(2):219-231. doi: 10.1261/rna.067678.118. Epub 2018 Nov
                  12. PMID: 30420522; PMCID: PMC6348988.
                </li> */}
                <li>
                  <b>barnaba:</b> Bottaro S et al., <i>RNA</i>.
                  Feb;25(2):219-231 (2019).
                </li>
                <li>
                  {/* <b>VRNA:</b> VARNA: Interactive drawing and editing of the RNA
                secondary structure Kévin Darty, Alain Denise and Yann Ponty */}
                  <b>VRNA:</b> Kévin Darty et al.,
                  <i> Bioinformatics</i>, pp. 1974-1975, Vol. 25, no. 15,
                  (2009).
                </li>
                {/* <b>RNApuzzler: </b>Daniel Wiegreffe, Daniel Alexander, Peter F
                  Stadler, Dirk Zeckzer, RNApuzzler: efficient outerplanar
                  drawing of RNA-secondary structures, <i>Bioinformatics</i>,
                  Volume 35, Issue 8, April 2019, Pages 1342–1349,
                  https://doi.org/10.1093/bioinformatics/bty817. */}
                <li>
                  <b>RNApuzzler: </b>Daniel Wiegreffe et al.,{" "}
                  <i>Bioinformatics</i>, Volume 35, Issue 8, Pages 1342–1349
                  (April 2019).
                </li>
                {/* <b>PseudoViewer:</b> Byun Y, Han K. PseudoViewer: web
                  application and web service for visualizing RNA pseudoknots
                  and secondary structures. <i>Nucleic Acids Res.</i> 2006 Jul
                  1;34(Web Server issue):W416-22. doi: 10.1093/nar/gkl210. PMID:
                  16845039; PMCID: PMC1538805. */}
                <li>
                  <b>PseudoViewer:</b> Byun Y et al., <i>Nucleic Acids Res.</i>{" "}
                  34 (2006 Jul 1).
                </li>
                <li>
                  {/* <b>R-Chie: </b>
                  Volodymyr Tsybulskyi, Mohamed Mounir, Irmtraud M Meyer,
                  R-CHIE: a web server and R package for visualizing cis and
                  trans RNA–RNA, RNA–DNA and DNA–DNA interactions,{" "}
                  <i>Nucleic Acids Research</i>, Volume 48, Issue 18, 09 October
                  2020, Page e105, doi:10.1093/nar/gkaa708.
                  <br></br>
                  Daniel Lai, Jeff R. Proctor, Jing Yun A. Zhu, and Irmtraud M.
                  Meyer (2012) R-chie: a web server and R package for
                  visualizing RNA secondary structures.{" "}
                  <i>Nucleic Acids Research</i>, first published online March
                  19, 2012. doi:10.1093/nar/gks241. */}
                  <b>R-Chie: </b>
                  Volodymyr Tsybulskyi et al., <i>Nucleic Acids Research</i>,
                  Volume 48, Issue 18, Page e105 (09 October 2020).
                  <br></br>
                  Daniel Lai et al., R-chie: a web server and R package for
                  visualizing RNA secondary structures,{" "}
                  <i>Nucleic Acids Research</i>, (first published online March
                  19, 2012).
                </li>
              </ol>
            </Paragraph>
            <Paragraph>
              Additionally, there are following references used by our team:
              <ol type="a" style={{ listStyleType: "lower-alpha" }}>
                <li>
                  <a
                    href=" https://rnajournal.cshlp.org/content/18/4/610.full"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    https://rnajournal.cshlp.org/content/18/4/610.full
                  </a>
                </li>{" "}
                {/*cite this correctly*/}
                <li>
                  <a
                    href="https://rnajournal.cshlp.org/content/18/4/610/F1.expansion.html"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    https://rnajournal.cshlp.org/content/18/4/610/F1.expansion.html
                  </a>
                </li>
                <li>
                  Popenda, M. et al., 2012.
                  <i>Nucleic Acids Research</i>, 40(14), e112–e112.
                </li>
                <li>
                  Ontiveros-Palacios, N. et al., 2025{" "}
                  <i>Nucleic Acids Research</i>, 53(D1), D258–D267.
                </li>
              </ol>
            </Paragraph>
          </Typography>
        </div>
      </Col>
    </Row>
  );
};
export default Help;
