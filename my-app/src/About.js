import { Card, Col, Row, Typography } from "antd";

const { Paragraph } = Typography;

const About = () => {
  return (
    <Row justify={"center"}>
      <Col span={20} style={{ maxWidth: "800px", margin: "0 auto" }}>
        <Card title="About" style={{ marginBottom: "24px" }}>
          <Typography>
            <Paragraph>
              RNAtive is a web-based tool designed to help researchers compare
              multiple RNA 3D structure models of the same sequence and identify
              the most reliable features - such as base pairs and stacking
              interactions - even when no experimental reference structure is
              available.
            </Paragraph>
            <Paragraph>
              The system works in several steps. First, it extracts interaction
              networks from each submitted RNA model using advanced annotation
              tools adapted from RNApdbee. Then, it compares these networks to
              construct a consensus secondary structure that reflects the most
              frequently observed interactions across all models. Users can also
              define their own structural constraints to guide the
              consensus-building process.
            </Paragraph>
            <Paragraph>
              To evaluate how well each model agrees with the consensus, RNAtive
              calculates the Interaction Network Fidelity (INF) and F1 scores
              and ranks the models accordingly. It also estimates the confidence
              level for each predicted interaction based on how consistently it
              appears across the dataset.
            </Paragraph>
            <Paragraph>
              RNAtive accepts two or more RNA 3D structures in PDB or mmCIF
              format (individually or in a compressed archive), with a total
              upload limit of 100 MB. The results include ranked models with
              scores, detailed interaction-level assessments, and the consensus
              structure in both dot-bracket and graphical form for easy
              interpretation and further use.{" "}
            </Paragraph>
          </Typography>
        </Card>

        <Card title="Authors" style={{ marginBottom: "24px" }}>
          <Typography>
            <Paragraph>
              Jan Pielesiak<sup>1</sup>, Maciej Antczak<sup>1,2</sup>, Marta
              Szachniuk<sup>1,2</sup> and Tomasz Zok<sup>1</sup>
            </Paragraph>
            <Paragraph>
              <sup>1</sup>Institute of Computing Science, Poznan University of
              Technology, Poznan, Poland
            </Paragraph>
            <Paragraph>
              <sup>2</sup>Institute of Bioorganic Chemistry, Polish Academy of
              Sciences, Poznan, Poland
            </Paragraph>
          </Typography>
        </Card>

        <Card title="Acknowledgements and Funding">
          <Typography>
            <Paragraph>
              RNAtive project has been supported by grant 2023/51/D/ST6/01207
              from the National Science Centre, Poland.
            </Paragraph>
          </Typography>
        </Card>
      </Col>
    </Row>
  );
};
export default About;
