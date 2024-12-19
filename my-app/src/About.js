import "./App.css";

import { Card, Col, Row, Typography } from "antd";

const { Paragraph } = Typography;

const About = () => {
  return (
    <Row justify={"center"}>
      <Col span={20}>
        <Card title="About" style={{ marginBottom: '24px' }}>
          <Typography>
            <Paragraph>RNAtive effectively tackles the challenge of ranking RNA 3D models and identifying structures that closely resemble the native form, even in the absence of a known reference structure.</Paragraph>
            <Paragraph>Designed to handle RNA structures with identical sequences, the system can process up to 100 models per session. Employing a multi-stage computational approach, RNAtive begins by extracting the interaction network for each submitted RNA 3D model, utilizing specialized functions adapted from RNApdbee. Subsequently, it constructs a consensus-driven secondary structure, integrating all interaction networks while applying a predetermined confidence threshold. In the third phase, the system calculates the Interaction Network Fidelity (INF) for each RNA model relative to this consensus. It further assesses the confidence level of each base pair within the models using a binary classification test. Ultimately, RNAtive develops an INF-based ranking of all evaluated 3D models. The system delivers comprehensive results, including the final rankings, base-pair confidence assessments, and the consensus secondary structure. This consensus is provided in dot-bracket notation and graphic format, ensuring clarity and accessibility for further analysis.</Paragraph>
          </Typography>
        </Card>

        <Card title="Authors" style={{ marginBottom: '24px' }}>
          <Typography>
            <Paragraph>
              Jan Pielesiak<sup>1</sup>, Maciej Antczak<sup>1,2</sup>, Marta Szachniuk<sup>1,2</sup> and Tomasz Zok<sup>1</sup>
            </Paragraph>
            <Paragraph>
              <sup>1</sup>Institute of Computing Science, Poznan University of Technology, Poznan, Poland
            </Paragraph>
            <Paragraph>
              <sup>2</sup>Institute of Bioorganic Chemistry, Polish Academy of Sciences, Poznan, Poland
            </Paragraph>
          </Typography>
        </Card>

        <Card title="Acknowledgements and Funding">
          <Typography>
            <Paragraph>RNAtive project has been supported by grant 2023/51/D/ST6/01207 from the National Science Centre, Poland.</Paragraph>
          </Typography>
        </Card>
      </Col>
    </Row>
  );
};
export default About;
