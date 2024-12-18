import "./App.css";
import {Anchor, Typography} from "antd";
import {useEffect, useState} from "react";
const { Title, Paragraph, Text, Link } = Typography;

const Help = () => {
 const [headings, setHeadings] = useState([]);

 useEffect(() => {
    // Get all Title elements
    const elements = document.querySelectorAll('h1, h2, h3, h4, h5');
    const items = Array.from(elements).map((element) => ({
      id: element.id,
      text: element.textContent,
      level: parseInt(element.tagName.charAt(1))
    }));
    setHeadings(items);
  }, []);

  return (
    <>
      <div style={{ width: 200, position: 'sticky', top: 20 }}>
        <Anchor>
          {headings.map((heading) => (
            <Anchor.Link
              key={heading.id}
              href={`#${heading.id}`}
              title={heading.text}
              style={{ paddingLeft: `${(heading.level - 1) * 15}px` }}
            />
          ))}
        </Anchor>
      </div>

      <Typography>
      <Title>Help</Title>
      <Title level={2}>What is RNAtive</Title>
      <Paragraph>RNAtive is a system that allows its users to rank a set of different models of the same RNA structure to determine which ones are the most realistic. RNAtive will return both the evaluation of individual pdb files provided, and its proposition of the most realistic actual looks of the molecule.</Paragraph>
      <Title level={2}>How to use RNAtive</Title>
      <Title level={3}>Example mode</Title>
      <Paragraph>To test out the system without using any pdb files of your own, you can click the "Load Example" button on the top of the form at the home page. This will automatically load example pdb files to be send to the server. To send it for evaluation, press the "Send Data" button at the bottom of the form. The "Preview Files" button will download those example pdb files to your computer, so you can evaluate their formatting or do other</Paragraph>
      <Title level={3}>Pdb files provided by user</Title>
      <Paragraph>To evaluate your own pdb files, please drop them in the dropzone or click on the dropzone and select the .pdb files from your computer.</Paragraph>
      <Title level={2}>Parameters</Title>
      <Title level={3}>Consensus mode</Title>
        <Paragraph>There are four consensus modes available: canonical, non-canonical, stacking and all.</Paragraph>
      <Title level={3}>Annotators</Title>
        <Paragraph>There are six annotators available: MC-Annotate, BARNABA, RNAview, FR3D, BPnet, RNApolis</Paragraph>
      <Title level={3}>Visualizers</Title>
        <Paragraph>There are four visualizers available: VARNA, RNApuzzler, PseudoViewer and R-Chie</Paragraph>
      <Title level={3}>Confidence level</Title>
        <Paragraph>Confidence level tells how much is required by the RNAtive to label interaction as acceptable. Setting this value to 0 turns on the Fuzzy Mode which ...</Paragraph>
      <Title level={3}>Dot-bracket parameter</Title>
        <Paragraph>If desired, a dot-bracket form of the molecule can be given to the RNAtive to be treated as a reference for evaluation. Accepted format looks like this:</Paragraph>
        <pre>
          ACGCCGCGCCAUGCCUGUGGCGG<br/>
          (((((((((((((.(.(((((((<br/>
          CCGCCGCGCCAUGCCUGUGGCGG<br/>
          )))))))))))))..))))))))
        </pre>
        Individual strands can be also represented with {">"}strand_name for example: <br></br>
        <pre>
          {">"}strand_A<br/>
          ACGCCGCGCCAUGCCUGUGGCGG<br/>
          (((((((((((((.(.(((((((<br/>
          {">"}strand_B<br/>
          CCGCCGCGCCAUGCCUGUGGCGG<br/>
          )))))))))))))..))))))))
        </pre>
      <Title level={2}>Getting already submitted results</Title>
        <Paragraph>Once the data has been submitted, url of the site changes. The request id is added to it. To access answers from a different device, or to return to them later, one can simply copy that url and paste it later/elsewhere. There is also a "get already calculated results:" option at the bottom of the form. To access results from there, just paste the id (text after the "/" sign in the url). ID is also presented at the results page at "Your unique code:".</Paragraph>
      <Title level={2}>Results</Title>
        <Paragraph>RNAtive returns the following:</Paragraph>
        <ol>
          <li>Id code of this computation</li>
          <li>Visualization by the selected visualizer</li>
          <li>Cannonical pairs in consensus molecule</li>
          <li>Non cannonical pairs in consensus molecule</li>
          <li>Stackings for the consensus molecule</li>
          <li>Ranking of sent files</li>
          <li>Dot bracket of consensus molecule</li>
          <li>
            Results for each sent file:
            <ol>
              <li>Canonical pairs</li>
              <li>Non-canonical pairs</li>
              <li>Stackings</li>
              <li>Dot bracket</li>
            </ol>
          </li>
        </ol>
      </Typography>
    </>
  );
};
export default Help;
