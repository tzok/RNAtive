import "./App.css";

import { Card, Col, Row } from "antd";

const About = () => {
  return (
    <Row justify={"center"}>
      <Col span={20}>
        <div>
          <header className="App-header">
            <div
              style={{
                display: "flex",
                flexDirection: "column",
                justifyContent: "center",
                alignItems: "center",
                gap: "20px",
              }}>
              <Card
                title="About"
                style={{
                  width: "100%",
                }}>
                <p>RNAtive effectively tackles the challenge of ranking RNA 3D models and identifying structures that closely resemble the native form, even in the absence of a known reference structure.</p>
                <p>Designed to handle RNA structures with identical sequences, the system can process up to 100 models per session. Employing a multi-stage computational approach, RNAtive begins by extracting the interaction network for each submitted RNA 3D model, utilizing specialized functions adapted from RNApdbee. Subsequently, it constructs a consensus-driven secondary structure, integrating all interaction networks while applying a predetermined confidence threshold. In the third phase, the system calculates the Interaction Network Fidelity (INF) for each RNA model relative to this consensus. It further assesses the confidence level of each base pair within the models using a binary classification test. Ultimately, RNAtive develops an INF-based ranking of all evaluated 3D models. The system delivers comprehensive results, including the final rankings, base-pair confidence assessments, and the consensus secondary structure. This consensus is provided in dot-bracket notation and graphic format, ensuring clarity and accessibility for further analysis.</p>
              </Card>

              <Card
                title="Authors"
                style={{
                  width: "100%",
                }}>
                <p>
                  Jan Pielesiak<sup>1</sup>, Maciej Antczak<sup>1,2</sup>, Marta Szachniuk<sup>1,2</sup> and Tomasz Zok<sup>1</sup>
                </p>
                <p>
                  <sup>1</sup>Institute of Computing Science, Poznan University of Technology, Poznan, Poland
                </p>
                <p>
                  <sup>2</sup>Institute of Bioorganic Chemistry, Polish Academy of Sciences, Poznan, Poland
                </p>
              </Card>

              <Card
                title="Acknowledgements and Funding"
                style={{
                  width: "100%",
                }}>
                <p>RNAtive project has been supported by grant 2023/51/D/ST6/01207 from the National Science Centre, Poland.</p>
              </Card>
            </div>
          </header>
        </div>
      </Col>
    </Row>
  );
};
export default About;
