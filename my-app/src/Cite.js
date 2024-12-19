import { Card, Col, Row, Typography } from "antd";

const { Paragraph } = Typography;

const Cite = () => {
  return (
    <Row justify={"center"}>
      <Col span={20}>
        <Card title="Cite Us">
          <Typography>
            <Paragraph>Any published work which has made use of RNAtive should cite the following paper:</Paragraph>
            <Paragraph>J. Pielesiak, M. Antczak, M. Szachniuk, T. Zok. RNAtive to recognize native-like structure in a set of RNA 3D models.</Paragraph>
          </Typography>
        </Card>
      </Col>
    </Row>
  );
};
export default Cite;
