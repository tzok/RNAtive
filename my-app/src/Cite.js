import "./App.css";
import { Card, Col, Row } from "antd";

const Cite = () => {
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
                title="Cite Us"
                style={{
                  width: "100%",
                }}>
                <p>Any published work which has made use of RNAtive should cite the following paper:</p>
                <p>J. Pielesiak, M. Antczak, M. Szachniuk, T. Zok. RNAtive to recognize native-like structure in a set of RNA 3D models.</p>
              </Card>
            </div>
          </header>
        </div>
      </Col>
    </Row>
  );
};
export default Cite;
