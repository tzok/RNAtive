import { Anchor, Col, Row, Typography } from "antd";
import { useEffect, useState, useRef } from "react";
import flowDiff from "./assets/rnative-flow-diff.png";
import uploadScreen from "./assets/upload.png";
import fuzzyMode from "./assets/fuzzy-mode.png";
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
            <CustomTitle level={3}>Pdb files provided by user</CustomTitle>
            <Paragraph>
              To evaluate your own pdb files, please drop them in "Upload"
              button or click on the "Upload" button and select the .pdb files
              from your computer.
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
            <CustomTitle level={3}>MolProbity filter</CustomTitle>
            <Paragraph>
              When enabled, individual models undergo MolProbity evaluation,
              receiving ratings of 'good', 'caution', or 'warning' across four
              key metrics: clashscore, backbone conformation, bonds, and angles.
              Based on the selected filter, models failing to meet quality
              standards will be excluded from subsequent evaluation.
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
            <CustomTitle level={3}>Base pair analyzer</CustomTitle>
            <Paragraph>
              There are six built-in annotators for extracting nucleotide
              interactions available: MC-Annotate, BARNABA, RNAview, FR3D,
              BPnet, RNApolis
            </Paragraph>
            <CustomTitle level={3}>Consensus mode</CustomTitle>
            <Paragraph>
              Consensus mode specifies which categories of nucleotide
              interactions should be included when building the consensus
              secondary structure and comparing models. There are four consensus
              modes available: canonical, non-canonical, stacking and all.
            </Paragraph>
            <CustomTitle level={3}>Fuzzy Mode and Confidence level</CustomTitle>
            <Paragraph>
              In fuzzy mode, every nucleotide interaction within the specified
              consensus mode contributes to model ranking calculations based on
              how frequently it appears across models. When fuzzy mode is
              disabled, interaction frequency acts as a filtering mechanism,
              with INF computations only considering interactions that surpass
              minimum confidence thresholds. Disabling Fuzzy mode allows setting
              the prefered confidence level.
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
            <CustomTitle level={3}>Visualizers</CustomTitle>
            <Paragraph>
              There are four visualizers available: VARNA, RNApuzzler,
              PseudoViewer and R-Chie
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
            <ol>
              <li>Visualization by the selected visualizer</li>
              <li>Ranking of sent files</li>
              <li>
                Consensus details:
                <ol>
                  <li>Dot bracket of consensus molecule</li>
                  <li>Canonical base pairs</li>
                  <li>Non-canonical base pairs</li>
                  <li>Stackings for the consensus molecule</li>
                </ol>
              </li>
              <li>
                Results for each sent file:
                <ol>
                  <li>Dot bracket</li>
                  <li>Canonical pairs</li>
                  <li>Non-canonical pairs</li>
                  <li>Stackings</li>
                </ol>
              </li>
            </ol>
          </Typography>
        </div>
      </Col>
    </Row>
  );
};
export default Help;
